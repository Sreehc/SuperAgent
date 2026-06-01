package com.superagent.knowledge.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.storage.ObjectStorageService;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.DocumentTask;
import com.superagent.knowledge.domain.DocumentTaskStatus;
import com.superagent.knowledge.domain.DocumentTaskType;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.rag.service.EmbeddingClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentProcessingService {

    private final KnowledgeRepository knowledgeRepository;
    private final ObjectStorageService objectStorageService;
    private final DocumentParserService documentParserService;
    private final RecursiveChunker recursiveChunker;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    public DocumentProcessingService(
            KnowledgeRepository knowledgeRepository,
            ObjectStorageService objectStorageService,
            DocumentParserService documentParserService,
            RecursiveChunker recursiveChunker,
            EmbeddingClient embeddingClient,
            TransactionTemplate transactionTemplate
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.objectStorageService = objectStorageService;
        this.documentParserService = documentParserService;
        this.recursiveChunker = recursiveChunker;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = transactionTemplate;
    }

    public void process(DocumentTaskMessage message) {
        DocumentTask triggerTask = knowledgeRepository.getDocumentTask(message.tenantId(), message.taskId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document task not found"));
        if (triggerTask.taskType() == DocumentTaskType.reprocess) {
            processReprocess(message, triggerTask);
            return;
        }
        processParseAndChunk(message, triggerTask);
    }

    private void processReprocess(DocumentTaskMessage message, DocumentTask reprocessTask) {
        if (knowledgeRepository.tryMarkTaskRunning(message.tenantId(), reprocessTask.id()).isEmpty()) {
            return;
        }
        try {
            DocumentTask parseTask = knowledgeRepository.createDocumentTask(
                    message.tenantId(),
                    message.documentId(),
                    DocumentTaskType.parse,
                    com.superagent.knowledge.domain.DocumentTaskStatus.pending,
                    "Reprocess triggered by admin"
            );
            processParseAndChunk(new DocumentTaskMessage(message.tenantId(), message.documentId(), parseTask.id(), "reprocess"), parseTask);
            knowledgeRepository.markTaskSuccess(message.tenantId(), reprocessTask.id(), "Reprocess completed");
        } catch (Exception exception) {
            knowledgeRepository.markTaskFailed(message.tenantId(), reprocessTask.id(), safeError(exception));
            throw exception;
        }
    }

    private void processParseAndChunk(DocumentTaskMessage message, DocumentTask parseTask) {
        KnowledgeDocument document = knowledgeRepository.getKnowledgeDocument(message.tenantId(), message.documentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found"));
        if (knowledgeRepository.tryMarkTaskRunning(message.tenantId(), parseTask.id()).isEmpty()) {
            return;
        }
        knowledgeRepository.updateDocumentStatus(message.tenantId(), document.id(), KnowledgeDocumentStatus.parsing, null, null);
        try (InputStream inputStream = objectStorageService.open(document.objectKey())) {
            DocumentParserService.ParsedDocument parsed = documentParserService.parse(document.fileType(), inputStream);
            knowledgeRepository.markTaskSuccess(
                    message.tenantId(),
                    parseTask.id(),
                    "Parsed " + parsed.charCount() + " chars"
            );
            DocumentTask chunkTask = knowledgeRepository.createDocumentTask(
                    message.tenantId(),
                    document.id(),
                    DocumentTaskType.chunk,
                    DocumentTaskStatus.pending,
                    "Chunk parsed content from task " + parseTask.id()
            );
            processChunkTask(message.tenantId(), document, parsed.content(), chunkTask.id());
        } catch (Exception exception) {
            failDocument(message.tenantId(), document.id(), parseTask.id(), exception);
            throw wrapIfNecessary(exception);
        }
    }

    protected void processChunkTask(long tenantId, KnowledgeDocument document, String content, long chunkTaskId) {
        if (knowledgeRepository.tryMarkTaskRunning(tenantId, chunkTaskId).isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                knowledgeRepository.updateDocumentStatus(tenantId, document.id(), KnowledgeDocumentStatus.chunking, null, null);
                List<RecursiveChunker.ChunkCandidate> candidates = recursiveChunker.chunk(content);
                List<KnowledgeRepository.ChunkInsert> inserts = candidates.stream()
                        .map(candidate -> new KnowledgeRepository.ChunkInsert(
                                null,
                                candidate.chunkNo(),
                                null,
                                candidate.content(),
                                hashContent(candidate.content()),
                                candidate.content().length(),
                                null,
                                Map.of(
                                        "strategy", "recursive",
                                        "sourceTaskId", chunkTaskId
                                )
                        ))
                        .toList();
                knowledgeRepository.replaceDocumentChunks(tenantId, document.id(), inserts);
                knowledgeRepository.markTaskSuccess(tenantId, chunkTaskId, "Generated " + inserts.size() + " chunks");
                knowledgeRepository.updateDocumentStatus(tenantId, document.id(), KnowledgeDocumentStatus.embedding, inserts.size(), null);
            });
            DocumentTask embedTask = knowledgeRepository.createDocumentTask(
                    tenantId,
                    document.id(),
                    DocumentTaskType.embed,
                    DocumentTaskStatus.pending,
                    "Embed chunks from task " + chunkTaskId
            );
            processEmbedTask(tenantId, document, embedTask.id());
        } catch (Exception exception) {
            failDocument(tenantId, document.id(), chunkTaskId, exception);
            throw wrapIfNecessary(exception);
        }
    }

    protected void processEmbedTask(long tenantId, KnowledgeDocument document, long embedTaskId) {
        if (knowledgeRepository.tryMarkTaskRunning(tenantId, embedTaskId).isEmpty()) {
            return;
        }
        try {
            List<DocumentChunk> chunks = knowledgeRepository.listAllDocumentChunks(tenantId, document.id());
            EmbeddingClient.EmbeddingResult embeddingResult = embeddingClient.embed(
                    chunks.stream().map(DocumentChunk::content).toList()
            );
            List<KnowledgeRepository.EmbeddingInsert> embeddings = java.util.stream.IntStream.range(0, chunks.size())
                    .mapToObj(index -> new KnowledgeRepository.EmbeddingInsert(
                            chunks.get(index).id(),
                            embeddingResult.vectors().get(index)
                    ))
                    .toList();
            transactionTemplate.executeWithoutResult(status -> {
                knowledgeRepository.replaceDocumentEmbeddings(
                        tenantId,
                        document.id(),
                        embeddingResult.provider(),
                        embeddingResult.model(),
                        embeddingResult.dimension(),
                        embeddings
                );
                knowledgeRepository.markTaskSuccess(tenantId, embedTaskId, "Embedded " + embeddings.size() + " chunks");
                knowledgeRepository.updateDocumentStatus(tenantId, document.id(), KnowledgeDocumentStatus.ready, chunks.size(), null);
            });
        } catch (Exception exception) {
            failDocument(tenantId, document.id(), embedTaskId, exception);
            throw wrapIfNecessary(exception);
        }
    }

    private void failDocument(long tenantId, long documentId, long taskId, Exception exception) {
        String errorMessage = safeError(exception);
        knowledgeRepository.markTaskFailed(tenantId, taskId, errorMessage);
        knowledgeRepository.updateDocumentStatus(tenantId, documentId, KnowledgeDocumentStatus.failed, null, errorMessage);
    }

    private RuntimeException wrapIfNecessary(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, safeError(exception));
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Document processing failed";
        }
        return message;
    }

    private String hashContent(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash chunk content");
        }
    }
}
