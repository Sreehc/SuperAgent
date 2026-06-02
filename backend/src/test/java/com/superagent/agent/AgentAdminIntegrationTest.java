package com.superagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
