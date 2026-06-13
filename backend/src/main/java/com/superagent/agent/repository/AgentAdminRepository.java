package com.superagent.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.AdminAgentCheckpoint;
import com.superagent.agent.domain.AdminAgentRunStep;
import com.superagent.agent.domain.AdminAgentRunSummary;
import com.superagent.agent.domain.AdminPluginItem;
import com.superagent.agent.domain.AdminToolCallDetail;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentAdminRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentAdminRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long countRuns(long tenantId, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM agent_run WHERE tenant_id = :tenantId");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<AdminAgentRunSummary> listRuns(long tenantId, String status, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, session_id, exchange_id, status, memory_strategy, route_reason, model_step_count, tool_call_count,
                       latest_checkpoint_no, error_message, started_at, finished_at
                FROM agent_run
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new AdminAgentRunSummary(
                rs.getLong("id"),
                rs.getLong("session_id"),
                getNullableLong(rs, "exchange_id"),
                rs.getString("status"),
                rs.getString("memory_strategy"),
                rs.getString("route_reason"),
                rs.getInt("model_step_count"),
                rs.getInt("tool_call_count"),
                rs.getInt("latest_checkpoint_no"),
                rs.getString("error_message"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        ));
    }

    public Optional<AdminAgentRunSummary> findRun(long tenantId, long runId) {
        return jdbcTemplate.query("""
                        SELECT id, session_id, exchange_id, status, memory_strategy, route_reason, model_step_count, tool_call_count,
                               latest_checkpoint_no, error_message, started_at, finished_at
                        FROM agent_run
                        WHERE tenant_id = :tenantId
                          AND id = :runId
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("runId", runId),
                (rs, rowNum) -> new AdminAgentRunSummary(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        getNullableLong(rs, "exchange_id"),
                        rs.getString("status"),
                        rs.getString("memory_strategy"),
                        rs.getString("route_reason"),
                        rs.getInt("model_step_count"),
                        rs.getInt("tool_call_count"),
                        rs.getInt("latest_checkpoint_no"),
                        rs.getString("error_message"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class)
                )
        ).stream().findFirst();
    }

    public Optional<AdminAgentRunSummary> findRunByExchangeId(long tenantId, long exchangeId) {
        return jdbcTemplate.query("""
                        SELECT id, session_id, exchange_id, status, memory_strategy, route_reason, model_step_count, tool_call_count,
                               latest_checkpoint_no, error_message, started_at, finished_at
                        FROM agent_run
                        WHERE tenant_id = :tenantId
                          AND exchange_id = :exchangeId
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("exchangeId", exchangeId),
                (rs, rowNum) -> new AdminAgentRunSummary(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        getNullableLong(rs, "exchange_id"),
                        rs.getString("status"),
                        rs.getString("memory_strategy"),
                        rs.getString("route_reason"),
                        rs.getInt("model_step_count"),
                        rs.getInt("tool_call_count"),
                        rs.getInt("latest_checkpoint_no"),
                        rs.getString("error_message"),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class)
                )
        ).stream().findFirst();
    }

    public List<AdminAgentRunStep> listSteps(long tenantId, long runId) {
        return jdbcTemplate.query("""
                        SELECT id, step_no, phase, status, decision_summary, observation_summary, selected_tool_id,
                               selected_tool_reason, error_message, metadata, started_at, finished_at
                        FROM agent_run_step
                        WHERE tenant_id = :tenantId
                          AND agent_run_id = :runId
                        ORDER BY step_no ASC, id ASC
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("runId", runId),
                (rs, rowNum) -> new AdminAgentRunStep(
                        rs.getLong("id"),
                        rs.getInt("step_no"),
                        rs.getString("phase"),
                        rs.getString("status"),
                        rs.getString("decision_summary"),
                        rs.getString("observation_summary"),
                        rs.getString("selected_tool_id"),
                        rs.getString("selected_tool_reason"),
                        rs.getString("error_message"),
                        parseMap(rs.getString("metadata")),
                        rs.getObject("started_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class)
                )
        );
    }

    public List<AdminAgentCheckpoint> listCheckpoints(long tenantId, long runId) {
        return jdbcTemplate.query("""
                        SELECT id, checkpoint_no, step_id, checkpoint_type, stable, payload_json, created_at
                        FROM agent_checkpoint
                        WHERE tenant_id = :tenantId
                          AND agent_run_id = :runId
                        ORDER BY checkpoint_no DESC, id DESC
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("runId", runId),
                (rs, rowNum) -> new AdminAgentCheckpoint(
                        rs.getLong("id"),
                        rs.getInt("checkpoint_no"),
                        getNullableLong(rs, "step_id"),
                        rs.getString("checkpoint_type"),
                        rs.getBoolean("stable"),
                        parseMap(rs.getString("payload_json")),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );
    }

    public List<AdminToolCallDetail> listToolCalls(long tenantId, Long runId, String toolId) {
        StringBuilder sql = new StringBuilder("""
                SELECT tct.id, tct.agent_run_id, tct.tool_id, tct.plugin_id, pr.version AS plugin_version,
                       tct.request_summary, tct.response_summary, tct.status, tct.latency_ms, tct.error_message, tct.metadata, tct.created_at
                FROM tool_call_trace tct
                LEFT JOIN plugin_registry pr ON pr.id = tct.plugin_id
                WHERE tct.tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (runId != null) {
            sql.append(" AND tct.agent_run_id = :runId");
            params.addValue("runId", runId);
        }
        if (toolId != null && !toolId.isBlank()) {
            sql.append(" AND tct.tool_id = :toolId");
            params.addValue("toolId", toolId);
        }
        sql.append(" ORDER BY tct.created_at DESC, tct.id DESC");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new AdminToolCallDetail(
                rs.getLong("id"),
                rs.getLong("agent_run_id"),
                rs.getString("tool_id"),
                getNullableLong(rs, "plugin_id"),
                rs.getString("plugin_version"),
                rs.getString("request_summary"),
                rs.getString("response_summary"),
                rs.getString("status"),
                (Integer) rs.getObject("latency_ms"),
                rs.getString("error_message"),
                parseMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    public List<AdminPluginItem> listPlugins(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT pr.id, pr.plugin_key, pr.version, pr.display_name, pr.status, pr.manifest_json, pr.updated_at,
                               COALESCE(pi.config_json::text, '{}'::text) AS installation_config_json,
                               COALESCE((
                                   SELECT json_agg(ttb.tool_id ORDER BY ttb.tool_id)
                                   FROM tenant_tool_binding ttb
                                   WHERE ttb.tenant_id = :tenantId
                                     AND ttb.plugin_id = pr.id
                                     AND ttb.enabled = TRUE
                               )::text, '[]') AS enabled_tools_json,
                               COALESCE((
                                   SELECT json_agg(DISTINCT tts.secret_key ORDER BY tts.secret_key)
                                   FROM tenant_tool_secret tts
                                   JOIN tenant_tool_binding ttb
                                     ON ttb.tenant_id = tts.tenant_id
                                    AND ttb.tool_id = tts.tool_id
                                   WHERE tts.tenant_id = :tenantId
                                     AND ttb.plugin_id = pr.id
                               )::text, '[]') AS secret_keys_json,
                               COALESCE((
                                   SELECT COUNT(*)
                                   FROM tool_call_trace tct
                                   WHERE tct.tenant_id = :tenantId
                                     AND tct.plugin_id = pr.id
                                     AND tct.status = 'failed'
                               ), 0) AS recent_error_count,
                               COALESCE(pi.enabled, FALSE) AS enabled
                        FROM plugin_registry pr
                        LEFT JOIN plugin_installation pi
                          ON pi.plugin_id = pr.id
                         AND pi.tenant_id = :tenantId
                        ORDER BY pr.plugin_key ASC
                        """,
                Map.of("tenantId", tenantId),
                (rs, rowNum) -> new AdminPluginItem(
                        rs.getLong("id"),
                        rs.getString("plugin_key"),
                        rs.getString("version"),
                        rs.getString("display_name"),
                        rs.getBoolean("enabled"),
                        rs.getString("status"),
                        parseMap(rs.getString("manifest_json")),
                        parseMap(rs.getString("installation_config_json")),
                        parseStringList(rs.getString("enabled_tools_json")),
                        parseStringList(rs.getString("secret_keys_json")),
                        rs.getInt("recent_error_count"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                )
        );
    }

    public void updatePluginInstallation(long tenantId, long pluginId, boolean enabled) {
        jdbcTemplate.update("""
                        INSERT INTO plugin_installation (tenant_id, plugin_id, enabled, config_json)
                        VALUES (:tenantId, :pluginId, :enabled, '{}'::jsonb)
                        ON CONFLICT (tenant_id, plugin_id)
                        DO UPDATE SET enabled = EXCLUDED.enabled,
                                      updated_at = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("pluginId", pluginId)
                        .addValue("enabled", enabled)
        );
    }

    public List<PluginToolCapabilityRow> listPluginToolCapabilityRows(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT
                            pr.id AS plugin_id,
                            pr.plugin_key,
                            pr.manifest_json::text AS manifest_json,
                            COALESCE(pi.enabled, FALSE) AS plugin_enabled,
                            ttb.tool_id,
                            COALESCE(ttb.enabled, FALSE) AS tool_enabled
                        FROM plugin_registry pr
                        LEFT JOIN plugin_installation pi
                          ON pi.plugin_id = pr.id
                         AND pi.tenant_id = :tenantId
                        LEFT JOIN tenant_tool_binding ttb
                          ON ttb.plugin_id = pr.id
                         AND ttb.tenant_id = :tenantId
                        WHERE pr.status = 'active'
                        ORDER BY pr.plugin_key ASC, ttb.tool_id ASC
                        """,
                Map.of("tenantId", tenantId),
                (rs, rowNum) -> new PluginToolCapabilityRow(
                        rs.getLong("plugin_id"),
                        rs.getString("plugin_key"),
                        rs.getString("manifest_json"),
                        rs.getBoolean("plugin_enabled"),
                        rs.getString("tool_id"),
                        rs.getBoolean("tool_enabled")
                )
        );
    }

    public List<String> findConfiguredSecretKeys(long tenantId, String toolId) {
        return jdbcTemplate.query("""
                        SELECT secret_key
                        FROM tenant_tool_secret
                        WHERE tenant_id = :tenantId
                          AND tool_id = :toolId
                        ORDER BY secret_key ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolId", toolId),
                (rs, rowNum) -> rs.getString("secret_key")
        );
    }

    public boolean upsertToolSecret(long tenantId, String toolId, String secretKey, String secretValue) {
        return jdbcTemplate.update("""
                        INSERT INTO tenant_tool_secret (tenant_id, tool_id, secret_key, secret_value)
                        VALUES (:tenantId, :toolId, :secretKey, :secretValue)
                        ON CONFLICT (tenant_id, tool_id, secret_key)
                        DO UPDATE SET secret_value = EXCLUDED.secret_value,
                                      updated_at = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolId", toolId)
                        .addValue("secretKey", secretKey)
                        .addValue("secretValue", secretValue)
        ) > 0;
    }

    public boolean deleteToolSecret(long tenantId, String toolId, String secretKey) {
        return jdbcTemplate.update("""
                        DELETE FROM tenant_tool_secret
                        WHERE tenant_id = :tenantId
                          AND tool_id = :toolId
                          AND secret_key = :secretKey
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolId", toolId)
                        .addValue("secretKey", secretKey)
        ) > 0;
    }

    public record PluginToolCapabilityRow(
            long pluginId,
            String pluginKey,
            String manifestJson,
            boolean pluginEnabled,
            String toolId,
            boolean toolEnabled
    ) {
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
