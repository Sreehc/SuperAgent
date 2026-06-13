package com.superagent.feedback.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.feedback.domain.ConversationFeedback;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationFeedbackRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConversationFeedbackRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<MessageFeedbackTarget> findFeedbackTarget(long tenantId, long messageId) {
        return jdbcTemplate.query("""
                        SELECT
                            cm.id AS message_id,
                            cm.session_id,
                            cs.owner_id,
                            ce.id AS exchange_id
                        FROM conversation_message cm
                        JOIN conversation_session cs
                          ON cs.id = cm.session_id
                         AND cs.tenant_id = cm.tenant_id
                        LEFT JOIN conversation_exchange ce
                          ON ce.assistant_message_id = cm.id
                         AND ce.tenant_id = cm.tenant_id
                        WHERE cm.tenant_id = :tenantId
                          AND cm.id = :messageId
                          AND cm.role = 'assistant'
                          AND cs.deleted_at IS NULL
                          AND cs.status <> 'deleted'
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("messageId", messageId),
                (rs, rowNum) -> new MessageFeedbackTarget(
                        rs.getLong("message_id"),
                        rs.getLong("session_id"),
                        rs.getLong("owner_id"),
                        getNullableLong(rs, "exchange_id")
                )
        ).stream().findFirst();
    }

    public ConversationFeedback upsert(
            long tenantId,
            MessageFeedbackTarget target,
            long actorUserId,
            String rating,
            String comment,
            String correction
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_feedback (
                            tenant_id, session_id, exchange_id, message_id, actor_user_id, rating, comment, correction
                        ) VALUES (
                            :tenantId, :sessionId, :exchangeId, :messageId, :actorUserId, :rating, :comment, :correction
                        )
                        ON CONFLICT (tenant_id, message_id, actor_user_id)
                        DO UPDATE SET rating = EXCLUDED.rating,
                                      comment = EXCLUDED.comment,
                                      correction = EXCLUDED.correction,
                                      updated_at = NOW()
                        RETURNING id, tenant_id, session_id, exchange_id, message_id, actor_user_id, rating, comment, correction,
                                  metadata, created_at, updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sessionId", target.sessionId())
                        .addValue("exchangeId", target.exchangeId())
                        .addValue("messageId", target.messageId())
                        .addValue("actorUserId", actorUserId)
                        .addValue("rating", rating)
                        .addValue("comment", comment)
                        .addValue("correction", correction),
                (rs, rowNum) -> toFeedback(rs)
        );
    }

    public boolean delete(long tenantId, long messageId, long actorUserId) {
        return jdbcTemplate.update("""
                        DELETE FROM conversation_feedback
                        WHERE tenant_id = :tenantId
                          AND message_id = :messageId
                          AND actor_user_id = :actorUserId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("messageId", messageId)
                        .addValue("actorUserId", actorUserId)
        ) > 0;
    }

    public List<ConversationFeedback> listForSession(long tenantId, long sessionId, long actorUserId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, session_id, exchange_id, message_id, actor_user_id, rating, comment, correction,
                               metadata, created_at, updated_at
                        FROM conversation_feedback
                        WHERE tenant_id = :tenantId
                          AND session_id = :sessionId
                          AND actor_user_id = :actorUserId
                        ORDER BY created_at ASC, id ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sessionId", sessionId)
                        .addValue("actorUserId", actorUserId),
                (rs, rowNum) -> toFeedback(rs)
        );
    }

    public long countAdmin(long tenantId, String rating) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM conversation_feedback WHERE tenant_id = :tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (rating != null && !rating.isBlank()) {
            sql.append(" AND rating = :rating");
            params.addValue("rating", rating.trim());
        }
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<ConversationFeedback> listAdmin(long tenantId, String rating, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, session_id, exchange_id, message_id, actor_user_id, rating, comment, correction,
                       metadata, created_at, updated_at
                FROM conversation_feedback
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        if (rating != null && !rating.isBlank()) {
            sql.append(" AND rating = :rating");
            params.addValue("rating", rating.trim());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> toFeedback(rs));
    }

    private ConversationFeedback toFeedback(ResultSet rs) throws java.sql.SQLException {
        return new ConversationFeedback(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("session_id"),
                getNullableLong(rs, "exchange_id"),
                rs.getLong("message_id"),
                rs.getLong("actor_user_id"),
                rs.getString("rating"),
                rs.getString("comment"),
                rs.getString("correction"),
                parseMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record MessageFeedbackTarget(long messageId, long sessionId, long ownerId, Long exchangeId) {
    }
}
