package com.superagent.knowledge.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.domain.KnowledgeBaseVisibility;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/knowledge-bases")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseSummary> createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = knowledgeService.createKnowledgeBase(
                request.name(),
                request.description(),
                request.visibility()
        );
        return ApiResponse.success(new KnowledgeBaseSummary(
                knowledgeBase.id(),
                knowledgeBase.name(),
                knowledgeBase.status().name(),
                knowledgeBase.visibility().name()
        ));
    }

    @GetMapping
    public ApiResponse<PagedResponse<KnowledgeBaseListItem>> listKnowledgeBases(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        var result = knowledgeService.listKnowledgeBases(page, pageSize, status, keyword);
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toListItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseDetail> getKnowledgeBase(@PathVariable long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeService.getKnowledgeBase(knowledgeBaseId);
        return ApiResponse.success(toDetail(knowledgeBase));
    }

    @PatchMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBasePatchResponse> updateKnowledgeBase(
            @PathVariable long knowledgeBaseId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request
    ) {
        KnowledgeBase knowledgeBase = knowledgeService.updateKnowledgeBase(
                knowledgeBaseId,
                request.name(),
                request.description(),
                request.visibility(),
                request.status()
        );
        return ApiResponse.success(new KnowledgeBasePatchResponse(knowledgeBase.id(), knowledgeBase.status().name()));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ApiResponse<DeleteKnowledgeBaseResponse> deleteKnowledgeBase(@PathVariable long knowledgeBaseId) {
        return ApiResponse.success(new DeleteKnowledgeBaseResponse(knowledgeService.deleteKnowledgeBase(knowledgeBaseId)));
    }

    @PostMapping(path = "/{knowledgeBaseId}/documents", consumes = "multipart/form-data")
    public ApiResponse<UploadDocumentResponse> uploadDocument(
            @PathVariable long knowledgeBaseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "knowledgeDomainId", required = false) Long knowledgeDomainId,
            @RequestParam(value = "chunkingProfileId", required = false) Long chunkingProfileId
    ) {
        var result = knowledgeService.uploadDocument(
                knowledgeBaseId,
                file,
                title,
                category,
                tags,
                knowledgeDomainId,
                chunkingProfileId
        );
        return ApiResponse.success(new UploadDocumentResponse(
                result.document().id(),
                result.document().knowledgeBaseId(),
                result.document().title(),
                result.document().status().name(),
                result.task().id(),
                result.document().knowledgeDomainId(),
                result.document().chunkingProfileId(),
                result.document().activeVersionNo()
        ));
    }

    @PostMapping(path = "/{knowledgeBaseId}/documents/batch", consumes = "multipart/form-data")
    public ApiResponse<UploadBatchDocumentResponse> uploadDocuments(
            @PathVariable long knowledgeBaseId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "knowledgeDomainId", required = false) Long knowledgeDomainId,
            @RequestParam(value = "chunkingProfileId", required = false) Long chunkingProfileId
    ) {
        var items = knowledgeService.uploadDocuments(
                knowledgeBaseId,
                files,
                category,
                tags,
                knowledgeDomainId,
                chunkingProfileId
        ).stream().map(result -> new UploadDocumentResponse(
                result.document().id(),
                result.document().knowledgeBaseId(),
                result.document().title(),
                result.document().status().name(),
                result.task().id(),
                result.document().knowledgeDomainId(),
                result.document().chunkingProfileId(),
                result.document().activeVersionNo()
        )).toList();
        return ApiResponse.success(new UploadBatchDocumentResponse(items, items.size()));
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<PagedResponse<KnowledgeDocumentListItem>> listDocuments(
            @PathVariable long knowledgeBaseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag
    ) {
        var result = knowledgeService.listDocuments(knowledgeBaseId, page, pageSize, status, fileType, keyword, tag);
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toDocumentListItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    private KnowledgeBaseListItem toListItem(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseListItem(
                knowledgeBase.id(),
                knowledgeBase.name(),
                knowledgeBase.status().name(),
                knowledgeBase.documentCount(),
                knowledgeBase.updatedAt()
        );
    }

    private KnowledgeBaseDetail toDetail(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseDetail(
                knowledgeBase.id(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.visibility().name(),
                knowledgeBase.status().name(),
                knowledgeBase.documentCount()
        );
    }

    private KnowledgeDocumentListItem toDocumentListItem(KnowledgeDocument document) {
        return new KnowledgeDocumentListItem(
                document.id(),
                document.title(),
                document.fileType(),
                document.fileSize(),
                document.status().name(),
                document.chunkCount(),
                document.updatedAt()
        );
    }

    public record CreateKnowledgeBaseRequest(
            @NotBlank(message = "name is required") String name,
            String description,
            KnowledgeBaseVisibility visibility
    ) {
    }

    public record UpdateKnowledgeBaseRequest(
            String name,
            String description,
            KnowledgeBaseVisibility visibility,
            KnowledgeBaseStatus status
    ) {
    }

    public record KnowledgeBaseSummary(
            long id,
            String name,
            String status,
            String visibility
    ) {
    }

    public record KnowledgeBaseListItem(
            long id,
            String name,
            String status,
            int documentCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record KnowledgeBaseDetail(
            long id,
            String name,
            String description,
            String visibility,
            String status,
            int documentCount
    ) {
    }

    public record KnowledgeBasePatchResponse(long id, String status) {
    }

    public record DeleteKnowledgeBaseResponse(boolean deleted) {
    }

    public record UploadDocumentResponse(
            long id,
            long knowledgeBaseId,
            String title,
            String status,
            long taskId,
            Long knowledgeDomainId,
            Long chunkingProfileId,
            int activeVersionNo
    ) {
    }

    public record UploadBatchDocumentResponse(
            List<UploadDocumentResponse> items,
            int uploadedCount
    ) {
    }

    public record KnowledgeDocumentListItem(
            long id,
            String title,
            String fileType,
            long fileSize,
            String status,
            int chunkCount,
            OffsetDateTime updatedAt
    ) {
    }

    public record PagedResponse<T>(
            List<T> items,
            int page,
            int pageSize,
            long total
    ) {
    }
}
