package com.superagent.knowledge.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.DocumentTask;
import com.superagent.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/documents")
public class DocumentController {

    private final KnowledgeService knowledgeService;

    public DocumentController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDetailItem> getDocument(@PathVariable long documentId) {
        var document = knowledgeService.getDocument(documentId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", document.category());
        metadata.put("tags", document.tags());
        return ApiResponse.success(new DocumentDetailItem(
                document.id(),
                document.knowledgeBaseId(),
                document.title(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.status().name(),
                document.chunkCount(),
                document.errorMessage(),
                document.parsedText(),
                metadata,
                document.createdAt(),
                document.updatedAt()
        ));
    }

    @PostMapping("/{documentId}/reprocess")
    public ApiResponse<ReprocessResponse> reprocess(
            @PathVariable long documentId,
            @Valid @RequestBody(required = false) ReprocessRequest request
    ) {
        var result = knowledgeService.reprocessDocument(documentId, request == null ? null : request.reason());
        return ApiResponse.success(new ReprocessResponse(result.documentId(), result.taskId(), result.status()));
    }

    @GetMapping("/{documentId}/chunks")
    public ApiResponse<KnowledgeController.PagedResponse<DocumentChunkItem>> listChunks(
            @PathVariable long documentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        var result = knowledgeService.listDocumentChunks(documentId, page, pageSize);
        return ApiResponse.success(new KnowledgeController.PagedResponse<>(
                result.items().stream().map(this::toChunkItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    @GetMapping("/{documentId}/tasks")
    public ApiResponse<List<DocumentTaskItem>> listTasks(@PathVariable long documentId) {
        return ApiResponse.success(knowledgeService.listDocumentTasks(documentId).stream()
                .map(this::toTaskItem)
                .toList());
    }

    private DocumentChunkItem toChunkItem(DocumentChunk chunk) {
        return new DocumentChunkItem(
                chunk.id(),
                chunk.chunkNo(),
                chunk.sectionTitle(),
                chunk.content(),
                chunk.charCount(),
                chunk.tokenCount(),
                chunk.metadata(),
                chunk.createdAt()
        );
    }

    private DocumentTaskItem toTaskItem(DocumentTask task) {
        return new DocumentTaskItem(
                task.id(),
                task.taskType().name(),
                task.status().name(),
                task.attemptCount(),
                task.inputSummary(),
                task.outputSummary(),
                task.errorMessage(),
                task.startedAt(),
                task.finishedAt(),
                task.createdAt()
        );
    }

    public record ReprocessResponse(long documentId, long taskId, String status) {
    }

    public record ReprocessRequest(@Size(max = 500) String reason) {
    }

    public record DocumentDetailItem(
            long id,
            long knowledgeBaseId,
            String title,
            String fileName,
            String fileType,
            long fileSize,
            String status,
            int chunkCount,
            String errorMessage,
            String parsedText,
            Map<String, Object> metadata,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DocumentChunkItem(
            long id,
            int chunkNo,
            String sectionTitle,
            String content,
            int charCount,
            Integer tokenCount,
            Map<String, Object> metadata,
            OffsetDateTime createdAt
    ) {
    }

    public record DocumentTaskItem(
            long id,
            String taskType,
            String status,
            int attemptCount,
            String inputSummary,
            String outputSummary,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            OffsetDateTime createdAt
    ) {
    }
}
