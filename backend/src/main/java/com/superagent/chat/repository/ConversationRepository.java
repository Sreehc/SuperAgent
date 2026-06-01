package com.superagent.chat.repository;

import com.superagent.chat.domain.ConversationExchange;
import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.ConversationReference;
import com.superagent.chat.domain.ConversationSession;
import com.superagent.chat.domain.ConversationStatus;
import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.domain.ExchangeTraceStage;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.domain.MessageRole;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private static final RowMapper<ConversationSession> SESSION_ROW_MAPPER = (rs, rowNum) -> new ConversationSession(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("owner_id"),
            rs.getString("title"),
            MemoryStrategy.valueOf(rs.getString("memory_strategy")),
            getNullableLong(rs, "knowledge_base_id"),
            ConversationStatus.valueOf(rs.getString("status")),
            rs.getObject("last_message_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private static final RowMapper<ConversationMessage> MESSAGE_ROW_MAPPER = (rs, rowNum) -> new ConversationMessage(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("session_id"),
            MessageRole.valueOf(rs.getString("role")),
            rs.getString("content"),
            rs.getString("status"),
            (Integer) rs.getObject("token_count"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private static final RowMapper<ConversationExchange> EXCHANGE_ROW_MAPPER = (rs, rowNum) -> new ConversationExchange(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("session_id"),
            rs.getLong("user_message_id"),
            getNullableLong(rs, "assistant_message_id"),
            ExecutionMode.valueOf(rs.getString("execution_mode")),
            rs.getString("status"),
            rs.getString("route_reason"),
            rs.getBigDecimal("route_confidence"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private static final RowMapper<ConversationReference> REFERENCE_ROW_MAPPER = (rs, rowNum) -> new ConversationReference(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("exchange_id"),
            rs.getLong("document_id"),
            rs.getLong("chunk_id"),
            rs.getInt("ordinal"),
            rs.getString("title"),
            rs.getString("quote"),
            rs.getBigDecimal("score"),
            rs.getString("source_uri")
    );

    private static final RowMapper<ExchangeTraceStage> TRACE_STAGE_ROW_MAPPER = (rs, rowNum) -> new ExchangeTraceStage(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getLong("exchange_id"),
            rs.getString("stage_code"),
            rs.getString("status"),
            rs.getString("input_summary"),
            rs.getString("output_summary"),
            rs.getString("error_message"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConversationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationSession createSession(long tenantId, long ownerId, String title, MemoryStrategy memoryStrategy, Long knowledgeBaseId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_session (
                            tenant_id,
                            owner_id,
                            title,
                            memory_strategy,
                            knowledge_base_id
                        ) VALUES (
                            :tenantId,
                            :ownerId,
                            :title,
                            :memoryStrategy,
                            :knowledgeBaseId
                        )
                        RETURNING id, tenant_id, owner_id, title, memory_strategy, knowledge_base_id, status,
                                  last_message_at, created_at, updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ownerId", ownerId)
                        .addValue("title", title)
                        .addValue("memoryStrategy", memoryStrategy.name())
                        .addValue("knowledgeBaseId", knowledgeBaseId),
                SESSION_ROW_MAPPER
        );
    }

    public long countVisibleSessions(long tenantId, long userId, boolean tenantAdmin, String status, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM conversation_session cs
                WHERE cs.tenant_id = :tenantId
                  AND cs.deleted_at IS NULL
                  AND cs.status <> 'deleted'
                """);
        MapSqlParameterSource params = buildSessionFilters(sql, tenantId, userId, tenantAdmin, status, keyword);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<ConversationSession> findVisibleSessions(long tenantId, long userId, boolean tenantAdmin, String status, String keyword, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, owner_id, title, memory_strategy, knowledge_base_id, status,
                       last_message_at, created_at, updated_at
                FROM conversation_session cs
                WHERE cs.tenant_id = :tenantId
                  AND cs.deleted_at IS NULL
                  AND cs.status <> 'deleted'
                """);
        MapSqlParameterSource params = buildSessionFilters(sql, tenantId, userId, tenantAdmin, status, keyword)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        sql.append("""
                ORDER BY COALESCE(cs.last_message_at, cs.created_at) DESC, cs.id DESC
                LIMIT :limit OFFSET :offset
                """);
        return jdbcTemplate.query(sql.toString(), params, SESSION_ROW_MAPPER);
    }

    public Optional<ConversationSession> findAccessibleSession(long sessionId, long tenantId, long userId, boolean tenantAdmin) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, owner_id, title, memory_strategy, knowledge_base_id, status,
                       last_message_at, created_at, updated_at
                FROM conversation_session cs
                WHERE cs.id = :sessionId
                  AND cs.tenant_id = :tenantId
                  AND cs.deleted_at IS NULL
                  AND cs.status <> 'deleted'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("tenantId", tenantId)
                .addValue("userId", userId);
        if (!tenantAdmin) {
            sql.append(" AND cs.owner_id = :userId");
        }
        return jdbcTemplate.query(sql.toString(), params, SESSION_ROW_MAPPER).stream().findFirst();
    }

    public ConversationSession updateSession(long sessionId, long tenantId, String title, MemoryStrategy memoryStrategy, Long knowledgeBaseId, ConversationStatus status) {
        return jdbcTemplate.queryForObject("""
                        UPDATE conversation_session
                        SET title = :title,
                            memory_strategy = :memoryStrategy,
                            knowledge_base_id = :knowledgeBaseId,
                            status = :status
                        WHERE id = :sessionId
                          AND tenant_id = :tenantId
                        RETURNING id, tenant_id, owner_id, title, memory_strategy, knowledge_base_id, status,
                                  last_message_at, created_at, updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("tenantId", tenantId)
                        .addValue("title", title)
                        .addValue("memoryStrategy", memoryStrategy.name())
                        .addValue("knowledgeBaseId", knowledgeBaseId)
                        .addValue("status", status.name()),
                SESSION_ROW_MAPPER
        );
    }

    public boolean softDeleteSession(long sessionId, long tenantId) {
        return jdbcTemplate.update("""
                        UPDATE conversation_session
                        SET status = 'deleted',
                            deleted_at = NOW()
                        WHERE id = :sessionId
                          AND tenant_id = :tenantId
                          AND deleted_at IS NULL
                        """,
                Map.of("sessionId", sessionId, "tenantId", tenantId)
        ) > 0;
    }

    public long countMessages(long sessionId, long tenantId) {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM conversation_message
                        WHERE session_id = :sessionId
                          AND tenant_id = :tenantId
                        """,
                Map.of("sessionId", sessionId, "tenantId", tenantId),
                Long.class
        );
    }

    public List<ConversationMessage> findMessages(long sessionId, long tenantId, int page, int pageSize) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, session_id, role, content, status, token_count, created_at, updated_at
                        FROM conversation_message
                        WHERE session_id = :sessionId
                          AND tenant_id = :tenantId
                        ORDER BY created_at ASC, id ASC
                        LIMIT :limit OFFSET :offset
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("tenantId", tenantId)
                        .addValue("limit", pageSize)
                        .addValue("offset", Math.max(page - 1, 0) * pageSize),
                MESSAGE_ROW_MAPPER
        );
    }

    public ConversationMessage createMessage(long tenantId, long sessionId, MessageRole role, String content, String status, Integer tokenCount) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_message (
                            tenant_id,
                            session_id,
                            role,
                            content,
                            status,
                            token_count
                        ) VALUES (
                            :tenantId,
                            :sessionId,
                            :role,
                            :content,
                            :status,
                            :tokenCount
                        )
                        RETURNING id, tenant_id, session_id, role, content, status, token_count, created_at, updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sessionId", sessionId)
                        .addValue("role", role.name())
                        .addValue("content", content)
                        .addValue("status", status)
                        .addValue("tokenCount", tokenCount),
                MESSAGE_ROW_MAPPER
        );
    }

    public ConversationExchange createExchange(
            long tenantId,
            long sessionId,
            long userMessageId,
            ExecutionMode executionMode,
            String status,
            String routeReason,
            BigDecimal routeConfidence
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_exchange (
                            tenant_id,
                            session_id,
                            user_message_id,
                            execution_mode,
                            status,
                            route_reason,
                            route_confidence
                        ) VALUES (
                            :tenantId,
                            :sessionId,
                            :userMessageId,
                            :executionMode,
                            :status,
                            :routeReason,
                            :routeConfidence
                        )
                        RETURNING id, tenant_id, session_id, user_message_id, assistant_message_id, execution_mode,
                                  status, route_reason, route_confidence, started_at, finished_at, created_at, updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sessionId", sessionId)
                        .addValue("userMessageId", userMessageId)
                        .addValue("executionMode", executionMode.name())
                        .addValue("status", status)
                        .addValue("routeReason", routeReason)
                        .addValue("routeConfidence", routeConfidence),
                EXCHANGE_ROW_MAPPER
        );
    }

    public void completeExchange(long exchangeId, long tenantId, Long assistantMessageId, String status) {
        jdbcTemplate.update("""
                        UPDATE conversation_exchange
                        SET assistant_message_id = :assistantMessageId,
                            status = :status,
                            finished_at = NOW()
                        WHERE id = :exchangeId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("exchangeId", exchangeId)
                        .addValue("tenantId", tenantId)
                        .addValue("assistantMessageId", assistantMessageId)
                        .addValue("status", status)
        );
    }

    public void touchSession(long sessionId, long tenantId, OffsetDateTime lastMessageAt) {
        jdbcTemplate.update("""
                        UPDATE conversation_session
                        SET last_message_at = :lastMessageAt
                        WHERE id = :sessionId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("tenantId", tenantId)
                        .addValue("lastMessageAt", lastMessageAt)
        );
    }

    public void updateSessionDefaults(long sessionId, long tenantId, MemoryStrategy memoryStrategy, Long knowledgeBaseId) {
        jdbcTemplate.update("""
                        UPDATE conversation_session
                        SET memory_strategy = :memoryStrategy,
                            knowledge_base_id = :knowledgeBaseId
                        WHERE id = :sessionId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("tenantId", tenantId)
                        .addValue("memoryStrategy", memoryStrategy.name())
                        .addValue("knowledgeBaseId", knowledgeBaseId)
        );
    }

    public ExchangeTraceStage createTraceStage(long tenantId, long exchangeId, String stageCode, String status, String inputSummary) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO exchange_trace_stage (
                            tenant_id,
                            exchange_id,
                            stage_code,
                            status,
                            input_summary,
                            started_at
                        ) VALUES (
                            :tenantId,
                            :exchangeId,
                            :stageCode,
                            :status,
                            :inputSummary,
                            NOW()
                        )
                        RETURNING id, tenant_id, exchange_id, stage_code, status, input_summary, output_summary,
                                  error_message, started_at, finished_at
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("exchangeId", exchangeId)
                        .addValue("stageCode", stageCode)
                        .addValue("status", status)
                        .addValue("inputSummary", inputSummary),
                TRACE_STAGE_ROW_MAPPER
        );
    }

    public void completeTraceStage(long stageId, long tenantId, String status, String outputSummary, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE exchange_trace_stage
                        SET status = :status,
                            output_summary = :outputSummary,
                            error_message = :errorMessage,
                            finished_at = NOW()
                        WHERE id = :stageId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("stageId", stageId)
                        .addValue("tenantId", tenantId)
                        .addValue("status", status)
                        .addValue("outputSummary", outputSummary)
                        .addValue("errorMessage", errorMessage)
        );
    }

    public ConversationReference createReference(
            long tenantId,
            long exchangeId,
            long documentId,
            long chunkId,
            int ordinal,
            String title,
            String quote,
            BigDecimal score,
            String sourceUri
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_reference (
                            tenant_id,
                            exchange_id,
                            document_id,
                            chunk_id,
                            ordinal,
                            title,
                            quote,
                            score,
                            source_uri
                        ) VALUES (
                            :tenantId,
                            :exchangeId,
                            :documentId,
                            :chunkId,
                            :ordinal,
                            :title,
                            :quote,
                            :score,
                            :sourceUri
                        )
                        RETURNING id, tenant_id, exchange_id, document_id, chunk_id, ordinal, title, quote, score, source_uri
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("exchangeId", exchangeId)
                        .addValue("documentId", documentId)
                        .addValue("chunkId", chunkId)
                        .addValue("ordinal", ordinal)
                        .addValue("title", title)
                        .addValue("quote", quote)
                        .addValue("score", score)
                        .addValue("sourceUri", sourceUri),
                REFERENCE_ROW_MAPPER
        );
    }

    public Optional<ReferenceCandidate> findReferenceCandidate(long tenantId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT
                            kd.id AS document_id,
                            dc.id AS chunk_id,
                            kd.title,
                            LEFT(dc.content, 220) AS quote
                        FROM knowledge_document kd
                        JOIN document_chunk dc ON dc.document_id = kd.id
                        WHERE kd.tenant_id = :tenantId
                          AND kd.knowledge_base_id = :knowledgeBaseId
                          AND kd.status = 'ready'
                          AND kd.deleted_at IS NULL
                        ORDER BY kd.updated_at DESC, dc.chunk_no ASC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("knowledgeBaseId", knowledgeBaseId),
                (rs, rowNum) -> new ReferenceCandidate(
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getString("title"),
                        rs.getString("quote")
                )
        ).stream().findFirst();
    }

    public List<ConversationReference> findReferencesByExchangeId(long exchangeId, long tenantId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, exchange_id, document_id, chunk_id, ordinal, title, quote, score, source_uri
                        FROM conversation_reference
                        WHERE exchange_id = :exchangeId
                          AND tenant_id = :tenantId
                        ORDER BY ordinal ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("exchangeId", exchangeId)
                        .addValue("tenantId", tenantId),
                REFERENCE_ROW_MAPPER
        );
    }

    private MapSqlParameterSource buildSessionFilters(
            StringBuilder sql,
            long tenantId,
            long userId,
            boolean tenantAdmin,
            String status,
            String keyword
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId);
        if (!tenantAdmin) {
            sql.append(" AND cs.owner_id = :userId");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND cs.status = :status");
            params.addValue("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND cs.title ILIKE :keyword");
            params.addValue("keyword", "%" + keyword.trim() + "%");
        }
        return params;
    }

    private static Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    public record ReferenceCandidate(
            long documentId,
            long chunkId,
            String title,
            String quote
    ) {
    }
}
