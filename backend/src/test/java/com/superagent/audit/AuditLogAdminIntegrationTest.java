package com.superagent.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuditLogAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM audit_log");
    }

    @Test
    void shouldListAuditLogsWithFiltersForAdminOnly() throws Exception {
        JsonNode adminLogin = login("admin", "password123");
        String adminToken = adminLogin.path("data").path("accessToken").asText();
        long tenantId = adminLogin.path("data").path("defaultTenant").path("id").asLong();
        long actorId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);

        jdbcTemplate.update("""
                        INSERT INTO audit_log (tenant_id, actor_id, action, resource_type, resource_id, detail)
                        VALUES (?, ?, 'tools.secret.updated', 'tool_secret', NULL, '{"toolId":"web.search","secretKey":"tavilyApiKey","configured":true}'::jsonb)
                        """,
                tenantId,
                actorId
        );
        jdbcTemplate.update("""
                        INSERT INTO audit_log (tenant_id, actor_id, action, resource_type, resource_id, detail)
                        VALUES (?, ?, 'settings.rag.updated', 'runtime_setting', NULL, '{"section":"rag"}'::jsonb)
                        """,
                tenantId,
                actorId
        );

        JsonNode response = readJson(mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "tools.secret.updated")
                        .param("resourceType", "tool_secret")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(response.path("data").path("total").asLong()).isEqualTo(1);
        JsonNode item = response.path("data").path("items").get(0);
        assertThat(item.path("action").asText()).isEqualTo("tools.secret.updated");
        assertThat(item.path("detail").path("secretKey").asText()).isEqualTo("tavilyApiKey");
        assertThat(response.toString()).doesNotContain("secret-value");

        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
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
