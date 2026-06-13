package com.superagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.domain.MessageRole;
import com.superagent.chat.domain.ConversationExchange;
import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.ConversationSession;
import com.superagent.chat.repository.ConversationRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AgentAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversationRepository conversationRepository;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM tool_call_artifact");
        jdbcTemplate.execute("DELETE FROM tool_call_trace");
        jdbcTemplate.execute("DELETE FROM agent_checkpoint");
        jdbcTemplate.execute("DELETE FROM agent_run_step");
        jdbcTemplate.execute("DELETE FROM agent_run");
        jdbcTemplate.execute("DELETE FROM plugin_installation");
        jdbcTemplate.execute("DELETE FROM tenant_tool_secret");
        jdbcTemplate.execute("DELETE FROM tenant_tool_binding");
        jdbcTemplate.execute("DELETE FROM plugin_registry");
        jdbcTemplate.execute("DELETE FROM conversation_reference");
        jdbcTemplate.execute("DELETE FROM retrieval_trace_item");
        jdbcTemplate.execute("DELETE FROM rerank_trace");
        jdbcTemplate.execute("DELETE FROM retrieval_trace");
        jdbcTemplate.execute("DELETE FROM model_call_trace");
        jdbcTemplate.execute("DELETE FROM exchange_trace_stage");
        jdbcTemplate.execute("DELETE FROM conversation_exchange");
        jdbcTemplate.execute("DELETE FROM conversation_memory_summary");
        jdbcTemplate.execute("DELETE FROM conversation_message");
        jdbcTemplate.execute("DELETE FROM conversation_session");
        jdbcTemplate.execute("DELETE FROM tenant_runtime_setting");
    }

    @Test
    void shouldExposeAgentRunDetailAndTraceLinkage() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long ownerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);

        ConversationSession session = conversationRepository.createSession(
                tenantId,
                ownerId,
                "Agent Trace",
                MemoryStrategy.SUMMARY_PLUS_WINDOW,
                null
        );
        ConversationMessage userMessage = conversationRepository.createMessageWithMetadata(
                tenantId,
                session.id(),
                MessageRole.user,
                "帮我联网查一下今天的行业新闻",
                "success",
                null,
                Map.of("userId", ownerId)
        );
        ConversationExchange exchange = conversationRepository.createExchange(
                tenantId,
                session.id(),
                userMessage.id(),
                ExecutionMode.REACT_AGENT,
                "success",
                "requires_web_search",
                BigDecimal.valueOf(0.98)
        );
        jdbcTemplate.update(
                "UPDATE conversation_exchange SET finished_at = NOW() WHERE id = ?",
                exchange.id()
        );

        Long pluginId = jdbcTemplate.queryForObject("""
                        INSERT INTO plugin_registry (plugin_key, version, display_name, manifest_json, status)
                        VALUES ('core-tools', '0.1.0', 'Core Tools', '{"permissions":["web.search"]}'::jsonb, 'active')
                        RETURNING id
                        """,
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO plugin_installation (tenant_id, plugin_id, enabled, config_json) VALUES (?, ?, TRUE, '{}'::jsonb)",
                tenantId,
                pluginId
        );

        Long runId = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run (
                            tenant_id, session_id, exchange_id, trigger_message_id, status, memory_strategy, question, route_reason,
                            model_step_count, tool_call_count, latest_checkpoint_no, started_at, finished_at
                        ) VALUES (?, ?, ?, ?, 'success', 'SUMMARY_PLUS_WINDOW', ?, 'requires_web_search', 3, 1, 2, NOW(), NOW())
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                session.id(),
                exchange.id(),
                userMessage.id(),
                userMessage.content()
        );
        Long stepId = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run_step (
                            tenant_id, agent_run_id, step_no, phase, status, decision_summary, observation_summary, selected_tool_id,
                            selected_tool_reason, metadata, started_at, finished_at
                        ) VALUES (?, ?, 1, 'PLAN', 'success', 'resume_from_checkpoint', 'generated answer', 'web.search',
                                  'requires freshness', '{}'::jsonb, NOW(), NOW())
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                runId
        );
        jdbcTemplate.update("""
                        INSERT INTO agent_checkpoint (
                            tenant_id, agent_run_id, checkpoint_no, step_id, checkpoint_type, stable, payload_json
                        ) VALUES (?, ?, 2, ?, 'tool_result', TRUE, '{"toolId":"web.search"}'::jsonb)
                        """,
                tenantId,
                runId,
                stepId
        );
        jdbcTemplate.update("""
                        INSERT INTO tool_call_trace (
                            tenant_id, agent_run_id, step_id, tool_id, plugin_id, request_summary, response_summary, status, latency_ms, metadata
                        ) VALUES (?, ?, ?, 'web.search', ?, '查找行业新闻', '返回 3 条网页搜索结果', 'success', 120,
                                  '{"pluginVersion":"0.1.0","output":{"results":[{"title":"A","url":"https://example.com/a"}]}}'::jsonb)
                        """,
                tenantId,
                runId,
                stepId,
                pluginId
        );

        JsonNode runDetail = readJson(mockMvc.perform(get("/api/v1/admin/agent-runs/{runId}/detail", runId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(runDetail.path("data").path("summary").path("runId").asLong()).isEqualTo(runId);
        assertThat(runDetail.path("data").path("steps").get(0).path("decisionSummary").asText()).isEqualTo("resume_from_checkpoint");
        assertThat(runDetail.path("data").path("toolCalls").get(0).path("pluginVersion").asText()).isEqualTo("0.1.0");
        assertThat(runDetail.path("data").path("toolCalls").get(0).path("metadata").path("output").path("results").isArray()).isTrue();

        JsonNode traceDetail = readJson(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchange.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(traceDetail.path("data").path("agentRunId").asLong()).isEqualTo(runId);
        assertThat(traceDetail.path("data").path("agentRunStatus").asText()).isEqualTo("success");

        JsonNode byExchange = readJson(mockMvc.perform(get("/api/v1/admin/agent-runs/by-exchange/{exchangeId}", exchange.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(byExchange.path("data").path("runId").asLong()).isEqualTo(runId);
    }

    @Test
    void shouldExposePluginGovernanceAndToolCalls() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long ownerId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);
        ConversationSession session = conversationRepository.createSession(
                tenantId,
                ownerId,
                "Plugin Governance",
                MemoryStrategy.SUMMARY_PLUS_WINDOW,
                null
        );

        Long pluginId = jdbcTemplate.queryForObject("""
                        INSERT INTO plugin_registry (plugin_key, version, display_name, manifest_json, status)
                        VALUES (
                            'core-tools',
                            '0.1.0',
                            'Core Tools',
                            '{
                              "permissions":["web.search","http.request"],
                              "tools":[{"id":"web.search"},{"id":"http.request","riskLevel":"high"}]
                            }'::jsonb,
                            'active'
                        )
                        RETURNING id
                        """,
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO plugin_installation (tenant_id, plugin_id, enabled, config_json) VALUES (?, ?, TRUE, ?::jsonb)",
                tenantId,
                pluginId,
                "{\"rollout\":\"tenant-only\"}"
        );
        jdbcTemplate.update(
                "INSERT INTO tenant_tool_binding (tenant_id, tool_id, plugin_id, enabled, risk_level, config_json) VALUES (?, 'http.request', ?, TRUE, 'high', ?::jsonb)",
                tenantId,
                pluginId,
                "{\"allowedMethods\":[\"GET\",\"POST\"]}"
        );
        jdbcTemplate.update(
                "INSERT INTO tenant_tool_secret (tenant_id, tool_id, secret_key, secret_value) VALUES (?, 'http.request', 'service_token', 'masked')",
                tenantId
        );
        Long runId = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run (
                            tenant_id, session_id, status, memory_strategy, question, route_reason,
                            model_step_count, tool_call_count, latest_checkpoint_no, started_at, finished_at
                        ) VALUES (?, ?, 'failed', 'SUMMARY_PLUS_WINDOW', '调用外部接口', 'requires_http_request', 1, 1, 0, NOW(), NOW())
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                session.id()
        );
        jdbcTemplate.update("""
                        INSERT INTO tool_call_trace (
                            tenant_id, agent_run_id, step_id, tool_id, plugin_id, request_summary, response_summary, status, latency_ms, error_message, metadata
                        ) VALUES (?, ?, NULL, 'http.request', ?, '调用外部接口', '返回 500', 'failed', 210, 'upstream_500',
                                  '{"policy":{"allowedMethods":["GET","POST"]}}'::jsonb)
                        """,
                tenantId,
                runId,
                pluginId
        );

        JsonNode plugins = readJson(mockMvc.perform(get("/api/v1/admin/plugins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode plugin = plugins.path("data").get(0);
        assertThat(plugin.path("pluginId").asLong()).isEqualTo(pluginId);
        assertThat(plugin.path("installationConfig").path("rollout").asText()).isEqualTo("tenant-only");
        assertThat(plugin.path("enabledTools").get(0).asText()).isEqualTo("http.request");
        assertThat(plugin.path("secretKeys").get(0).asText()).isEqualTo("service_token");
        assertThat(plugin.path("recentErrorCount").asInt()).isEqualTo(1);

        JsonNode toolCalls = readJson(mockMvc.perform(get("/api/v1/admin/tool-calls")
                        .param("toolId", "http.request")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(toolCalls.path("data")).hasSize(1);
        assertThat(toolCalls.path("data").get(0).path("toolId").asText()).isEqualTo("http.request");

        JsonNode patchResponse = readJson(mockMvc.perform(patch("/api/v1/admin/plugins/{pluginId}", pluginId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(patchResponse.path("data").path("updated").asBoolean()).isTrue();
        Boolean enabled = jdbcTemplate.queryForObject(
                "SELECT enabled FROM plugin_installation WHERE tenant_id = ? AND plugin_id = ?",
                Boolean.class,
                tenantId,
                pluginId
        );
        assertThat(enabled).isFalse();
    }

    @Test
    void shouldExposeToolCapabilitiesForCurrentRoleWithoutSecretValues() throws Exception {
        JsonNode adminLogin = login("admin", "password123");
        String adminToken = adminLogin.path("data").path("accessToken").asText();
        long tenantId = adminLogin.path("data").path("defaultTenant").path("id").asLong();

        Long pluginId = jdbcTemplate.queryForObject("""
                        INSERT INTO plugin_registry (plugin_key, version, display_name, manifest_json, status)
                        VALUES (
                            'core-tools',
                            '0.1.0',
                            'Core Tools',
                            '{
                              "riskLevel":"standard",
                              "tools":[
                                {"id":"web.search","kind":"web"},
                                {"id":"http.request","kind":"http","riskLevel":"high"}
                              ]
                            }'::jsonb,
                            'active'
                        )
                        RETURNING id
                        """,
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO plugin_installation (tenant_id, plugin_id, enabled, config_json) VALUES (?, ?, TRUE, '{}'::jsonb)",
                tenantId,
                pluginId
        );
        jdbcTemplate.update(
                "INSERT INTO tenant_tool_binding (tenant_id, tool_id, plugin_id, enabled, risk_level, config_json) VALUES (?, 'http.request', ?, TRUE, 'high', '{}'::jsonb)",
                tenantId,
                pluginId
        );
        jdbcTemplate.update(
                "INSERT INTO tenant_tool_secret (tenant_id, tool_id, secret_key, secret_value) VALUES (?, 'http.request', 'service_token', 'do-not-return')",
                tenantId
        );

        JsonNode adminCapabilities = readJson(mockMvc.perform(get("/api/v1/tools/capabilities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode adminTools = adminCapabilities.path("data").path("tools");
        assertThat(adminTools).hasSize(2);
        JsonNode adminHttp = findTool(adminTools, "http.request");
        assertThat(adminHttp.path("enabled").asBoolean()).isTrue();
        assertThat(adminHttp.path("executable").asBoolean()).isTrue();
        assertThat(adminHttp.path("configuredSecrets").get(0).asText()).isEqualTo("service_token");
        assertThat(adminCapabilities.toString()).doesNotContain("do-not-return");

        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        JsonNode memberCapabilities = readJson(mockMvc.perform(get("/api/v1/tools/capabilities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode memberHttp = findTool(memberCapabilities.path("data").path("tools"), "http.request");
        assertThat(memberHttp.path("enabled").asBoolean()).isTrue();
        assertThat(memberHttp.path("executable").asBoolean()).isFalse();
        assertThat(memberHttp.path("reason").asText()).isEqualTo("role_not_allowed");
    }

    @Test
    void shouldManageToolSecretsWithOwnerOnlyPermissionAndAudit() throws Exception {
        JsonNode adminLogin = login("admin", "password123");
        String adminToken = adminLogin.path("data").path("accessToken").asText();
        long tenantId = adminLogin.path("data").path("defaultTenant").path("id").asLong();

        JsonNode updateResponse = readJson(mockMvc.perform(put("/api/v1/admin/tools/{toolId}/secrets/{secretKey}", "web.search", "tavilyApiKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"secret-value\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updateResponse.path("data").path("toolId").asText()).isEqualTo("web.search");
        assertThat(updateResponse.path("data").path("secretKey").asText()).isEqualTo("tavilyApiKey");
        assertThat(updateResponse.path("data").path("configured").asBoolean()).isTrue();
        assertThat(updateResponse.toString()).doesNotContain("secret-value");

        String stored = jdbcTemplate.queryForObject(
                "SELECT secret_value FROM tenant_tool_secret WHERE tenant_id = ? AND tool_id = 'web.search' AND secret_key = 'tavilyApiKey'",
                String.class,
                tenantId
        );
        assertThat(stored).isEqualTo("secret-value");
        Integer updateAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = ? AND action = 'tools.secret.updated' AND detail->>'secretKey' = 'tavilyApiKey'",
                Integer.class,
                tenantId
        );
        assertThat(updateAuditCount).isEqualTo(1);

        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        mockMvc.perform(put("/api/v1/admin/tools/{toolId}/secrets/{secretKey}", "web.search", "tavilyApiKey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"member-secret\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());

        JsonNode deleteResponse = readJson(mockMvc.perform(delete("/api/v1/admin/tools/{toolId}/secrets/{secretKey}", "web.search", "tavilyApiKey")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(deleteResponse.path("data").path("configured").asBoolean()).isFalse();
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant_tool_secret WHERE tenant_id = ? AND tool_id = 'web.search' AND secret_key = 'tavilyApiKey'",
                Integer.class,
                tenantId
        );
        assertThat(remaining).isZero();
        Integer deleteAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = ? AND action = 'tools.secret.deleted' AND detail->>'configured' = 'false'",
                Integer.class,
                tenantId
        );
        assertThat(deleteAuditCount).isEqualTo(1);
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode findTool(JsonNode tools, String toolId) {
        for (JsonNode tool : tools) {
            if (tool.path("toolId").asText().equals(toolId)) {
                return tool;
            }
        }
        throw new AssertionError("Tool not found: " + toolId);
    }
}
