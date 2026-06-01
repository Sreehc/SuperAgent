package com.superagent.knowledge.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.DocumentTask;
import com.superagent.knowledge.domain.DocumentTaskStatus;
import com.superagent.knowledge.domain.DocumentTaskType;
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.domain.KnowledgeBaseVisibility;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    RETURNING id
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setLong(2, knowledgeBaseId);
            statement.setString(3, title);
            statement.setString(4, fileName);
            statement.setString(5, fileType);
            statement.setLong(6, fileSize);
            statement.setString(7, objectKey);
            statement.setString(8, contentHash);
            statement.setString(9, status.name());
            statement.setString(10, metadataJson);
            statement.setLong(11, createdBy);
            return statement;
        }, keyHolder);
        return getKnowledgeDocument(tenantId, keyHolder.getKey().longValue()).orElseThrow();
    }

    public Optional<KnowledgeDocument> getKnowledgeDocument(long tenantId, long documentId) {
        return jdbcTemplate.query("""
                        SELECT id,
                               tenant_id,
                               knowledge_base_id,
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

    public DocumentTask markTaskRunning(long tenantId, long taskId) {
        jdbcTemplate.update("""
                        UPDATE document_task
                        SET status = ?,
                            attempt_count = attempt_count + 1,
                            started_at = NOW(),
                            finished_at = NULL,
                            error_message = NULL
                        WHERE tenant_id = ?
                          AND id = ?
                        """,
                DocumentTaskStatus.running.name(),
                tenantId,
                taskId
        );
        return getDocumentTask(tenantId, taskId).orElseThrow();
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
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE knowledge_document
                    SET status = ?,
                        chunk_count = COALESCE(?, chunk_count),
                        error_message = ?,
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
            statement.setLong(4, tenantId);
            statement.setLong(5, documentId);
            return statement;
        });
        return getKnowledgeDocument(tenantId, documentId).orElseThrow();
    }

    public int deleteDocumentChunks(long tenantId, long documentId) {
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
                            metadata
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
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
                        ps.setString(10, writeMetadata(chunk.metadata()));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
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

    private KnowledgeDocument mapKnowledgeDocument(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = readMetadata(rs.getString("metadata"));
        return new KnowledgeDocument(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("knowledge_base_id"),
                rs.getString("title"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("object_key"),
                rs.getString("content_hash"),
                KnowledgeDocumentStatus.valueOf(rs.getString("status")),
                rs.getInt("chunk_count"),
                rs.getString("error_message"),
                metadata.get("category") instanceof String category && !category.isBlank() ? category : null,
                metadata.get("tags") instanceof List<?> tags ? tags.stream().map(String::valueOf).toList() : List.of(),
                rs.getLong("created_by"),
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

    private OffsetDateTime readOffsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
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
