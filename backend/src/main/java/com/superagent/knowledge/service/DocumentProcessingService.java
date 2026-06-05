package com.superagent.knowledge.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.storage.ObjectStorageService;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.DocumentTask;
import com.superagent.knowledge.domain.DocumentTaskStatus;
import com.superagent.knowledge.domain.DocumentTaskType;
import com.superagent.knowledge.domain.KnowledgeDocumentVersion;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.rag.service.EmbeddingClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentProcessingService {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern SLIDE_MARKER_PATTERN = Pattern.compile("^(?:slide|page)\\s*(\\d+)\\s*[:：-]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_PAGE_PATTERN = Pattern.compile("^第\\s*(\\d+)\\s*[页章节]\\s*[:：-]?\\s*(.*)$");
    private static final Pattern STRUCTURED_SPLIT_PATTERN = Pattern.compile("(?im)(?=^(slide\\s*\\d+|page\\s*\\d+|第\\s*\\d+\\s*[页章节]|#{1,6}\\s))");

    private final KnowledgeRepository knowledgeRepository;
    private final ObjectStorageService objectStorageService;
    private final DocumentParserService documentParserService;
    private final RecursiveChunker recursiveChunker;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;
    private final DocumentGraphService documentGraphService;

    public DocumentProcessingService(
            KnowledgeRepository knowledgeRepository,
            ObjectStorageService objectStorageService,
            DocumentParserService documentParserService,
            RecursiveChunker recursiveChunker,
            EmbeddingClient embeddingClient,
            TransactionTemplate transactionTemplate,
            DocumentGraphService documentGraphService
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.objectStorageService = objectStorageService;
        this.documentParserService = documentParserService;
        this.recursiveChunker = recursiveChunker;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = transactionTemplate;
        this.documentGraphService = documentGraphService;
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
        KnowledgeDocumentVersion version = resolveActiveVersion(message.tenantId(), document);
        knowledgeRepository.updateDocumentVersion(
                message.tenantId(),
                version.id(),
                "parsing",
                null,
                "pending",
                version.metadata()
        );
        knowledgeRepository.updateDocumentStatus(
                message.tenantId(),
                document.id(),
                KnowledgeDocumentStatus.parsing,
                null,
                null,
                null,
                version.versionNo(),
                "pending",
                null
        );
        try (InputStream inputStream = objectStorageService.open(document.objectKey())) {
            DocumentParserService.ParsedDocument parsed = documentParserService.parse(document.fileType(), inputStream);
            knowledgeRepository.updateDocumentStatus(
                    message.tenantId(),
                    document.id(),
                    KnowledgeDocumentStatus.parsing,
                    null,
                    null,
                    parsed.content(),
                    version.versionNo(),
                    "pending",
                    null
            );
            knowledgeRepository.markTaskSuccess(
                    message.tenantId(),
                    parseTask.id(),
                    "Parsed " + parsed.charCount() + " chars"
            );
            knowledgeRepository.updateDocumentVersion(
                    message.tenantId(),
                    version.id(),
                    "parsed",
                    null,
                    "pending",
                    mergeVersionMetadata(version.metadata(), Map.of("parsedCharCount", parsed.charCount()))
            );
            DocumentTask chunkTask = knowledgeRepository.createDocumentTask(
                    message.tenantId(),
                    document.id(),
                    DocumentTaskType.chunk,
                    DocumentTaskStatus.pending,
                    "Chunk parsed content from task " + parseTask.id()
            );
            processChunkTask(message.tenantId(), document, version, parsed.content(), chunkTask.id());
        } catch (Exception exception) {
            failDocument(message.tenantId(), document.id(), parseTask.id(), version, exception);
            throw wrapIfNecessary(exception);
        }
    }

    protected void processChunkTask(long tenantId, KnowledgeDocument document, KnowledgeDocumentVersion version, String content, long chunkTaskId) {
        if (knowledgeRepository.tryMarkTaskRunning(tenantId, chunkTaskId).isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                knowledgeRepository.updateDocumentVersion(
                        tenantId,
                        version.id(),
                        "chunking",
                        null,
                        "pending",
                        version.metadata()
                );
                knowledgeRepository.updateDocumentStatus(
                        tenantId,
                        document.id(),
                        KnowledgeDocumentStatus.chunking,
                        null,
                        null,
                        null,
                        version.versionNo(),
                        "pending",
                        null
                );
                ChunkingPlan chunkingPlan = buildChunkingPlan(document, version, content);
                List<RecursiveChunker.ChunkCandidate> candidates = chunkingPlan.candidates();
                List<KnowledgeRepository.ChunkInsert> inserts = candidates.stream()
                        .map(candidate -> new KnowledgeRepository.ChunkInsert(
                                null,
                                candidate.chunkNo(),
                                candidate.sectionTitle(),
                                candidate.content(),
                                hashContent(candidate.content()),
                                candidate.content().length(),
                                null,
                                buildChunkMetadata(chunkingPlan.strategy(), version, chunkTaskId, candidate.metadata())
                        ))
                        .toList();
                knowledgeRepository.replaceDocumentChunks(tenantId, document.id(), inserts);
                knowledgeRepository.markTaskSuccess(tenantId, chunkTaskId, "Generated " + inserts.size() + " chunks");
                knowledgeRepository.updateDocumentVersion(
                        tenantId,
                        version.id(),
                        "embedding",
                        inserts.size(),
                        "pending",
                        mergeVersionMetadata(version.metadata(), Map.of(
                                "chunkingStrategy", chunkingPlan.strategy(),
                                "chunkCount", inserts.size()
                        ))
                );
                knowledgeRepository.updateDocumentStatus(
                        tenantId,
                        document.id(),
                        KnowledgeDocumentStatus.embedding,
                        inserts.size(),
                        null,
                        null,
                        version.versionNo(),
                        "pending",
                        null
                );
            });
            DocumentTask embedTask = knowledgeRepository.createDocumentTask(
                    tenantId,
                    document.id(),
                    DocumentTaskType.embed,
                    DocumentTaskStatus.pending,
                    "Embed chunks from task " + chunkTaskId
            );
            processEmbedTask(tenantId, document, version, embedTask.id());
        } catch (Exception exception) {
            failDocument(tenantId, document.id(), chunkTaskId, version, exception);
            throw wrapIfNecessary(exception);
        }
    }

    protected void processEmbedTask(long tenantId, KnowledgeDocument document, KnowledgeDocumentVersion version, long embedTaskId) {
        if (knowledgeRepository.tryMarkTaskRunning(tenantId, embedTaskId).isEmpty()) {
            return;
        }
        try {
            List<DocumentChunk> chunks = knowledgeRepository.listAllDocumentChunks(tenantId, document.id());
            EmbeddingClient.EmbeddingResult embeddingResult = embeddingClient.embed(
                    tenantId,
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
                knowledgeRepository.updateDocumentVersion(
                        tenantId,
                        version.id(),
                        "ready",
                        chunks.size(),
                        "pending",
                        mergeVersionMetadata(version.metadata(), Map.of(
                                "embeddingProvider", embeddingResult.provider(),
                                "embeddingModel", embeddingResult.model(),
                                "embeddingDimension", embeddingResult.dimension()
                        ))
                );
                knowledgeRepository.updateDocumentStatus(
                        tenantId,
                        document.id(),
                        KnowledgeDocumentStatus.ready,
                        chunks.size(),
                        null,
                        null,
                        version.versionNo(),
                        "pending",
                        null
                );
            });
            trySynchronizeGraph(tenantId, document);
        } catch (Exception exception) {
            failDocument(tenantId, document.id(), embedTaskId, version, exception);
            throw wrapIfNecessary(exception);
        }
    }

    private void failDocument(long tenantId, long documentId, long taskId, KnowledgeDocumentVersion version, Exception exception) {
        String errorMessage = safeError(exception);
        knowledgeRepository.markTaskFailed(tenantId, taskId, errorMessage);
        knowledgeRepository.updateDocumentVersion(
                tenantId,
                version.id(),
                "failed",
                null,
                "failed",
                mergeVersionMetadata(version.metadata(), Map.of("error", errorMessage))
        );
        knowledgeRepository.updateDocumentStatus(
                tenantId,
                documentId,
                KnowledgeDocumentStatus.failed,
                null,
                errorMessage,
                null,
                version.versionNo(),
                "failed",
                errorMessage
        );
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

    private void trySynchronizeGraph(long tenantId, KnowledgeDocument document) {
        try {
            KnowledgeDocument refreshed = knowledgeRepository.getKnowledgeDocument(tenantId, document.id()).orElseThrow();
            com.superagent.knowledge.domain.KnowledgeBase knowledgeBase = knowledgeRepository.getKnowledgeBase(tenantId, refreshed.knowledgeBaseId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge base not found"));
            documentGraphService.synchronizeGraph(tenantId, knowledgeBase, refreshed);
        } catch (Exception exception) {
            // Graph sync is best-effort. The document stays ready for classic RAG.
        }
    }

    private KnowledgeDocumentVersion resolveActiveVersion(long tenantId, KnowledgeDocument document) {
        return knowledgeRepository.findLatestDocumentVersion(tenantId, document.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document version not found"));
    }

    private ChunkingPlan buildChunkingPlan(KnowledgeDocument document, KnowledgeDocumentVersion version, String content) {
        String strategy = extractStrategy(document, version);
        if ("markdown_heading".equalsIgnoreCase(strategy)) {
            return new ChunkingPlan(strategy, chunkMarkdownByHeading(content));
        }
        if ("slide_section".equalsIgnoreCase(strategy)) {
            return new ChunkingPlan(strategy, chunkSlideSections(content));
        }
        return new ChunkingPlan("recursive", recursiveChunker.chunk(content));
    }

    private String extractStrategy(KnowledgeDocument document, KnowledgeDocumentVersion version) {
        Object metadataValue = version.metadata().get("chunkingStrategy");
        if (metadataValue instanceof String strategy && !strategy.isBlank()) {
            return strategy;
        }
        if (document.chunkingProfileId() != null && version.chunkingProfileId() != null) {
            return "recursive";
        }
        return "recursive";
    }

    private List<RecursiveChunker.ChunkCandidate> chunkMarkdownByHeading(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<RecursiveChunker.ChunkCandidate> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        String currentSectionTitle = null;
        Map<String, Object> currentMetadata = Map.of();
        for (String line : normalized.split("\\n")) {
            Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                appendStructuredChunk(chunks, currentSectionTitle, currentLines, currentMetadata);
                int level = matcher.group(1).length();
                String title = matcher.group(2).trim();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(title);
                currentSectionTitle = title;
                currentMetadata = new LinkedHashMap<>();
                currentMetadata.put("blockType", "heading_section");
                currentMetadata.put("headingLevel", level);
                currentMetadata.put("headingPath", List.copyOf(headingStack));
            }
            currentLines.add(line);
        }
        appendStructuredChunk(chunks, currentSectionTitle, currentLines, currentMetadata);
        if (chunks.isEmpty()) {
            return recursiveChunker.chunk(content);
        }
        return chunks;
    }

    private List<RecursiveChunker.ChunkCandidate> chunkSlideSections(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] blocks = STRUCTURED_SPLIT_PATTERN.split(normalized);
        List<RecursiveChunker.ChunkCandidate> chunks = new java.util.ArrayList<>();
        for (String block : blocks) {
            String candidate = block.trim();
            if (candidate.isBlank()) {
                continue;
            }
            chunks.add(buildSlideChunkCandidate(chunks.size() + 1, candidate));
        }
        if (chunks.isEmpty()) {
            return recursiveChunker.chunk(content);
        }
        return chunks;
    }

    private RecursiveChunker.ChunkCandidate buildSlideChunkCandidate(int chunkNo, String candidate) {
        List<String> lines = candidate.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        String firstLine = lines.isEmpty() ? candidate : lines.get(0);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("blockType", "slide_section");
        Matcher slideMatcher = SLIDE_MARKER_PATTERN.matcher(firstLine);
        Matcher chinesePageMatcher = CHINESE_PAGE_PATTERN.matcher(firstLine);
        String sectionTitle = firstLine;
        if (slideMatcher.matches()) {
            metadata.put("slideNo", Integer.parseInt(slideMatcher.group(1)));
            String suffix = slideMatcher.group(2) == null ? "" : slideMatcher.group(2).trim();
            if (!suffix.isBlank()) {
                sectionTitle = suffix;
            }
        } else if (chinesePageMatcher.matches()) {
            metadata.put("pageNo", Integer.parseInt(chinesePageMatcher.group(1)));
            String suffix = chinesePageMatcher.group(2) == null ? "" : chinesePageMatcher.group(2).trim();
            if (!suffix.isBlank()) {
                sectionTitle = suffix;
            }
        } else {
            Matcher headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(firstLine);
            if (headingMatcher.matches()) {
                sectionTitle = headingMatcher.group(2).trim();
                metadata.put("headingLevel", headingMatcher.group(1).length());
            }
        }
        return new RecursiveChunker.ChunkCandidate(chunkNo, sectionTitle, candidate, metadata);
    }

    private void appendStructuredChunk(
            List<RecursiveChunker.ChunkCandidate> chunks,
            String sectionTitle,
            List<String> currentLines,
            Map<String, Object> metadata
    ) {
        if (currentLines.isEmpty()) {
            return;
        }
        String candidate = String.join("\n", currentLines).trim();
        currentLines.clear();
        if (candidate.isBlank()) {
            return;
        }
        chunks.add(new RecursiveChunker.ChunkCandidate(
                chunks.size() + 1,
                sectionTitle,
                candidate,
                metadata == null ? Map.of() : Map.copyOf(metadata)
        ));
    }

    private Map<String, Object> mergeVersionMetadata(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (patch != null) {
            patch.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    private Map<String, Object> buildChunkMetadata(
            String strategy,
            KnowledgeDocumentVersion version,
            long chunkTaskId,
            Map<String, Object> candidateMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("strategy", strategy);
        metadata.put("versionNo", version.versionNo());
        metadata.put("sourceTaskId", chunkTaskId);
        if (version.chunkingProfileId() != null) {
            metadata.put("chunkingProfileId", version.chunkingProfileId());
        }
        if (candidateMetadata != null) {
            candidateMetadata.forEach((key, value) -> {
                if (value != null) {
                    metadata.put(key, value);
                }
            });
        }
        return metadata;
    }

    private record ChunkingPlan(String strategy, List<RecursiveChunker.ChunkCandidate> candidates) {
    }
}
