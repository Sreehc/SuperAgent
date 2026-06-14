package com.superagent.knowledge.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.infra.storage.ObjectStorageService;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.DocumentTask;
import com.superagent.knowledge.domain.DocumentTaskStatus;
import com.superagent.knowledge.domain.DocumentTaskType;
import com.superagent.knowledge.domain.ChunkingProfile;
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.domain.KnowledgeBaseVisibility;
import com.superagent.knowledge.domain.KnowledgeDomain;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import com.superagent.knowledge.domain.KnowledgeDocumentVersion;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.messaging.DocumentTaskProducer;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.settings.repository.AuditLogRepository;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final KnowledgeRepository knowledgeRepository;
    private final ObjectStorageService objectStorageService;
    private final SuperAgentProperties properties;
    private final ObjectProvider<DocumentTaskProducer> documentTaskProducerProvider;
    private final ObjectProvider<DocumentProcessingService> documentProcessingServiceProvider;
    private final DocumentGraphService documentGraphService;
    private final AuditLogRepository auditLogRepository;
    private final Tika tika = new Tika();

    public KnowledgeService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            KnowledgeRepository knowledgeRepository,
            ObjectStorageService objectStorageService,
            SuperAgentProperties properties,
            ObjectProvider<DocumentTaskProducer> documentTaskProducerProvider,
            ObjectProvider<DocumentProcessingService> documentProcessingServiceProvider,
            DocumentGraphService documentGraphService,
            AuditLogRepository auditLogRepository
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.knowledgeRepository = knowledgeRepository;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
        this.documentTaskProducerProvider = documentTaskProducerProvider;
        this.documentProcessingServiceProvider = documentProcessingServiceProvider;
        this.documentGraphService = documentGraphService;
        this.auditLogRepository = auditLogRepository;
    }

    public KnowledgeBase createKnowledgeBase(String name, String description, KnowledgeBaseVisibility visibility) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        try {
            KnowledgeBase knowledgeBase = knowledgeRepository.createKnowledgeBase(
                    tenantContext.tenantId(),
                    requireName(name),
                    normalizeNullable(description),
                    visibility == null ? KnowledgeBaseVisibility.tenant : visibility,
                    KnowledgeBaseStatus.draft,
                    principal.userId()
            );
            auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "knowledge_base.created", "knowledge_base", knowledgeBase.id(), Map.of(
                    "name", knowledgeBase.name(),
                    "visibility", knowledgeBase.visibility().name()
            ));
            return knowledgeBase;
        } catch (DuplicateKeyException exception) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Knowledge base name already exists");
        }
    }

    public PagedResult<KnowledgeBase> listKnowledgeBases(Integer page, Integer pageSize, String status, String keyword) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        boolean admin = isAdmin(principal);
        long total = knowledgeRepository.countVisibleKnowledgeBases(tenantContext.tenantId(), admin, normalizeNullable(status), keyword);
        List<KnowledgeBase> items = knowledgeRepository.findVisibleKnowledgeBases(
                tenantContext.tenantId(),
                admin,
                normalizeNullable(status),
                keyword,
                resolvedPage,
                resolvedPageSize
        );
        return new PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public KnowledgeBase getKnowledgeBase(long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireVisibleKnowledgeBase(knowledgeBaseId);
        if (!isAdmin(currentAuthenticatedUser.get()) && knowledgeBase.status() != KnowledgeBaseStatus.published) {
            throw new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge base not found");
        }
        return knowledgeBase;
    }

    public KnowledgeBase updateKnowledgeBase(long knowledgeBaseId, String name, String description, KnowledgeBaseVisibility visibility, KnowledgeBaseStatus status) {
        requireAdminRole();
        KnowledgeBase existing = requireKnowledgeBase(knowledgeBaseId);
        TenantContext tenantContext = requireTenantContext();
        KnowledgeBase updated = knowledgeRepository.updateKnowledgeBase(
                tenantContext.tenantId(),
                existing.id(),
                name == null || name.isBlank() ? existing.name() : name.trim(),
                description == null ? existing.description() : normalizeNullable(description),
                visibility == null ? existing.visibility() : visibility,
                status == null ? existing.status() : status
        );
        auditLogRepository.append(tenantContext.tenantId(), currentAuthenticatedUser.get().userId(), "knowledge_base.updated", "knowledge_base", updated.id(), Map.of(
                "status", updated.status().name(),
                "visibility", updated.visibility().name()
        ));
        return updated;
    }

    public boolean deleteKnowledgeBase(long knowledgeBaseId) {
        requireAdminRole();
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        TenantContext tenantContext = requireTenantContext();
        boolean deleted = knowledgeRepository.softDeleteKnowledgeBase(tenantContext.tenantId(), knowledgeBase.id());
        if (deleted) {
            auditLogRepository.append(tenantContext.tenantId(), currentAuthenticatedUser.get().userId(), "knowledge_base.deleted", "knowledge_base", knowledgeBase.id(), Map.of("name", knowledgeBase.name()));
        }
        return deleted;
    }

    public boolean deleteDocument(long documentId) {
        requireAdminRole();
        KnowledgeDocument document = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        boolean deleted = knowledgeRepository.softDeleteDocument(tenantContext.tenantId(), document.id());
        if (deleted) {
            auditLogRepository.append(tenantContext.tenantId(), currentAuthenticatedUser.get().userId(), "knowledge_document.deleted", "knowledge_document", document.id(), Map.of(
                    "title", document.title(),
                    "knowledgeBaseId", document.knowledgeBaseId()
            ));
        }
        return deleted;
    }

    public KnowledgeDocument updateDocumentMetadata(
            long documentId,
            String title,
            String category,
            String tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) {
        requireAdminRole();
        KnowledgeDocument existing = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        Long resolvedKnowledgeDomainId = knowledgeDomainId == null ? existing.knowledgeDomainId() : knowledgeDomainId;
        if (resolvedKnowledgeDomainId != null) {
            knowledgeRepository.getKnowledgeDomain(tenantContext.tenantId(), resolvedKnowledgeDomainId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge domain not found"));
        }
        ChunkingProfile chunkingProfile = resolveChunkingProfile(
                tenantContext.tenantId(),
                chunkingProfileId == null ? existing.chunkingProfileId() : chunkingProfileId
        );
        return knowledgeRepository.updateDocumentMetadata(
                tenantContext.tenantId(),
                existing.id(),
                title == null || title.isBlank() ? existing.title() : title.trim(),
                resolvedKnowledgeDomainId,
                chunkingProfile == null ? null : chunkingProfile.id(),
                category == null ? existing.category() : normalizeNullable(category),
                tags == null ? existing.tags() : parseTags(tags)
        );
    }

    public KnowledgeDocument updateDocumentGovernance(
            long documentId,
            Long ownerUserId,
            OffsetDateTime expiresAt,
            String reviewStatus,
            String title,
            String category,
            String tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) {
        requireAdminRole();
        KnowledgeDocument existing = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();

        if (title != null || category != null || tags != null || knowledgeDomainId != null || chunkingProfileId != null) {
            existing = updateDocumentMetadata(documentId, title, category, tags, knowledgeDomainId, chunkingProfileId);
        }

        String normalizedReviewStatus = existing.reviewStatus() == null ? "approved" : existing.reviewStatus();
        Long reviewedBy = existing.reviewedBy();
        OffsetDateTime reviewedAt = existing.reviewedAt();
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            String requested = reviewStatus.trim();
            if (!java.util.Set.of("draft", "pending_review", "approved", "rejected").contains(requested)) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid review status");
            }
            normalizedReviewStatus = requested;
            if ("approved".equals(requested) || "rejected".equals(requested)) {
                reviewedBy = principal.userId();
                reviewedAt = OffsetDateTime.now();
            } else {
                reviewedBy = null;
                reviewedAt = null;
            }
        }

        KnowledgeDocument updated = knowledgeRepository.updateDocumentGovernance(
                tenantContext.tenantId(),
                existing.id(),
                ownerUserId == null ? existing.ownerUserId() : ownerUserId,
                expiresAt == null ? existing.expiresAt() : expiresAt,
                normalizedReviewStatus,
                reviewedBy,
                reviewedAt
        );
        java.util.HashMap<String, Object> auditDetail = new java.util.HashMap<>();
        auditDetail.put("ownerUserId", updated.ownerUserId());
        auditDetail.put("expiresAt", updated.expiresAt() == null ? null : updated.expiresAt().toString());
        auditDetail.put("reviewStatus", updated.reviewStatus());
        auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "knowledge_document.metadata_updated", "knowledge_document", updated.id(), auditDetail);
        return updated;
    }

    public UploadedDocumentResult uploadDocument(
            long knowledgeBaseId,
            MultipartFile file,
            String title,
            String category,
            String tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) {
        requireAdminRole();
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        String detectedContentType = validateUploadFile(file);

        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        KnowledgeDomain domain = knowledgeDomainId == null
                ? null
                : knowledgeRepository.getKnowledgeDomain(tenantContext.tenantId(), knowledgeDomainId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge domain not found"));
        ChunkingProfile chunkingProfile = resolveChunkingProfile(tenantContext.tenantId(), chunkingProfileId);
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String fileType = extractExtension(fileName);
        String resolvedTitle = title == null || title.isBlank() ? fileName : title.trim();
        List<String> resolvedTags = parseTags(tags);
        String objectKey = buildObjectKey(tenantContext.tenantId(), knowledgeBase.id(), fileType);
        String contentHash = calculateHash(file);

        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.store(objectKey, inputStream, file.getSize(), detectedContentType);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload document");
        }

        KnowledgeDocument document = knowledgeRepository.createKnowledgeDocument(
                tenantContext.tenantId(),
                knowledgeBase.id(),
                domain == null ? null : domain.id(),
                chunkingProfile == null ? null : chunkingProfile.id(),
                resolvedTitle,
                fileName,
                fileType,
                file.getSize(),
                objectKey,
                contentHash,
                KnowledgeDocumentStatus.uploaded,
                normalizeNullable(category),
                resolvedTags,
                principal.userId()
        );
        knowledgeRepository.createDocumentVersion(
                tenantContext.tenantId(),
                document.id(),
                1,
                chunkingProfile == null ? null : chunkingProfile.id(),
                "uploaded",
                principal.userId(),
                buildVersionMetadata(
                        "upload",
                        domain == null ? null : domain.id(),
                        chunkingProfile == null ? "recursive" : chunkingProfile.strategy(),
                        null
                ),
                0,
                "pending"
        );
        DocumentTask task = knowledgeRepository.createDocumentTask(
                tenantContext.tenantId(),
                document.id(),
                DocumentTaskType.parse,
                DocumentTaskStatus.pending,
                "Document uploaded to MinIO at " + OffsetDateTime.now()
        );
        publishTaskIfEnabled(new DocumentTaskMessage(tenantContext.tenantId(), document.id(), task.id(), "upload"));
        auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "knowledge_document.uploaded", "knowledge_document", document.id(), Map.of(
                "knowledgeBaseId", knowledgeBase.id(),
                "fileName", fileName,
                "fileType", fileType,
                "fileSize", file.getSize()
        ));
        return new UploadedDocumentResult(document, task);
    }

    public List<BatchUploadItem> uploadDocuments(
            long knowledgeBaseId,
            List<MultipartFile> files,
            String category,
            String tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "files are required");
        }
        if (files.size() > 20) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "batch upload supports at most 20 files");
        }
        java.util.ArrayList<BatchUploadItem> items = new java.util.ArrayList<>(files.size());
        for (MultipartFile file : files) {
            String fileName = file == null || file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            try {
                UploadedDocumentResult result = uploadDocument(knowledgeBaseId, file, null, category, tags, knowledgeDomainId, chunkingProfileId);
                items.add(BatchUploadItem.success(fileName, result));
            } catch (AppException exception) {
                items.add(BatchUploadItem.failure(fileName, exception.getErrorCode().name(), exception.getMessage()));
            } catch (Exception exception) {
                items.add(BatchUploadItem.failure(fileName, "INTERNAL_ERROR", exception.getMessage() == null ? "upload failed" : exception.getMessage()));
            }
        }
        return items;
    }

    public PagedResult<KnowledgeDocument> listDocuments(
            long knowledgeBaseId,
            Integer page,
            Integer pageSize,
            String status,
            String fileType,
            String keyword,
            String tag
    ) {
        requireVisibleKnowledgeBase(knowledgeBaseId);
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        long total = knowledgeRepository.countKnowledgeDocuments(
                tenantContext.tenantId(),
                knowledgeBaseId,
                normalizeNullable(status),
                normalizeNullable(fileType),
                keyword,
                tag
        );
        List<KnowledgeDocument> items = knowledgeRepository.findKnowledgeDocuments(
                tenantContext.tenantId(),
                knowledgeBaseId,
                normalizeNullable(status),
                normalizeNullable(fileType),
                keyword,
                tag,
                resolvedPage,
                resolvedPageSize
        );
        return new PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public KnowledgeDomain createKnowledgeDomain(String code, String name, String description) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.createKnowledgeDomain(
                tenantContext.tenantId(),
                code.trim(),
                requireName(name),
                normalizeNullable(description),
                Map.of()
        );
    }

    public List<KnowledgeDomain> listKnowledgeDomains() {
        requireAdminRole();
        return knowledgeRepository.listKnowledgeDomains(requireTenantContext().tenantId());
    }

    public KnowledgeDomain updateKnowledgeDomain(long id, String name, String description, String status) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        KnowledgeDomain existing = knowledgeRepository.getKnowledgeDomain(tenantContext.tenantId(), id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge domain not found"));
        return knowledgeRepository.updateKnowledgeDomain(
                tenantContext.tenantId(),
                id,
                name == null || name.isBlank() ? existing.name() : name.trim(),
                description == null ? existing.description() : normalizeNullable(description),
                status == null || status.isBlank() ? existing.status() : status.trim(),
                existing.metadata()
        );
    }

    public ChunkingProfile createChunkingProfile(String code, String name, String strategy, boolean isDefault, Map<String, Object> config) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.createChunkingProfile(
                tenantContext.tenantId(),
                code.trim(),
                requireName(name),
                strategy,
                config == null ? Map.of() : config,
                isDefault
        );
    }

    public List<ChunkingProfile> listChunkingProfiles() {
        requireAdminRole();
        return knowledgeRepository.listChunkingProfiles(requireTenantContext().tenantId());
    }

    public ChunkingProfile updateChunkingProfile(long id, String name, String strategy, Boolean isDefault, String status, Map<String, Object> config) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        ChunkingProfile existing = knowledgeRepository.getChunkingProfile(tenantContext.tenantId(), id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Chunking profile not found"));
        return knowledgeRepository.updateChunkingProfile(
                tenantContext.tenantId(),
                id,
                name == null || name.isBlank() ? existing.name() : name.trim(),
                strategy == null || strategy.isBlank() ? existing.strategy() : strategy.trim(),
                config == null ? existing.config() : config,
                isDefault == null ? existing.isDefault() : isDefault,
                status == null || status.isBlank() ? existing.status() : status.trim()
        );
    }

    public List<KnowledgeDocumentVersion> listDocumentVersions(long documentId) {
        KnowledgeDocument document = requireVisibleDocument(documentId);
        return knowledgeRepository.listDocumentVersions(requireTenantContext().tenantId(), document.id());
    }

    public ReprocessDocumentResult reprocessDocument(long documentId, String reason) {
        return reprocessDocument(documentId, reason, null);
    }

    public ReprocessDocumentResult reprocessDocument(long documentId, String reason, Long chunkingProfileId) {
        requireAdminRole();
        KnowledgeDocument document = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        ChunkingProfile chunkingProfile = resolveChunkingProfile(tenantContext.tenantId(), chunkingProfileId == null ? document.chunkingProfileId() : chunkingProfileId);
        int nextVersion = knowledgeRepository.listDocumentVersions(tenantContext.tenantId(), document.id()).stream()
                .mapToInt(KnowledgeDocumentVersion::versionNo)
                .max()
                .orElse(0) + 1;
        knowledgeRepository.createDocumentVersion(
                tenantContext.tenantId(),
                document.id(),
                nextVersion,
                chunkingProfile == null ? null : chunkingProfile.id(),
                "reprocessing",
                currentAuthenticatedUser.get().userId(),
                buildVersionMetadata(
                        "manual_reprocess",
                        document.knowledgeDomainId(),
                        chunkingProfile == null ? "recursive" : chunkingProfile.strategy(),
                        reason
                ),
                0,
                "pending"
        );
        knowledgeRepository.updateDocumentStatus(
                tenantContext.tenantId(),
                document.id(),
                KnowledgeDocumentStatus.uploaded,
                0,
                null,
                null,
                nextVersion,
                "pending",
                null
        );
        DocumentTask task = knowledgeRepository.createDocumentTask(
                tenantContext.tenantId(),
                document.id(),
                DocumentTaskType.reprocess,
                DocumentTaskStatus.pending,
                (reason == null || reason.isBlank() ? "Manual reprocess requested" : reason.trim()) + " at " + OffsetDateTime.now()
        );
        publishTaskIfEnabled(new DocumentTaskMessage(tenantContext.tenantId(), document.id(), task.id(), "manual_reprocess"));
        auditLogRepository.append(tenantContext.tenantId(), currentAuthenticatedUser.get().userId(), "knowledge_document.reprocessed", "knowledge_document", document.id(), Map.of(
                "taskId", task.id(),
                "reason", reason == null ? "manual_reprocess" : reason
        ));
        return new ReprocessDocumentResult(document.id(), task.id(), task.status().name());
    }

    public KnowledgeDocument getDocument(long documentId) {
        return requireVisibleDocument(documentId);
    }

    public PagedResult<DocumentChunk> listDocumentChunks(long documentId, Integer page, Integer pageSize) {
        KnowledgeDocument document = requireVisibleDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 200);
        long total = knowledgeRepository.countDocumentChunks(tenantContext.tenantId(), document.id());
        List<DocumentChunk> items = knowledgeRepository.listDocumentChunks(
                tenantContext.tenantId(),
                document.id(),
                resolvedPage,
                resolvedPageSize
        );
        return new PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public List<DocumentTask> listDocumentTasks(long documentId) {
        KnowledgeDocument document = requireVisibleDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.listDocumentTasks(tenantContext.tenantId(), document.id());
    }

    public DocumentGraphService.DocumentGraphSnapshot getDocumentGraph(long documentId) {
        KnowledgeDocument document = requireVisibleDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        KnowledgeBase knowledgeBase = requireVisibleKnowledgeBase(document.knowledgeBaseId());
        return documentGraphService.buildGraph(tenantContext.tenantId(), knowledgeBase, document);
    }

    public DocumentGraphService.DocumentGraphSnapshot rebuildDocumentGraph(long documentId) {
        requireAdminRole();
        KnowledgeDocument document = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        KnowledgeBase knowledgeBase = requireKnowledgeBase(document.knowledgeBaseId());
        return documentGraphService.synchronizeGraph(tenantContext.tenantId(), knowledgeBase, document);
    }

    public List<KnowledgeDocument> findExpiringDocuments(int withinDays) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int normalized = withinDays <= 0 ? 30 : Math.min(withinDays, 365);
        return knowledgeRepository.findExpiringDocuments(tenantContext.tenantId(), normalized);
    }

    public List<KnowledgeRepository.DuplicateDocumentGroup> findDuplicateDocuments() {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.findDuplicateDocuments(tenantContext.tenantId());
    }

    private KnowledgeBase requireVisibleKnowledgeBase(long knowledgeBaseId) {
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.getKnowledgeBase(tenantContext.tenantId(), knowledgeBaseId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge base not found"));
    }

    private KnowledgeBase requireKnowledgeBase(long knowledgeBaseId) {
        return requireVisibleKnowledgeBase(knowledgeBaseId);
    }

    private KnowledgeDocument requireVisibleDocument(long documentId) {
        KnowledgeDocument document = requireKnowledgeDocument(documentId);
        KnowledgeBase knowledgeBase = requireVisibleKnowledgeBase(document.knowledgeBaseId());
        if (!isAdmin(currentAuthenticatedUser.get()) && knowledgeBase.status() != KnowledgeBaseStatus.published) {
            throw new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found");
        }
        return document;
    }

    private KnowledgeDocument requireKnowledgeDocument(long documentId) {
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.getKnowledgeDocument(tenantContext.tenantId(), documentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document not found"));
    }

    private ChunkingProfile resolveChunkingProfile(long tenantId, Long chunkingProfileId) {
        if (chunkingProfileId != null) {
            return knowledgeRepository.getChunkingProfile(tenantId, chunkingProfileId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Chunking profile not found"));
        }
        return knowledgeRepository.listChunkingProfiles(tenantId).stream()
                .filter(profile -> profile.isDefault() && "active".equalsIgnoreCase(profile.status()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildVersionMetadata(
            String trigger,
            Long knowledgeDomainId,
            String chunkingStrategy,
            String reason
    ) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("trigger", trigger);
        metadata.put("chunkingStrategy", chunkingStrategy == null || chunkingStrategy.isBlank() ? "recursive" : chunkingStrategy);
        if (knowledgeDomainId != null) {
            metadata.put("knowledgeDomainId", knowledgeDomainId);
        }
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason.trim());
        }
        return metadata;
    }

    private void requireAdminRole() {
        if (!isAdmin(currentAuthenticatedUser.get())) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Knowledge base admin permission required");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.currentRole() == TenantRole.OWNER || principal.currentRole() == TenantRole.ADMIN;
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Knowledge base name is required");
        }
        return name.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize, int defaultValue, int maxValue) {
        if (pageSize == null || pageSize < 1) {
            return defaultValue;
        }
        return Math.min(pageSize, maxValue);
    }

    private String validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Document file is required");
        }
        if (file.getSize() > properties.getStorage().getUploadMaxFileSizeBytes()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Document file exceeds max allowed size");
        }
        String extension = extractExtension(file.getOriginalFilename());
        boolean allowed = properties.getStorage().getUploadAllowedExtensions().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(extension));
        if (!allowed) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Document file type is not allowed");
        }
        String detectedContentType = detectContentType(file);
        boolean contentTypeAllowed = isContentTypeAllowedForExtension(extension, detectedContentType);
        if (!contentTypeAllowed) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Document content type is not allowed");
        }
        return detectedContentType;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isContentTypeAllowedForExtension(String extension, String detectedContentType) {
        String normalized = detectedContentType.toLowerCase(Locale.ROOT);
        if (properties.getStorage().getUploadAllowedContentTypes().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .noneMatch(item -> item.equals(normalized))) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> normalized.equals("application/pdf");
            case "doc" -> normalized.equals("application/msword") || normalized.equals("application/x-tika-msoffice");
            case "docx" -> normalized.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "ppt" -> normalized.equals("application/vnd.ms-powerpoint") || normalized.equals("application/x-tika-msoffice");
            case "pptx" -> normalized.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "md" -> normalized.equals("text/plain")
                    || normalized.equals("text/markdown")
                    || normalized.equals("text/x-web-markdown")
                    || normalized.equals("application/octet-stream");
            case "txt" -> normalized.equals("text/plain") || normalized.equals("text/markdown") || normalized.equals("application/octet-stream");
            case "html" -> normalized.equals("text/html") || normalized.equals("application/xhtml+xml");
            default -> false;
        };
    }

    private String buildObjectKey(long tenantId, long knowledgeBaseId, String extension) {
        return "tenant/" + tenantId
                + "/knowledge-base/" + knowledgeBaseId
                + "/raw/" + UUID.randomUUID() + "." + extension;
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String calculateHash(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to calculate file hash");
        }
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String detected = tika.detect(inputStream, file.getOriginalFilename());
            if (detected == null || detected.isBlank()) {
                return "application/octet-stream";
            }
            return detected;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to detect document content type");
        }
    }

    private void publishTaskIfEnabled(DocumentTaskMessage message) {
        if (!Boolean.TRUE.equals(properties.getMessaging().getKafkaEnabled())) {
            if (Boolean.TRUE.equals(properties.getMessaging().getInlineProcessingWhenKafkaDisabled())) {
                DocumentProcessingService documentProcessingService = documentProcessingServiceProvider.getIfAvailable();
                if (documentProcessingService == null) {
                    throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Inline document processing is not configured");
                }
                documentProcessingService.process(message);
            }
            return;
        }
        DocumentTaskProducer producer = documentTaskProducerProvider.getIfAvailable();
        if (producer == null) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Kafka producer is not configured");
        }
        producer.publish(message);
    }

    public record UploadedDocumentResult(KnowledgeDocument document, DocumentTask task) {
    }

    public record BatchUploadItem(
            String fileName,
            String status,
            Long documentId,
            Long taskId,
            String errorCode,
            String errorMessage,
            UploadedDocumentResult result
    ) {
        public static BatchUploadItem success(String fileName, UploadedDocumentResult result) {
            return new BatchUploadItem(fileName, "accepted", result.document().id(), result.task().id(), null, null, result);
        }

        public static BatchUploadItem failure(String fileName, String code, String message) {
            return new BatchUploadItem(fileName, "failed", null, null, code, message, null);
        }
    }

    public record ReprocessDocumentResult(long documentId, long taskId, String status) {
    }

    public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
    }
}
