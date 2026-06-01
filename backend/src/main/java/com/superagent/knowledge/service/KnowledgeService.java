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
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.domain.KnowledgeBaseVisibility;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.messaging.DocumentTaskProducer;
import com.superagent.knowledge.repository.KnowledgeRepository;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

    public KnowledgeService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            KnowledgeRepository knowledgeRepository,
            ObjectStorageService objectStorageService,
            SuperAgentProperties properties,
            ObjectProvider<DocumentTaskProducer> documentTaskProducerProvider
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.knowledgeRepository = knowledgeRepository;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
        this.documentTaskProducerProvider = documentTaskProducerProvider;
    }

    public KnowledgeBase createKnowledgeBase(String name, String description, KnowledgeBaseVisibility visibility) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        try {
            return knowledgeRepository.createKnowledgeBase(
                    tenantContext.tenantId(),
                    requireName(name),
                    normalizeNullable(description),
                    visibility == null ? KnowledgeBaseVisibility.tenant : visibility,
                    KnowledgeBaseStatus.draft,
                    principal.userId()
            );
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
        return knowledgeRepository.updateKnowledgeBase(
                tenantContext.tenantId(),
                existing.id(),
                name == null || name.isBlank() ? existing.name() : name.trim(),
                description == null ? existing.description() : normalizeNullable(description),
                visibility == null ? existing.visibility() : visibility,
                status == null ? existing.status() : status
        );
    }

    public boolean deleteKnowledgeBase(long knowledgeBaseId) {
        requireAdminRole();
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        TenantContext tenantContext = requireTenantContext();
        return knowledgeRepository.softDeleteKnowledgeBase(tenantContext.tenantId(), knowledgeBase.id());
    }

    public UploadedDocumentResult uploadDocument(
            long knowledgeBaseId,
            MultipartFile file,
            String title,
            String category,
            String tags
    ) {
        requireAdminRole();
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        validateUploadFile(file);

        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String fileType = extractExtension(fileName);
        String resolvedTitle = title == null || title.isBlank() ? fileName : title.trim();
        List<String> resolvedTags = parseTags(tags);
        String objectKey = buildObjectKey(tenantContext.tenantId(), knowledgeBase.id(), fileType);
        String contentHash = calculateHash(file);

        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.store(objectKey, inputStream, file.getSize(), normalizeContentType(file.getContentType()));
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload document");
        }

        KnowledgeDocument document = knowledgeRepository.createKnowledgeDocument(
                tenantContext.tenantId(),
                knowledgeBase.id(),
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
        DocumentTask task = knowledgeRepository.createDocumentTask(
                tenantContext.tenantId(),
                document.id(),
                DocumentTaskType.parse,
                DocumentTaskStatus.pending,
                "Document uploaded to MinIO at " + OffsetDateTime.now()
        );
        publishTaskIfEnabled(new DocumentTaskMessage(tenantContext.tenantId(), document.id(), task.id(), "upload"));
        return new UploadedDocumentResult(document, task);
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

    public ReprocessDocumentResult reprocessDocument(long documentId, String reason) {
        requireAdminRole();
        KnowledgeDocument document = requireKnowledgeDocument(documentId);
        TenantContext tenantContext = requireTenantContext();
        DocumentTask task = knowledgeRepository.createDocumentTask(
                tenantContext.tenantId(),
                document.id(),
                DocumentTaskType.reprocess,
                DocumentTaskStatus.pending,
                (reason == null || reason.isBlank() ? "Manual reprocess requested" : reason.trim()) + " at " + OffsetDateTime.now()
        );
        publishTaskIfEnabled(new DocumentTaskMessage(tenantContext.tenantId(), document.id(), task.id(), "manual_reprocess"));
        return new ReprocessDocumentResult(document.id(), task.id(), task.status().name());
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

    private void validateUploadFile(MultipartFile file) {
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
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
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

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private void publishTaskIfEnabled(DocumentTaskMessage message) {
        if (!Boolean.TRUE.equals(properties.getMessaging().getKafkaEnabled())) {
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

    public record ReprocessDocumentResult(long documentId, long taskId, String status) {
    }

    public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
    }
}
