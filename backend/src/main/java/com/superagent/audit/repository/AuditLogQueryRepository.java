package com.superagent.audit.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.audit.domain.AuditLogItem;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogQueryRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogQueryRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long count(
            long tenantId,
            String action,
            String resourceType,
            Long resourceId,
            Long actorId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_log WHERE tenant_id = :tenantId");
        MapSqlParameterSource params = buildParams(tenantId, action, resourceType, resourceId, actorId, from, to);
        appendFilters(sql, action, resourceType, resourceId, actorId, from, to);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public java.util.List<AuditLogItem> list(
            long tenantId,
            String action,
            String resourceType,
            Long resourceId,
            Long actorId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int pageSize
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, actor_id, action, resource_type, resource_id, detail::text AS detail, created_at
                FROM audit_log
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildParams(tenantId, action, resourceType, resourceId, actorId, from, to)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        appendFilters(sql, action, resourceType, resourceId, actorId, from, to);
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> toItem(rs));
    }

    private MapSqlParameterSource buildParams(
            long tenantId,
            String action,
            String resourceType,
            Long resourceId,
            Long actorId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("action", blankToNull(action))
                .addValue("resourceType", blankToNull(resourceType))
                .addValue("resourceId", resourceId)
                .addValue("actorId", actorId)
                .addValue("from", from)
                .addValue("to", to);
    }

    private void appendFilters(
            StringBuilder sql,
            String action,
            String resourceType,
            Long resourceId,
            Long actorId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        if (blankToNull(action) != null) {
            sql.append(" AND action = :action");
        }
        if (blankToNull(resourceType) != null) {
            sql.append(" AND resource_type = :resourceType");
        }
        if (resourceId != null) {
            sql.append(" AND resource_id = :resourceId");
        }
        if (actorId != null) {
            sql.append(" AND actor_id = :actorId");
        }
        if (from != null) {
            sql.append(" AND created_at >= :from");
        }
        if (to != null) {
            sql.append(" AND created_at <= :to");
        }
    }

    private AuditLogItem toItem(ResultSet rs) throws java.sql.SQLException {
        return new AuditLogItem(
                rs.getLong("id"),
                getNullableLong(rs, "tenant_id"),
                getNullableLong(rs, "actor_id"),
                rs.getString("action"),
                rs.getString("resource_type"),
                getNullableLong(rs, "resource_id"),
                parseMap(rs.getString("detail")),
                rs.getObject("created_at", OffsetDateTime.class)
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
