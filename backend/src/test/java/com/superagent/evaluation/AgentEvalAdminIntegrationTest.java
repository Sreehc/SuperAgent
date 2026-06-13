package com.superagent.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
class AgentEvalAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM audit_log WHERE action LIKE 'eval.%'");
        jdbcTemplate.execute("DELETE FROM agent_eval_run");
        jdbcTemplate.execute("DELETE FROM agent_eval_case");
        jdbcTemplate.execute("DELETE FROM agent_eval_suite");
    }

    @Test
    void shouldManageSuitesCasesAndRuns() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        JsonNode suite = readJson(mockMvc.perform(post("/api/v1/admin/evals/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "suiteKey", "agent-productization",
                                "name", "Agent 产品化回归",
                                "description", "覆盖 Agent 与工具治理"
                        ))))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        long suiteId = suite.path("id").asLong();

        JsonNode evalCase = readJson(mockMvc.perform(post("/api/v1/admin/evals/suites/{suiteId}/cases", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "caseKey", "tool-trace",
                                "input", Map.of("command", "./mvnw -Dtest=AgentAdminIntegrationTest test"),
                                "expected", Map.of("tag", "tool-trace")
                        ))))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(evalCase.path("caseKey").asText()).isEqualTo("tool-trace");

        JsonNode run = readJson(mockMvc.perform(post("/api/v1/admin/evals/suites/{suiteId}/runs", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "report", Map.of(
                                        "passed", false,
                                        "cases", List.of(
                                                Map.of("caseKey", "tool-trace", "passed", true),
                                                Map.of("caseKey", "agent-loop", "passed", false)
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(run.path("status").asText()).isEqualTo("failed");
        assertThat(run.path("passedCount").asInt()).isEqualTo(1);
        assertThat(run.path("failedCount").asInt()).isEqualTo(1);

        JsonNode detail = readJson(mockMvc.perform(get("/api/v1/admin/evals/suites/{suiteId}", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(detail.path("cases")).hasSize(1);
        assertThat(detail.path("recentRuns")).hasSize(1);

        JsonNode list = readJson(mockMvc.perform(get("/api/v1/admin/evals/suites")
                        .param("keyword", "productization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(list.path("total").asInt()).isEqualTo(1);

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action LIKE 'eval.%'", Integer.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(3);

        JsonNode deleted = readJson(mockMvc.perform(delete("/api/v1/admin/evals/cases/{caseId}", evalCase.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(deleted.path("deleted").asBoolean()).isTrue();
    }

    @Test
    void shouldForbidMembers() throws Exception {
        JsonNode login = login("member", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        mockMvc.perform(get("/api/v1/admin/evals/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());
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
