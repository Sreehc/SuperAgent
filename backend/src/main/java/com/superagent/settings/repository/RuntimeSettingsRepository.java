package com.superagent.settings.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RuntimeSettingsRepository {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RuntimeSettingsRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> findSection(long tenantId, String settingKey) {
        return jdbcTemplate.query("""
                        SELECT config_json
                        FROM tenant_runtime_setting
                        WHERE tenant_id = :tenantId
                          AND setting_key = :settingKey
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("settingKey", settingKey),
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readMap(rs.getString("config_json")));
                }
        );
    }

    public void upsertSection(long tenantId, String settingKey, Map<String, Object> config) {
        jdbcTemplate.update("""
                        INSERT INTO tenant_runtime_setting (
                            tenant_id,
                            setting_key,
                            config_json
                        ) VALUES (
                            :tenantId,
                            :settingKey,
                            CAST(:configJson AS jsonb)
                        )
                        ON CONFLICT (tenant_id, setting_key)
                        DO UPDATE SET config_json = EXCLUDED.config_json,
                                      updated_at = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("settingKey", settingKey)
                        .addValue("configJson", writeJson(config))
        );
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize runtime settings", exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize runtime settings", exception);
        }
    }
}
