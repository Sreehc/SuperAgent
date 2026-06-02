package com.superagent.agent.repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentRunRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AgentRunRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createRun(
            long tenantId,
            long sessionId,
            long exchangeId,
            long messageId,
            String question,
            String memoryStrategy,
            String metadataJson
    ) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run (
                            tenant_id, session_id, exchange_id, trigger_message_id, question, memory_strategy, status, started_at, metadata
                        ) VALUES (
                            :tenantId, :sessionId, :exchangeId, :messageId, :question, :memoryStrategy, 'running', NOW(), CAST(:metadataJson AS jsonb)
                        )
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sessionId", sessionId)
                        .addValue("exchangeId", exchangeId)
                        .addValue("messageId", messageId)
                        .addValue("question", question)
                        .addValue("memoryStrategy", memoryStrategy)
                        .addValue("metadataJson", metadataJson),
                Long.class
        );
        return id == null ? -1L : id;
    }

    public long createStep(
            long tenantId,
            long runId,
            int stepNo,
            String phase,
            String status,
            String decisionSummary,
            String observationSummary,
            String selectedToolId,
            String selectedToolReason,
            String errorMessage,
            String metadataJson
    ) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run_step (
                            tenant_id, agent_run_id, step_no, phase, status, decision_summary, observation_summary,
                            selected_tool_id, selected_tool_reason, error_message, metadata, started_at, finished_at
                        ) VALUES (
                            :tenantId, :runId, :stepNo, :phase, :status, :decisionSummary, :observationSummary,
                            :selectedToolId, :selectedToolReason, :errorMessage, CAST(:metadataJson AS jsonb), NOW(), NOW()
                        )
                        ON CONFLICT (agent_run_id, step_no)
                        DO UPDATE SET phase = EXCLUDED.phase,
                                      status = EXCLUDED.status,
                                      decision_summary = EXCLUDED.decision_summary,
                                      observation_summary = EXCLUDED.observation_summary,
                                      selected_tool_id = EXCLUDED.selected_tool_id,
                                      selected_tool_reason = EXCLUDED.selected_tool_reason,
                                      error_message = EXCLUDED.error_message,
                                      metadata = EXCLUDED.metadata,
                                      finished_at = NOW(),
                                      updated_at = NOW()
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId)
                        .addValue("stepNo", stepNo)
                        .addValue("phase", phase)
                        .addValue("status", status)
                        .addValue("decisionSummary", decisionSummary)
                        .addValue("observationSummary", observationSummary)
                        .addValue("selectedToolId", selectedToolId)
                        .addValue("selectedToolReason", selectedToolReason)
                        .addValue("errorMessage", errorMessage)
                        .addValue("metadataJson", metadataJson == null ? "{}" : metadataJson),
                Long.class
        );
        return id == null ? -1L : id;
    }

    public void saveCheckpoint(long tenantId, long runId, int checkpointNo, Long stepId, String checkpointType, String payloadJson) {
        jdbcTemplate.update("""
                        INSERT INTO agent_checkpoint (
                            tenant_id, agent_run_id, checkpoint_no, step_id, checkpoint_type, payload_json
                        ) VALUES (
                            :tenantId, :runId, :checkpointNo, :stepId, :checkpointType, CAST(:payloadJson AS jsonb)
                        )
                        ON CONFLICT (agent_run_id, checkpoint_no)
                        DO UPDATE SET payload_json = EXCLUDED.payload_json,
                                      step_id = EXCLUDED.step_id,
                                      checkpoint_type = EXCLUDED.checkpoint_type
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId)
                        .addValue("checkpointNo", checkpointNo)
                        .addValue("stepId", stepId)
                        .addValue("checkpointType", checkpointType)
                        .addValue("payloadJson", payloadJson)
        );
        jdbcTemplate.update("""
                        UPDATE agent_run
                        SET latest_checkpoint_no = GREATEST(latest_checkpoint_no, :checkpointNo)
                        WHERE id = :runId
                          AND tenant_id = :tenantId
                        """,
                Map.of("runId", runId, "tenantId", tenantId, "checkpointNo", checkpointNo)
        );
    }

    public long createToolCall(long tenantId, long runId, Long stepId, String toolId, Long pluginId, String requestSummary) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO tool_call_trace (
                            tenant_id, agent_run_id, step_id, tool_id, plugin_id, request_summary, status
                        ) VALUES (
                            :tenantId, :runId, :stepId, :toolId, :pluginId, :requestSummary, 'running'
                        )
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId)
                        .addValue("stepId", stepId)
                        .addValue("toolId", toolId)
                        .addValue("pluginId", pluginId)
                        .addValue("requestSummary", requestSummary),
                Long.class
        );
        return id == null ? -1L : id;
    }

    public void completeToolCall(
            long tenantId,
            long toolCallId,
            String responseSummary,
            int latencyMs,
            String status,
            String errorMessage,
            String metadataJson
    ) {
        jdbcTemplate.update("""
                        UPDATE tool_call_trace
                        SET response_summary = :responseSummary,
                            latency_ms = :latencyMs,
                            status = :status,
                            error_message = :errorMessage,
                            metadata = CAST(:metadataJson AS jsonb)
                        WHERE id = :toolCallId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolCallId", toolCallId)
                        .addValue("responseSummary", responseSummary)
                        .addValue("latencyMs", latencyMs)
                        .addValue("status", status)
                        .addValue("errorMessage", errorMessage)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public void updateRunProgress(long tenantId, long runId, int modelSteps, int toolCalls, String status, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE agent_run
                        SET model_step_count = :modelSteps,
                            tool_call_count = :toolCalls,
                            status = :status,
                            error_message = :errorMessage,
                            finished_at = CASE WHEN :status IN ('success', 'failed', 'cancelled') THEN NOW() ELSE finished_at END
                        WHERE id = :runId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId)
                        .addValue("modelSteps", modelSteps)
                        .addValue("toolCalls", toolCalls)
                        .addValue("status", status)
                        .addValue("errorMessage", errorMessage)
        );
    }

    public void markRunRunning(long tenantId, long runId) {
        jdbcTemplate.update("""
                        UPDATE agent_run
                        SET status = 'running',
                            error_message = NULL,
                            finished_at = NULL,
                            started_at = COALESCE(started_at, NOW())
                        WHERE id = :runId
                          AND tenant_id = :tenantId
                        """,
                Map.of("runId", runId, "tenantId", tenantId)
        );
    }

    public Optional<RunRecord> findRun(long runId) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, session_id, exchange_id, trigger_message_id, question, memory_strategy,
                               status, latest_checkpoint_no, model_step_count, tool_call_count, metadata::text AS metadata_json, error_message
                        FROM agent_run
                        WHERE id = :runId
                        """,
                Map.of("runId", runId),
                rs -> rs.next() ? Optional.of(mapRunStatus(rs)) : Optional.empty()
        );
    }

    public Optional<CheckpointRecord> findLatestStableCheckpoint(long tenantId, long runId) {
        return jdbcTemplate.query("""
                        SELECT checkpoint_no, step_id, checkpoint_type, stable, payload_json::text AS payload_json, created_at
                        FROM agent_checkpoint
                        WHERE tenant_id = :tenantId
                          AND agent_run_id = :runId
                          AND stable = TRUE
                        ORDER BY checkpoint_no DESC, id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId),
                rs -> rs.next() ? Optional.of(new CheckpointRecord(
                        rs.getInt("checkpoint_no"),
                        getNullableLong(rs, "step_id"),
                        rs.getString("checkpoint_type"),
                        rs.getBoolean("stable"),
                        rs.getString("payload_json"),
                        rs.getObject("created_at", OffsetDateTime.class)
                )) : Optional.empty()
        );
    }

    public Optional<Long> findLatestToolCallId(long tenantId, long runId, long stepId) {
        return jdbcTemplate.query("""
                        SELECT id
                        FROM tool_call_trace
                        WHERE tenant_id = :tenantId
                          AND agent_run_id = :runId
                          AND step_id = :stepId
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId)
                        .addValue("stepId", stepId),
                rs -> rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty()
        );
    }

    public List<EnabledToolRow> findEnabledTools(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT
                            pr.id AS plugin_id,
                            pr.plugin_key,
                            pr.manifest_json,
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
                (rs, rowNum) -> new EnabledToolRow(
                        rs.getLong("plugin_id"),
                        rs.getString("plugin_key"),
                        rs.getString("manifest_json"),
                        rs.getBoolean("plugin_enabled"),
                        rs.getString("tool_id"),
                        rs.getBoolean("tool_enabled")
                )
        );
    }

    public Optional<Map<String, Object>> findRuntimeSetting(long tenantId, String settingKey) {
        return jdbcTemplate.query("""
                        SELECT config_json::text AS config_json
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
                    return Optional.of(Map.of("configJson", rs.getString("config_json")));
                }
        );
    }

    public Optional<TenantToolBindingRow> findToolBinding(long tenantId, String toolId) {
        return jdbcTemplate.query("""
                        SELECT plugin_id, enabled, risk_level, config_json::text AS config_json
                        FROM tenant_tool_binding
                        WHERE tenant_id = :tenantId
                          AND tool_id = :toolId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolId", toolId),
                rs -> rs.next() ? Optional.of(new TenantToolBindingRow(
                        getNullableLong(rs, "plugin_id"),
                        rs.getBoolean("enabled"),
                        rs.getString("risk_level"),
                        rs.getString("config_json")
                )) : Optional.empty()
        );
    }

    public Map<String, String> findToolSecrets(long tenantId, String toolId) {
        return jdbcTemplate.query("""
                        SELECT secret_key, secret_value
                        FROM tenant_tool_secret
                        WHERE tenant_id = :tenantId
                          AND tool_id = :toolId
                        ORDER BY secret_key ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("toolId", toolId),
                rs -> {
                    Map<String, String> secrets = new LinkedHashMap<>();
                    while (rs.next()) {
                        secrets.put(rs.getString("secret_key"), rs.getString("secret_value"));
                    }
                    return secrets;
                }
        );
    }

    private RunRecord mapRunStatus(ResultSet rs) throws java.sql.SQLException {
        return new RunRecord(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("session_id"),
                getNullableLong(rs, "exchange_id"),
                getNullableLong(rs, "trigger_message_id"),
                rs.getString("question"),
                rs.getString("memory_strategy"),
                rs.getString("status"),
                rs.getInt("latest_checkpoint_no"),
                rs.getInt("model_step_count"),
                rs.getInt("tool_call_count"),
                rs.getString("metadata_json"),
                rs.getString("error_message")
        );
    }

    public record RunRecord(
            long id,
            long tenantId,
            long sessionId,
            Long exchangeId,
            Long triggerMessageId,
            String question,
            String memoryStrategy,
            String status,
            int latestCheckpointNo,
            int modelStepCount,
            int toolCallCount,
            String metadataJson,
            String errorMessage
    ) {
    }

    public record CheckpointRecord(
            int checkpointNo,
            Long stepId,
            String checkpointType,
            boolean stable,
            String payloadJson,
            OffsetDateTime createdAt
    ) {
    }

    public record EnabledToolRow(
            long pluginId,
            String pluginKey,
            String manifestJson,
            boolean pluginEnabled,
            String toolId,
            boolean toolEnabled
    ) {
    }

    public record TenantToolBindingRow(
            Long pluginId,
            boolean enabled,
            String riskLevel,
            String configJson
    ) {
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
