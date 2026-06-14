package com.superagent.knowledge.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.knowledge.service.KnowledgeService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/documents")
public class KnowledgeAdminController {

    private final KnowledgeService knowledgeService;

    public KnowledgeAdminController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/expiring")
    public ApiResponse<ExpiringDocumentResponse> listExpiring(
            @RequestParam(required = false, defaultValue = "30") Integer withinDays
    ) {
        List<KnowledgeDocument> documents = knowledgeService.findExpiringDocuments(withinDays == null ? 30 : withinDays);
        List<ExpiringDocumentItem> items = documents.stream().map(doc -> new ExpiringDocumentItem(
                doc.id(),
                doc.knowledgeBaseId(),
                doc.title(),
                doc.fileName(),
                doc.fileType(),
                doc.status().name(),
                doc.ownerUserId(),
                doc.expiresAt(),
                doc.reviewStatus(),
                doc.updatedAt()
        )).toList();
        return ApiResponse.success(new ExpiringDocumentResponse(items, items.size(), withinDays == null ? 30 : withinDays));
    }

    @GetMapping("/duplicates")
    public ApiResponse<DuplicateDocumentResponse> listDuplicates() {
        List<KnowledgeRepository.DuplicateDocumentGroup> groups = knowledgeService.findDuplicateDocuments();
        List<DuplicateDocumentGroupItem> items = groups.stream().map(group -> new DuplicateDocumentGroupItem(
                group.contentHash(),
                group.duplicateCount(),
                group.documentIds(),
                group.titles(),
                group.knowledgeBaseIds(),
                group.firstCreatedAt(),
                group.lastUpdatedAt()
        )).toList();
        return ApiResponse.success(new DuplicateDocumentResponse(items, items.size()));
    }

    public record ExpiringDocumentResponse(List<ExpiringDocumentItem> items, int total, int withinDays) {
    }

    public record ExpiringDocumentItem(
            long id,
            long knowledgeBaseId,
            String title,
            String fileName,
            String fileType,
            String status,
            Long ownerUserId,
            OffsetDateTime expiresAt,
            String reviewStatus,
            OffsetDateTime updatedAt
    ) {
    }

    public record DuplicateDocumentResponse(List<DuplicateDocumentGroupItem> items, int total) {
    }

    public record DuplicateDocumentGroupItem(
            String contentHash,
            int duplicateCount,
            List<Long> documentIds,
            List<String> titles,
            List<Long> knowledgeBaseIds,
            OffsetDateTime firstCreatedAt,
            OffsetDateTime lastUpdatedAt
    ) {
    }
}
