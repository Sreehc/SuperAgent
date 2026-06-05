package com.superagent.knowledge.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.superagent.rag.domain.RetrievalResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<KnowledgeBase> knowledgeBaseRowMapper = (rs, rowNum) -> mapKnowledgeBase(rs);
    private final RowMapper<KnowledgeDocument> knowledgeDocumentRowMapper = (rs, rowNum) -> mapKnowledgeDocument(rs);
    private final RowMapper<DocumentTask> documentTaskRowMapper = (rs, rowNum) -> mapDocumentTask(rs);
    private final RowMapper<DocumentChunk> documentChunkRowMapper = (rs, rowNum) -> mapDocumentChunk(rs);
    private final RowMapper<KnowledgeDomain> knowledgeDomainRowMapper = (rs, rowNum) -> mapKnowledgeDomain(rs);
    private final RowMapper<ChunkingProfile> chunkingProfileRowMapper = (rs, rowNum) -> mapChunkingProfile(rs);
    private final RowMapper<KnowledgeDocumentVersion> documentVersionRowMapper = (rs, rowNum) -> mapDocumentVersion(rs);

    public KnowledgeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public KnowledgeBase createKnowledgeBase(
            long tenantId,
            String name,
            String description,
            KnowledgeBaseVisibility visibility,
            KnowledgeBaseStatus status,
            long ownerId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_base (tenant_id, name, description, visibility, status, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setString(4, visibility.name());
            statement.setString(5, status.name());
            statement.setLong(6, ownerId);
            return statement;
        }, keyHolder);
        return getKnowledgeBase(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public KnowledgeDomain createKnowledgeDomain(long tenantId, String code, String name, String description, Map<String, Object> metadata) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_domain (tenant_id, code, name, description, metadata)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.setString(4, description);
            statement.setString(5, writeMetadata(metadata));
            return statement;
        }, keyHolder);
        return getKnowledgeDomain(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<KnowledgeDomain> listKnowledgeDomains(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, code, name, description, status, metadata, created_at, updated_at
                        FROM knowledge_domain
                        WHERE tenant_id = ?
                          AND deleted_at IS NULL
                        ORDER BY code ASC
                        """,
                knowledgeDomainRowMapper,
                tenantId
        );
    }

    public Optional<KnowledgeDomain> getKnowledgeDomain(long tenantId, long id) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, code, name, description, status, metadata, created_at, updated_at
                        FROM knowledge_domain
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                knowledgeDomainRowMapper,
                tenantId,
                id
        ).stream().findFirst();
    }

    public KnowledgeDomain updateKnowledgeDomain(long tenantId, long id, String name, String description, String status, Map<String, Object> metadata) {
        jdbcTemplate.update("""
                        UPDATE knowledge_domain
                        SET name = ?, description = ?, status = ?, metadata = ?::jsonb, updated_at = NOW()
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                name, description, status, writeMetadata(metadata), tenantId, id
        );
        return getKnowledgeDomain(tenantId, id).orElseThrow();
    }

    public ChunkingProfile createChunkingProfile(long tenantId, String code, String name, String strategy, Map<String, Object> config, boolean isDefault) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO chunking_profile (tenant_id, code, name, strategy, config_json, is_default)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.setString(4, strategy);
            statement.setString(5, writeMetadata(config));
            statement.setBoolean(6, isDefault);
            return statement;
        }, keyHolder);
        return getChunkingProfile(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public List<ChunkingProfile> listChunkingProfiles(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, code, name, strategy, config_json, is_default, status, created_at, updated_at
                        FROM chunking_profile
                        WHERE tenant_id = ?
                          AND deleted_at IS NULL
                        ORDER BY code ASC
                        """,
                chunkingProfileRowMapper,
                tenantId
        );
    }

    public Optional<ChunkingProfile> getChunkingProfile(long tenantId, long id) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, code, name, strategy, config_json, is_default, status, created_at, updated_at
                        FROM chunking_profile
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                chunkingProfileRowMapper,
                tenantId,
                id
        ).stream().findFirst();
    }

    public ChunkingProfile updateChunkingProfile(long tenantId, long id, String name, String strategy, Map<String, Object> config, boolean isDefault, String status) {
        jdbcTemplate.update("""
                        UPDATE chunking_profile
                        SET name = ?, strategy = ?, config_json = ?::jsonb, is_default = ?, status = ?, updated_at = NOW()
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                name, strategy, writeMetadata(config), isDefault, status, tenantId, id
        );
        return getChunkingProfile(tenantId, id).orElseThrow();
    }

    public KnowledgeDocumentVersion createDocumentVersion(long tenantId, long documentId, int versionNo, Long chunkingProfileId, String status, Long createdBy) {
        return createDocumentVersion(tenantId, documentId, versionNo, chunkingProfileId, status, createdBy, Map.of(), 0, "pending");
    }

    public KnowledgeDocumentVersion createDocumentVersion(
            long tenantId,
            long documentId,
            int versionNo,
            Long chunkingProfileId,
            String status,
            Long createdBy,
            Map<String, Object> metadata,
            int chunkCount,
            String graphSyncStatus
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_document_version (
                        tenant_id,
                        document_id,
                        version_no,
                        chunking_profile_id,
                        status,
                        chunk_count,
                        graph_sync_status,
                        metadata,
                        created_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setLong(2, documentId);
            statement.setInt(3, versionNo);
            if (chunkingProfileId == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, chunkingProfileId);
            }
            statement.setString(5, status);
            statement.setInt(6, chunkCount);
            statement.setString(7, graphSyncStatus);
            statement.setString(8, writeMetadata(metadata == null ? Map.of() : metadata));
            if (createdBy == null) {
                statement.setNull(9, Types.BIGINT);
            } else {
                statement.setLong(9, createdBy);
            }
            return statement;
        }, keyHolder);
        return getDocumentVersion(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<KnowledgeDocumentVersion> findLatestDocumentVersion(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, document_id, version_no, chunking_profile_id, status, chunk_count, graph_sync_status,
                               metadata, created_by, created_at, updated_at
                        FROM knowledge_document_version
                        WHERE tenant_id = ?
                          AND document_id = ?
                        ORDER BY version_no DESC, id DESC
                        LIMIT 1
                        """,
                documentVersionRowMapper,
                tenantId,
                documentId
        ).stream().findFirst();
    }

    public KnowledgeDocumentVersion updateDocumentVersion(
            long tenantId,
            long versionId,
            String status,
            Integer chunkCount,
            String graphSyncStatus,
            Map<String, Object> metadata
    ) {
        KnowledgeDocumentVersion existing = getDocumentVersion(tenantId, versionId).orElseThrow();
        jdbcTemplate.update("""
                        UPDATE knowledge_document_version
                        SET status = ?,
                            chunk_count = COALESCE(?, chunk_count),
                            graph_sync_status = ?,
                            metadata = ?::jsonb,
                            updated_at = NOW()
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                status == null ? existing.status() : status,
                chunkCount,
                graphSyncStatus == null ? existing.graphSyncStatus() : graphSyncStatus,
                writeMetadata(metadata == null ? existing.metadata() : metadata),
                tenantId,
                versionId
        );
        return getDocumentVersion(tenantId, versionId).orElseThrow();
    }

    public List<KnowledgeDocumentVersion> listDocumentVersions(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, document_id, version_no, chunking_profile_id, status, chunk_count, graph_sync_status,
                               metadata, created_by, created_at, updated_at
                        FROM knowledge_document_version
                        WHERE tenant_id = ?
                          AND document_id = ?
                        ORDER BY version_no DESC, id DESC
                        """,
                documentVersionRowMapper,
                tenantId,
                documentId
        );
    }

    public Optional<KnowledgeDocumentVersion> getDocumentVersion(long tenantId, long id) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, document_id, version_no, chunking_profile_id, status, chunk_count, graph_sync_status,
                               metadata, created_by, created_at, updated_at
                        FROM knowledge_document_version
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                documentVersionRowMapper,
                tenantId,
                id
        ).stream().findFirst();
    }

    public Optional<KnowledgeBase> getKnowledgeBase(long tenantId, long knowledgeBaseId) {
        return jdbcTemplate.query("""
                        SELECT kb.id,
                               kb.tenant_id,
                               kb.name,
                               kb.description,
                               kb.visibility,
                               kb.status,
                               kb.owner_id,
                               kb.created_at,
                               kb.updated_at,
                               COALESCE(doc_stat.document_count, 0) AS document_count
                        FROM knowledge_base kb
                        LEFT JOIN (
                            SELECT knowledge_base_id, COUNT(*) AS document_count
                            FROM knowledge_document
                            WHERE deleted_at IS NULL
                            GROUP BY knowledge_base_id
                        ) doc_stat ON doc_stat.knowledge_base_id = kb.id
                        WHERE kb.tenant_id = ?
                          AND kb.id = ?
                          AND kb.deleted_at IS NULL
                        """,
                knowledgeBaseRowMapper,
                tenantId,
                knowledgeBaseId
        ).stream().findFirst();
    }

    public long countVisibleKnowledgeBases(long tenantId, boolean admin, String status, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM knowledge_base kb
                WHERE kb.tenant_id = ?
                  AND kb.deleted_at IS NULL
                """);
        if (!admin) {
            sql.append(" AND kb.status = 'published'");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND kb.status = '").append(status).append("'");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND kb.name ILIKE ?");
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, tenantId, "%" + keyword.trim() + "%");
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, tenantId);
    }

    public List<KnowledgeBase> findVisibleKnowledgeBases(long tenantId, boolean admin, String status, String keyword, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT kb.id,
                       kb.tenant_id,
                       kb.name,
                       kb.description,
                       kb.visibility,
                       kb.status,
                       kb.owner_id,
                       kb.created_at,
                       kb.updated_at,
                       COALESCE(doc_stat.document_count, 0) AS document_count
                FROM knowledge_base kb
                LEFT JOIN (
                    SELECT knowledge_base_id, COUNT(*) AS document_count
                    FROM knowledge_document
                    WHERE deleted_at IS NULL
                    GROUP BY knowledge_base_id
                ) doc_stat ON doc_stat.knowledge_base_id = kb.id
                WHERE kb.tenant_id = ?
                  AND kb.deleted_at IS NULL
                """);

        Object[] args;
        if (keyword != null && !keyword.isBlank()) {
            if (!admin) {
                sql.append(" AND kb.status = 'published'");
            }
            if (status != null && !status.isBlank()) {
                sql.append(" AND kb.status = '").append(status).append("'");
            }
            sql.append(" AND kb.name ILIKE ?");
            sql.append(" ORDER BY kb.updated_at DESC LIMIT ? OFFSET ?");
            args = new Object[]{tenantId, "%" + keyword.trim() + "%", pageSize, (page - 1) * pageSize};
        } else {
            if (!admin) {
                sql.append(" AND kb.status = 'published'");
            }
            if (status != null && !status.isBlank()) {
                sql.append(" AND kb.status = '").append(status).append("'");
            }
            sql.append(" ORDER BY kb.updated_at DESC LIMIT ? OFFSET ?");
            args = new Object[]{tenantId, pageSize, (page - 1) * pageSize};
        }
        return jdbcTemplate.query(sql.toString(), knowledgeBaseRowMapper, args);
    }

    public KnowledgeBase updateKnowledgeBase(
            long tenantId,
            long knowledgeBaseId,
            String name,
            String description,
            KnowledgeBaseVisibility visibility,
            KnowledgeBaseStatus status
    ) {
        jdbcTemplate.update("""
                        UPDATE knowledge_base
                        SET name = ?,
                            description = ?,
                            visibility = ?,
                            status = ?
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                name,
                description,
                visibility.name(),
                status.name(),
                tenantId,
                knowledgeBaseId
        );
        return getKnowledgeBase(tenantId, knowledgeBaseId).orElseThrow();
    }

    public boolean softDeleteKnowledgeBase(long tenantId, long knowledgeBaseId) {
        return jdbcTemplate.update("""
                        UPDATE knowledge_base
                        SET deleted_at = NOW(),
                            status = 'deleted'
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                tenantId,
                knowledgeBaseId
        ) > 0;
    }

    public KnowledgeDocument createKnowledgeDocument(
            long tenantId,
            long knowledgeBaseId,
            Long knowledgeDomainId,
            Long chunkingProfileId,
            String title,
            String fileName,
            String fileType,
            long fileSize,
            String objectKey,
            String contentHash,
            KnowledgeDocumentStatus status,
            String category,
            List<String> tags,
            long createdBy
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String metadataJson = writeMetadata(Map.of(
                "category", category == null ? "" : category,
                "tags", tags == null ? List.of() : tags
        ));
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_document (
                        tenant_id,
                        knowledge_base_id,
                        knowledge_domain_id,
                        chunking_profile_id,
                        title,
                        file_name,
                        file_type,
                        file_size,
                        object_key,
                        content_hash,
                        status,
                        metadata,
                        created_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setLong(2, knowledgeBaseId);
            if (knowledgeDomainId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, knowledgeDomainId);
            }
            if (chunkingProfileId == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, chunkingProfileId);
            }
            statement.setString(5, title);
            statement.setString(6, fileName);
            statement.setString(7, fileType);
            statement.setLong(8, fileSize);
            statement.setString(9, objectKey);
            statement.setString(10, contentHash);
            statement.setString(11, status.name());
            statement.setString(12, metadataJson);
            statement.setLong(13, createdBy);
            return statement;
        }, keyHolder);
        return getKnowledgeDocument(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<KnowledgeDocument> getKnowledgeDocument(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               knowledge_base_id,
                               knowledge_domain_id,
                               chunking_profile_id,
                               graph_sync_status,
                               graph_error_message,
                               active_version_no,
                               title,
                               file_name,
                               file_type,
                               file_size,
                               object_key,
                               content_hash,
                               status,
                               chunk_count,
                               error_message,
                               metadata,
                               created_by,
                               created_at,
                               updated_at
                        FROM knowledge_document
                        WHERE tenant_id = ?
                          AND id = ?
                          AND deleted_at IS NULL
                        """,
                knowledgeDocumentRowMapper,
                tenantId,
                documentId
        ).stream().findFirst();
    }

    public long countKnowledgeDocuments(long tenantId, long knowledgeBaseId, String status, String fileType, String keyword, String tag) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM knowledge_document
                WHERE tenant_id = ?
                  AND knowledge_base_id = ?
                  AND deleted_at IS NULL
                """);
        new DocumentFilterAppender(sql).append(status, fileType, keyword, tag);
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, new DocumentFilterArgs(tenantId, knowledgeBaseId, keyword, tag).toArgs(status, fileType, false));
    }

    public List<KnowledgeDocument> findKnowledgeDocuments(
            long tenantId,
            long knowledgeBaseId,
            String status,
            String fileType,
            String keyword,
            String tag,
            int page,
            int pageSize
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       tenant_id,
                       knowledge_base_id,
                       knowledge_domain_id,
                       chunking_profile_id,
                       graph_sync_status,
                       graph_error_message,
                       active_version_no,
                       title,
                       file_name,
                       file_type,
                       file_size,
                       object_key,
                       content_hash,
                       status,
                       chunk_count,
                       error_message,
                       metadata,
                       created_by,
                       created_at,
                       updated_at
                FROM knowledge_document
                WHERE tenant_id = ?
                  AND knowledge_base_id = ?
                  AND deleted_at IS NULL
                """);
        new DocumentFilterAppender(sql).append(status, fileType, keyword, tag);
        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        return jdbcTemplate.query(
                sql.toString(),
                knowledgeDocumentRowMapper,
                new DocumentFilterArgs(tenantId, knowledgeBaseId, keyword, tag).toArgs(status, fileType, true, pageSize, (page - 1) * pageSize)
        );
    }

    public DocumentTask createDocumentTask(
            long tenantId,
            long documentId,
            DocumentTaskType taskType,
            DocumentTaskStatus status,
            String inputSummary
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO document_task (
                        tenant_id,
                        document_id,
                        task_type,
                        status,
                        input_summary
                    )
                    VALUES (?, ?, ?, ?, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setLong(2, documentId);
            statement.setString(3, taskType.name());
            statement.setString(4, status.name());
            statement.setString(5, inputSummary);
            return statement;
        }, keyHolder);
        return getDocumentTask(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<DocumentTask> getDocumentTask(long tenantId, long taskId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               document_id,
                               task_type,
                               status,
                               attempt_count,
                               input_summary,
                               output_summary,
                               error_message,
                               started_at,
                               finished_at,
                               created_at,
                               updated_at
                        FROM document_task
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                documentTaskRowMapper,
                tenantId,
                taskId
        ).stream().findFirst();
    }

    public List<DocumentTask> listDocumentTasks(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               document_id,
                               task_type,
                               status,
                               attempt_count,
                               input_summary,
                               output_summary,
                               error_message,
                               started_at,
                               finished_at,
                               created_at,
                               updated_at
                        FROM document_task
                        WHERE tenant_id = ?
                          AND document_id = ?
                        ORDER BY created_at DESC, id DESC
                        """,
                documentTaskRowMapper,
                tenantId,
                documentId
        );
    }

    public List<DocumentChunk> listDocumentChunks(long tenantId, long documentId, int page, int pageSize) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               document_id,
                               parent_chunk_id,
                               chunk_no,
                               section_title,
                               content,
                               content_hash,
                               char_count,
                               token_count,
                               metadata,
                               created_at,
                               updated_at
                        FROM document_chunk
                        WHERE tenant_id = ?
                          AND document_id = ?
                        ORDER BY chunk_no ASC
                        LIMIT ? OFFSET ?
                        """,
                documentChunkRowMapper,
                tenantId,
                documentId,
                pageSize,
                (page - 1) * pageSize
        );
    }

    public long countDocumentChunks(long tenantId, long documentId) {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM document_chunk
                        WHERE tenant_id = ?
                          AND document_id = ?
                        """,
                Long.class,
                tenantId,
                documentId
        );
    }

    public Optional<DocumentTask> tryMarkTaskRunning(long tenantId, long taskId) {
        int updated = jdbcTemplate.update("""
                        UPDATE document_task
                        SET status = ?,
                            attempt_count = attempt_count + 1,
                            started_at = NOW(),
                            finished_at = NULL,
                            error_message = NULL
                        WHERE tenant_id = ?
                          AND id = ?
                          AND status = ?
                        """,
                DocumentTaskStatus.running.name(),
                tenantId,
                taskId,
                DocumentTaskStatus.pending.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return getDocumentTask(tenantId, taskId);
    }

    public DocumentTask markTaskSuccess(long tenantId, long taskId, String outputSummary) {
        jdbcTemplate.update("""
                        UPDATE document_task
                        SET status = ?,
                            output_summary = ?,
                            error_message = NULL,
                            finished_at = NOW()
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                DocumentTaskStatus.success.name(),
                outputSummary,
                tenantId,
                taskId
        );
        return getDocumentTask(tenantId, taskId).orElseThrow();
    }

    public DocumentTask markTaskFailed(long tenantId, long taskId, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE document_task
                        SET status = ?,
                            error_message = ?,
                            finished_at = NOW()
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                DocumentTaskStatus.failed.name(),
                errorMessage,
                tenantId,
                taskId
        );
        return getDocumentTask(tenantId, taskId).orElseThrow();
    }

    public KnowledgeDocument updateDocumentStatus(
            long tenantId,
            long documentId,
            KnowledgeDocumentStatus status,
            Integer chunkCount,
            String errorMessage
    ) {
        return updateDocumentStatus(tenantId, documentId, status, chunkCount, errorMessage, null);
    }

    public KnowledgeDocument updateDocumentStatus(
            long tenantId,
            long documentId,
            KnowledgeDocumentStatus status,
            Integer chunkCount,
            String errorMessage,
            String parsedText
    ) {
        return updateDocumentStatus(tenantId, documentId, status, chunkCount, errorMessage, parsedText, null, null, null);
    }

    public KnowledgeDocument updateDocumentStatus(
            long tenantId,
            long documentId,
            KnowledgeDocumentStatus status,
            Integer chunkCount,
            String errorMessage,
            String parsedText,
            Integer activeVersionNo,
            String graphSyncStatus,
            String graphErrorMessage
    ) {
        KnowledgeDocument existing = getKnowledgeDocument(tenantId, documentId).orElseThrow();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("category", existing.category() == null ? "" : existing.category());
        metadata.put("tags", existing.tags() == null ? List.of() : existing.tags());
        if (parsedText != null) {
            metadata.put("parsedText", parsedText);
        } else if (existing.parsedText() != null && !existing.parsedText().isBlank()) {
            metadata.put("parsedText", existing.parsedText());
        }
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE knowledge_document
                    SET status = ?,
                        chunk_count = COALESCE(?, chunk_count),
                        error_message = ?,
                        active_version_no = COALESCE(?, active_version_no),
                        graph_sync_status = COALESCE(?, graph_sync_status),
                        graph_error_message = ?,
                        metadata = ?::jsonb,
                        updated_at = NOW()
                    WHERE tenant_id = ?
                      AND id = ?
                      AND deleted_at IS NULL
                    """);
            statement.setString(1, status.name());
            if (chunkCount == null) {
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(2, chunkCount);
            }
            statement.setString(3, errorMessage);
            if (activeVersionNo == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, activeVersionNo);
            }
            statement.setString(5, graphSyncStatus);
            statement.setString(6, graphErrorMessage);
            statement.setString(7, writeMetadata(metadata));
            statement.setLong(8, tenantId);
            statement.setLong(9, documentId);
            return statement;
        });
        return getKnowledgeDocument(tenantId, documentId).orElseThrow();
    }

    public int deleteDocumentChunks(long tenantId, long documentId) {
        jdbcTemplate.update("""
                        DELETE FROM document_embedding
                        WHERE tenant_id = ?
                          AND document_id = ?
                        """,
                tenantId,
                documentId
        );
        return jdbcTemplate.update("""
                        DELETE FROM document_chunk
                        WHERE tenant_id = ?
                          AND document_id = ?
                        """,
                tenantId,
                documentId
        );
    }

    public void replaceDocumentChunks(long tenantId, long documentId, List<ChunkInsert> chunks) {
        deleteDocumentChunks(tenantId, documentId);
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO document_chunk (
                            tenant_id,
                            document_id,
                            parent_chunk_id,
                            chunk_no,
                            section_title,
                            content,
                            content_hash,
                            char_count,
                            token_count,
                            search_vector,
                            metadata
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('simple', ?), ?::jsonb)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int index) throws SQLException {
                        ChunkInsert chunk = chunks.get(index);
                        ps.setLong(1, tenantId);
                        ps.setLong(2, documentId);
                        if (chunk.parentChunkId() == null) {
                            ps.setNull(3, Types.BIGINT);
                        } else {
                            ps.setLong(3, chunk.parentChunkId());
                        }
                        ps.setInt(4, chunk.chunkNo());
                        ps.setString(5, chunk.sectionTitle());
                        ps.setString(6, chunk.content());
                        ps.setString(7, chunk.contentHash());
                        ps.setInt(8, chunk.charCount());
                        if (chunk.tokenCount() == null) {
                            ps.setNull(9, Types.INTEGER);
                        } else {
                            ps.setInt(9, chunk.tokenCount());
                        }
                        ps.setString(10, chunk.content());
                        ps.setString(11, writeMetadata(chunk.metadata()));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
    }

    public void replaceDocumentEmbeddings(
            long tenantId,
            long documentId,
            String provider,
            String model,
            int dimension,
            List<EmbeddingInsert> embeddings
    ) {
        jdbcTemplate.update("""
                        DELETE FROM document_embedding
                        WHERE tenant_id = ?
                          AND document_id = ?
                          AND provider = ?
                          AND model = ?
                        """,
                tenantId,
                documentId,
                provider,
                model
        );
        if (embeddings.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO document_embedding (
                            tenant_id,
                            document_id,
                            chunk_id,
                            provider,
                            model,
                            dimension,
                            embedding
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?::vector)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int index) throws SQLException {
                        EmbeddingInsert embedding = embeddings.get(index);
                        ps.setLong(1, tenantId);
                        ps.setLong(2, documentId);
                        ps.setLong(3, embedding.chunkId());
                        ps.setString(4, provider);
                        ps.setString(5, model);
                        ps.setInt(6, dimension);
                        ps.setString(7, toVectorLiteral(embedding.vector()));
                    }

                    @Override
                    public int getBatchSize() {
                        return embeddings.size();
                    }
                });
    }

    public List<DocumentChunk> listAllDocumentChunks(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               document_id,
                               parent_chunk_id,
                               chunk_no,
                               section_title,
                               content,
                               content_hash,
                               char_count,
                               token_count,
                               metadata,
                               created_at,
                               updated_at
                        FROM document_chunk
                        WHERE tenant_id = ?
                          AND document_id = ?
                        ORDER BY chunk_no ASC
                        """,
                documentChunkRowMapper,
                tenantId,
                documentId
        );
    }

    public List<RetrievalResult> findTopKByVector(
            long tenantId,
            Long knowledgeBaseId,
            List<Double> vector,
            int topK,
            boolean versionConsistencyEnabled
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    kd.knowledge_base_id,
                    kd.id AS document_id,
                    dc.id AS chunk_id,
                    kd.title AS document_title,
                    kd.active_version_no,
                    dc.chunk_no,
                    dc.content,
                    dc.section_title,
                    dc.metadata,
                    1 - (de.embedding <=> ?::vector) AS score
                FROM document_embedding de
                JOIN knowledge_document kd ON kd.id = de.document_id
                JOIN document_chunk dc ON dc.id = de.chunk_id
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                WHERE de.tenant_id = ?
                  AND kd.tenant_id = ?
                  AND dc.tenant_id = ?
                  AND kd.status = 'ready'
                  AND kd.deleted_at IS NULL
                  AND kb.deleted_at IS NULL
                  AND kb.status = 'published'
                """);
        ArrayList<Object> args = new ArrayList<>();
        String vectorLiteral = toVectorLiteral(vector);
        args.add(vectorLiteral);
        args.add(tenantId);
        args.add(tenantId);
        args.add(tenantId);
        if (knowledgeBaseId != null) {
            sql.append(" AND kd.knowledge_base_id = ?");
            args.add(knowledgeBaseId);
        }
        if (versionConsistencyEnabled) {
            sql.append("""
                     AND COALESCE(NULLIF(dc.metadata ->> 'versionNo', '')::int, kd.active_version_no) = kd.active_version_no
                    """);
        }
        sql.append(" ORDER BY de.embedding <=> ?::vector ASC LIMIT ?");
        args.add(vectorLiteral);
        args.add(topK);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RetrievalResult(
                        "vector",
                        rs.getLong("knowledge_base_id"),
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("document_title"),
                        rs.getInt("chunk_no"),
                        rs.getString("content"),
                        rs.getString("section_title"),
                        rs.getDouble("score"),
                        enrichRetrievalMetadata(readMetadata(rs.getString("metadata")), rs.getInt("active_version_no"))
                ),
                args.toArray()
        );
    }

    public List<RetrievalResult> findTopKByKeyword(
            long tenantId,
            Long knowledgeBaseId,
            String query,
            int topK,
            boolean versionConsistencyEnabled
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    kd.knowledge_base_id,
                    kd.id AS document_id,
                    dc.id AS chunk_id,
                    kd.title AS document_title,
                    kd.active_version_no,
                    dc.chunk_no,
                    dc.content,
                    dc.section_title,
                    dc.metadata,
                    ts_rank_cd(dc.search_vector, websearch_to_tsquery('simple', ?)) AS score
                FROM document_chunk dc
                JOIN knowledge_document kd ON kd.id = dc.document_id
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                WHERE dc.tenant_id = ?
                  AND kd.tenant_id = ?
                  AND kd.status = 'ready'
                  AND kd.deleted_at IS NULL
                  AND kb.deleted_at IS NULL
                  AND kb.status = 'published'
                  AND dc.search_vector @@ websearch_to_tsquery('simple', ?)
                """);
        ArrayList<Object> args = new ArrayList<>();
        args.add(query);
        args.add(tenantId);
        args.add(tenantId);
        args.add(query);
        if (knowledgeBaseId != null) {
            sql.append(" AND kd.knowledge_base_id = ?");
            args.add(knowledgeBaseId);
        }
        if (versionConsistencyEnabled) {
            sql.append("""
                     AND COALESCE(NULLIF(dc.metadata ->> 'versionNo', '')::int, kd.active_version_no) = kd.active_version_no
                    """);
        }
        sql.append(" ORDER BY score DESC, dc.chunk_no ASC LIMIT ?");
        args.add(topK);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RetrievalResult(
                        "keyword",
                        rs.getLong("knowledge_base_id"),
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("document_title"),
                        rs.getInt("chunk_no"),
                        rs.getString("content"),
                        rs.getString("section_title"),
                        rs.getDouble("score"),
                        enrichRetrievalMetadata(readMetadata(rs.getString("metadata")), rs.getInt("active_version_no"))
                ),
                args.toArray()
        );
    }

    public List<RetrievalResult> findTopKByKeywordFallback(
            long tenantId,
            Long knowledgeBaseId,
            List<String> terms,
            int topK,
            boolean versionConsistencyEnabled
    ) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT
                    kd.knowledge_base_id,
                    kd.id AS document_id,
                    dc.id AS chunk_id,
                    kd.title AS document_title,
                    kd.active_version_no,
                    dc.chunk_no,
                    dc.content,
                    dc.section_title,
                    dc.metadata,
                    (
                """);
        ArrayList<Object> args = new ArrayList<>();
        for (int index = 0; index < terms.size(); index++) {
            if (index > 0) {
                sql.append(" + ");
            }
            sql.append("CASE WHEN dc.content ILIKE ? THEN 1 ELSE 0 END");
            args.add("%" + terms.get(index) + "%");
        }
        sql.append("""
                    )::double precision AS score
                FROM document_chunk dc
                JOIN knowledge_document kd ON kd.id = dc.document_id
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                WHERE dc.tenant_id = ?
                  AND kd.tenant_id = ?
                  AND kd.status = 'ready'
                  AND kd.deleted_at IS NULL
                  AND kb.deleted_at IS NULL
                  AND kb.status = 'published'
                  AND (
                """);
        args.add(tenantId);
        args.add(tenantId);
        for (int index = 0; index < terms.size(); index++) {
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append("dc.content ILIKE ?");
            args.add("%" + terms.get(index) + "%");
        }
        sql.append(")");
        if (knowledgeBaseId != null) {
            sql.append(" AND kd.knowledge_base_id = ?");
            args.add(knowledgeBaseId);
        }
        if (versionConsistencyEnabled) {
            sql.append("""
                     AND COALESCE(NULLIF(dc.metadata ->> 'versionNo', '')::int, kd.active_version_no) = kd.active_version_no
                    """);
        }
        sql.append(" ORDER BY score DESC, dc.chunk_no ASC LIMIT ?");
        args.add(topK);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RetrievalResult(
                        "keyword",
                        rs.getLong("knowledge_base_id"),
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("document_title"),
                        rs.getInt("chunk_no"),
                        rs.getString("content"),
                        rs.getString("section_title"),
                        rs.getDouble("score"),
                        enrichRetrievalMetadata(readMetadata(rs.getString("metadata")), rs.getInt("active_version_no"))
                ),
                args.toArray()
        );
    }

    public List<RetrievalResult> findNeighborChunks(
            long tenantId,
            long documentId,
            List<Integer> anchorChunkNos,
            int neighborWindow,
            boolean versionConsistencyEnabled
    ) {
        if (anchorChunkNos == null || anchorChunkNos.isEmpty() || neighborWindow <= 0) {
            return List.of();
        }
        int minChunkNo = anchorChunkNos.stream().mapToInt(Integer::intValue).min().orElse(1) - neighborWindow;
        int maxChunkNo = anchorChunkNos.stream().mapToInt(Integer::intValue).max().orElse(1) + neighborWindow;
        List<RetrievalResult> candidates = jdbcTemplate.query("""
                        SELECT
                            kd.knowledge_base_id,
                            kd.id AS document_id,
                            dc.id AS chunk_id,
                            kd.title AS document_title,
                            kd.active_version_no,
                            dc.chunk_no,
                            dc.content,
                            dc.section_title,
                            dc.metadata,
                            0.0::double precision AS score
                        FROM document_chunk dc
                        JOIN knowledge_document kd ON kd.id = dc.document_id
                        JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                        WHERE dc.tenant_id = ?
                          AND kd.tenant_id = ?
                          AND dc.document_id = ?
                          AND kd.status = 'ready'
                          AND kd.deleted_at IS NULL
                          AND kb.deleted_at IS NULL
                          AND kb.status = 'published'
                          AND dc.chunk_no BETWEEN ? AND ?
                        ORDER BY dc.chunk_no ASC
                        """,
                (rs, rowNum) -> new RetrievalResult(
                        "neighbor",
                        rs.getLong("knowledge_base_id"),
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("document_title"),
                        rs.getInt("chunk_no"),
                        rs.getString("content"),
                        rs.getString("section_title"),
                        rs.getDouble("score"),
                        enrichRetrievalMetadata(readMetadata(rs.getString("metadata")), rs.getInt("active_version_no"))
                ),
                tenantId,
                tenantId,
                documentId,
                Math.max(1, minChunkNo),
                maxChunkNo
        );
        return candidates.stream()
                .filter(result -> anchorChunkNos.stream().anyMatch(anchor -> Math.abs(anchor - result.chunkNo()) <= neighborWindow))
                .filter(result -> !anchorChunkNos.contains(result.chunkNo()))
                .filter(result -> !versionConsistencyEnabled || resolveChunkVersionNo(result.metadata(), resolveActiveVersionNo(result.metadata())) == resolveActiveVersionNo(result.metadata()))
                .toList();
    }

    public record ChunkInsert(
            Long parentChunkId,
            int chunkNo,
            String sectionTitle,
            String content,
            String contentHash,
            int charCount,
            Integer tokenCount,
            Map<String, Object> metadata
    ) {
    }

    public record EmbeddingInsert(long chunkId, List<Double> vector) {
    }

    private KnowledgeBase mapKnowledgeBase(ResultSet rs) throws SQLException {
        return new KnowledgeBase(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                KnowledgeBaseVisibility.valueOf(rs.getString("visibility")),
                KnowledgeBaseStatus.valueOf(rs.getString("status")),
                rs.getLong("owner_id"),
                rs.getInt("document_count"),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private KnowledgeDomain mapKnowledgeDomain(ResultSet rs) throws SQLException {
        return new KnowledgeDomain(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                readMetadata(rs.getString("metadata")),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private ChunkingProfile mapChunkingProfile(ResultSet rs) throws SQLException {
        return new ChunkingProfile(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("strategy"),
                readMetadata(rs.getString("config_json")),
                rs.getBoolean("is_default"),
                rs.getString("status"),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private KnowledgeDocument mapKnowledgeDocument(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = readMetadata(rs.getString("metadata"));
        return new KnowledgeDocument(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("knowledge_base_id"),
                getNullableLong(rs, "knowledge_domain_id"),
                getNullableLong(rs, "chunking_profile_id"),
                rs.getString("graph_sync_status"),
                rs.getString("graph_error_message"),
                rs.getInt("active_version_no"),
                rs.getString("title"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("object_key"),
                rs.getString("content_hash"),
                KnowledgeDocumentStatus.valueOf(rs.getString("status")),
                rs.getInt("chunk_count"),
                rs.getString("error_message"),
                metadata.get("parsedText") instanceof String parsedText && !parsedText.isBlank() ? parsedText : null,
                metadata.get("category") instanceof String category && !category.isBlank() ? category : null,
                metadata.get("tags") instanceof List<?> tags ? tags.stream().map(String::valueOf).toList() : List.of(),
                rs.getLong("created_by"),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private KnowledgeDocumentVersion mapDocumentVersion(ResultSet rs) throws SQLException {
        return new KnowledgeDocumentVersion(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("document_id"),
                rs.getInt("version_no"),
                getNullableLong(rs, "chunking_profile_id"),
                rs.getString("status"),
                rs.getInt("chunk_count"),
                rs.getString("graph_sync_status"),
                readMetadata(rs.getString("metadata")),
                getNullableLong(rs, "created_by"),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private DocumentTask mapDocumentTask(ResultSet rs) throws SQLException {
        return new DocumentTask(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("document_id"),
                DocumentTaskType.valueOf(rs.getString("task_type")),
                DocumentTaskStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getString("input_summary"),
                rs.getString("output_summary"),
                rs.getString("error_message"),
                readOffsetDateTime(rs, "started_at"),
                readOffsetDateTime(rs, "finished_at"),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private DocumentChunk mapDocumentChunk(ResultSet rs) throws SQLException {
        return new DocumentChunk(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("document_id"),
                rs.getObject("parent_chunk_id", Long.class),
                rs.getInt("chunk_no"),
                rs.getString("section_title"),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("char_count"),
                rs.getObject("token_count", Integer.class),
                readMetadata(rs.getString("metadata")),
                readOffsetDateTime(rs, "created_at"),
                readOffsetDateTime(rs, "updated_at")
        );
    }

    private Map<String, Object> enrichRetrievalMetadata(Map<String, Object> metadata, int activeVersionNo) {
        Map<String, Object> enriched = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        enriched.put("activeVersionNo", activeVersionNo);
        enriched.putIfAbsent("chunkVersionNo", resolveChunkVersionNo(enriched, activeVersionNo));
        return enriched;
    }

    private int resolveActiveVersionNo(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("activeVersionNo");
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private int resolveChunkVersionNo(Map<String, Object> metadata, int fallbackVersionNo) {
        if (metadata == null) {
            return fallbackVersionNo;
        }
        Object versionNo = metadata.get("versionNo");
        if (versionNo == null) {
            return fallbackVersionNo;
        }
        return versionNo instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(versionNo));
    }

    private OffsetDateTime readOffsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize metadata", exception);
        }
    }

    private Map<String, Object> readMetadata(String rawMetadata) {
        try {
            return objectMapper.readValue(rawMetadata, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize metadata", exception);
        }
    }

    private String toVectorLiteral(List<Double> vector) {
        return "[" + vector.stream()
                .map(value -> java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString())
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private static class DocumentFilterAppender {

        private final StringBuilder sql;

        private DocumentFilterAppender(StringBuilder sql) {
            this.sql = sql;
        }

        private void append(String status, String fileType, String keyword, String tag) {
            if (status != null && !status.isBlank()) {
                sql.append(" AND status = ?");
            }
            if (fileType != null && !fileType.isBlank()) {
                sql.append(" AND file_type = ?");
            }
            if (keyword != null && !keyword.isBlank()) {
                sql.append(" AND title ILIKE ?");
            }
            if (tag != null && !tag.isBlank()) {
                sql.append(" AND metadata -> 'tags' ? ?");
            }
        }
    }

    private record DocumentFilterArgs(long tenantId, long knowledgeBaseId, String keyword, String tag) {
        private Object[] toArgs(String status, String fileType, boolean paged, Object... pagination) {
            ArrayList<Object> args = new ArrayList<>();
            args.add(tenantId);
            args.add(knowledgeBaseId);
            if (status != null && !status.isBlank()) {
                args.add(status);
            }
            if (fileType != null && !fileType.isBlank()) {
                args.add(fileType);
            }
            if (keyword != null && !keyword.isBlank()) {
                args.add("%" + keyword.trim() + "%");
            }
            if (tag != null && !tag.isBlank()) {
                args.add(tag.trim());
            }
            if (paged) {
                args.addAll(List.of(pagination));
            }
            return args.toArray();
        }
    }
}
