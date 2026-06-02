package com.superagent.knowledge.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.knowledge.domain.ChunkingProfile;
import com.superagent.knowledge.domain.KnowledgeDomain;
import com.superagent.knowledge.domain.KnowledgeDocumentVersion;
import com.superagent.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}")
public class KnowledgeGovernanceController {

    private final KnowledgeService knowledgeService;

    public KnowledgeGovernanceController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/admin/knowledge-domains")
    public ApiResponse<List<KnowledgeDomainItem>> listKnowledgeDomains() {
        return ApiResponse.success(knowledgeService.listKnowledgeDomains().stream().map(this::toDomainItem).toList());
    }

    @PostMapping("/admin/knowledge-domains")
    public ApiResponse<KnowledgeDomainItem> createKnowledgeDomain(@Valid @RequestBody CreateKnowledgeDomainRequest request) {
        return ApiResponse.success(toDomainItem(
                knowledgeService.createKnowledgeDomain(request.code(), request.name(), request.description())
        ));
    }

    @PatchMapping("/admin/knowledge-domains/{domainId}")
    public ApiResponse<KnowledgeDomainItem> updateKnowledgeDomain(
            @PathVariable long domainId,
            @Valid @RequestBody UpdateKnowledgeDomainRequest request
    ) {
        return ApiResponse.success(toDomainItem(
                knowledgeService.updateKnowledgeDomain(domainId, request.name(), request.description(), request.status())
        ));
    }

    @GetMapping("/admin/chunking-profiles")
    public ApiResponse<List<ChunkingProfileItem>> listChunkingProfiles() {
        return ApiResponse.success(knowledgeService.listChunkingProfiles().stream().map(this::toProfileItem).toList());
    }

    @PostMapping("/admin/chunking-profiles")
    public ApiResponse<ChunkingProfileItem> createChunkingProfile(@Valid @RequestBody CreateChunkingProfileRequest request) {
        return ApiResponse.success(toProfileItem(
                knowledgeService.createChunkingProfile(
                        request.code(),
                        request.name(),
                        request.strategy(),
                        request.isDefault(),
                        request.config() == null ? Map.of() : request.config()
                )
        ));
    }

    @PatchMapping("/admin/chunking-profiles/{profileId}")
    public ApiResponse<ChunkingProfileItem> updateChunkingProfile(
            @PathVariable long profileId,
            @Valid @RequestBody UpdateChunkingProfileRequest request
    ) {
        return ApiResponse.success(toProfileItem(
                knowledgeService.updateChunkingProfile(
                        profileId,
                        request.name(),
                        request.strategy(),
                        request.isDefault(),
                        request.status(),
                        request.config()
                )
        ));
    }

    @GetMapping("/documents/{documentId}/versions")
    public ApiResponse<List<DocumentVersionItem>> listDocumentVersions(@PathVariable long documentId) {
        return ApiResponse.success(knowledgeService.listDocumentVersions(documentId).stream().map(this::toVersionItem).toList());
    }

    private KnowledgeDomainItem toDomainItem(KnowledgeDomain domain) {
        return new KnowledgeDomainItem(domain.id(), domain.code(), domain.name(), domain.description(), domain.status(), domain.createdAt(), domain.updatedAt());
    }

    private ChunkingProfileItem toProfileItem(ChunkingProfile profile) {
        return new ChunkingProfileItem(
                profile.id(),
                profile.code(),
                profile.name(),
                profile.strategy(),
                profile.isDefault(),
                profile.status(),
                profile.config(),
                profile.createdAt(),
                profile.updatedAt()
        );
    }

    private DocumentVersionItem toVersionItem(KnowledgeDocumentVersion version) {
        return new DocumentVersionItem(
                version.id(),
                version.documentId(),
                version.versionNo(),
                version.chunkingProfileId(),
                version.status(),
                version.chunkCount(),
                version.graphSyncStatus(),
                version.createdAt(),
                version.updatedAt()
        );
    }

    public record CreateKnowledgeDomainRequest(@NotBlank String code, @NotBlank String name, String description) {
    }

    public record UpdateKnowledgeDomainRequest(String name, String description, String status) {
    }

    public record CreateChunkingProfileRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String strategy,
            boolean isDefault,
            Map<String, Object> config
    ) {
    }

    public record UpdateChunkingProfileRequest(
            String name,
            String strategy,
            Boolean isDefault,
            String status,
            Map<String, Object> config
    ) {
    }

    public record KnowledgeDomainItem(
            long id,
            String code,
            String name,
            String description,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ChunkingProfileItem(
            long id,
            String code,
            String name,
            String strategy,
            boolean isDefault,
            String status,
            Map<String, Object> config,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DocumentVersionItem(
            long id,
            long documentId,
            int versionNo,
            Long chunkingProfileId,
            String status,
            int chunkCount,
            String graphSyncStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
