package com.superagent.settings.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(
            Long tenantId,
            Long actorId,
            String action,
            String resourceType,
            Long resourceId,
            Map<String, Object> detail
    ) {
        jdbcTemplate.update("""
                        INSERT INTO audit_log (
                            tenant_id,
                            actor_id,
                            action,
                            resource_type,
                            resource_id,
                            detail
                        ) VALUES (
                            :tenantId,
                            :actorId,
                            :action,
                            :resourceType,
                            :resourceId,
                            CAST(:detail AS jsonb)
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("actorId", actorId)
                        .addValue("action", action)
                        .addValue("resourceType", resourceType)
                        .addValue("resourceId", resourceId)
                        .addValue("detail", writeJson(detail))
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize audit detail", exception);
        }
    }
}
