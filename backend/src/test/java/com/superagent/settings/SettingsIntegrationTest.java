package com.superagent.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import(TestEmbeddingClientConfiguration.class)
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSettingsState() {
        cleanSettings();
    }

    @AfterEach
    void cleanSettings() {
        jdbcTemplate.execute("DELETE FROM tenant_runtime_setting");
        jdbcTemplate.execute("DELETE FROM audit_log WHERE action LIKE 'settings.%'");
    }

    @Test
    void shouldAllowOwnerToUpdateSettingsAndPersistAuditLog() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        mockMvc.perform(patch("/api/v1/admin/settings/model")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseUrl", "https://model.example.com/v1",
                                "chatModel", "gpt-4.1",
                                "embeddingModel", "text-embedding-3-large",
                                "apiKey", "sk-runtime-model"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/settings/rerank")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "enabled", true,
                                "provider", "openai-compatible",
                                "baseUrl", "https://rerank.example.com/v1",
                                "model", "bge-reranker-large",
                                "apiKey", "rk-runtime-rerank"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRagPatch())))
                .andExpect(status().isOk());

        MvcResult modelResponse = mockMvc.perform(get("/api/v1/admin/settings/model")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode modelJson = objectMapper.readTree(modelResponse.getResponse().getContentAsString());
        assertThat(modelJson.path("data").path("provider").asText()).isEqualTo("openai-compatible");
        assertThat(modelJson.path("data").path("baseUrl").asText()).isEqualTo("https://model.example.com/v1");
        assertThat(modelJson.path("data").path("apiKeySet").asBoolean()).isTrue();

        MvcResult ragResponse = mockMvc.perform(get("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ragJson = objectMapper.readTree(ragResponse.getResponse().getContentAsString());
        assertThat(ragJson.path("data").path("queryUnderstandingEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("decompositionEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("rewriteEnabled").asBoolean()).isFalse();
        assertThat(ragJson.path("data").path("versionConsistencyEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("neighborExpansionEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("candidateTopK").asInt()).isEqualTo(10);
        assertThat(ragJson.path("data").path("rerankEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("neighborWindow").asInt()).isEqualTo(1);
        assertThat(ragJson.path("data").path("maxChunksPerDocument").asInt()).isEqualTo(2);
        assertThat(ragJson.path("data").path("perQuestionEvidenceCharLimit").asInt()).isEqualTo(2800);
        assertThat(ragJson.path("data").path("maxEvidenceContentChars").asInt()).isEqualTo(1500);
        assertThat(ragJson.path("data").path("answerConfidenceThreshold").asDouble()).isEqualTo(0.61d);
        assertThat(ragJson.path("data").path("queryResultCacheEnabled").asBoolean()).isTrue();
        assertThat(ragJson.path("data").path("queryResultCacheTtlSeconds").asLong()).isEqualTo(45L);
        assertThat(ragJson.path("data").path("noEvidenceMinResults").asInt()).isEqualTo(2);
        assertThat(ragJson.path("data").path("forceCitationEnabled").asBoolean()).isFalse();

        MvcResult rerankResponse = mockMvc.perform(get("/api/v1/admin/settings/rerank")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rerankJson = objectMapper.readTree(rerankResponse.getResponse().getContentAsString());
        assertThat(rerankJson.path("data").path("enabled").asBoolean()).isTrue();
        assertThat(rerankJson.path("data").path("apiKeySet").asBoolean()).isTrue();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = ? AND action IN ('settings.model.updated', 'settings.rag.updated', 'settings.rerank.updated')",
                Integer.class,
                tenantId
        );
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    void shouldForbidMemberFromAccessingSettingsApis() throws Exception {
        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        long tenantId = memberLogin.path("data").path("defaultTenant").path("id").asLong();

        mockMvc.perform(get("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vectorTopK", 9))))
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
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    private Map<String, Object> buildRagPatch() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queryUnderstandingEnabled", true);
        payload.put("decompositionEnabled", true);
        payload.put("rewriteEnabled", false);
        payload.put("subQuestionEnabled", false);
        payload.put("versionConsistencyEnabled", true);
        payload.put("neighborExpansionEnabled", true);
        payload.put("maxSubQuestions", 2);
        payload.put("vectorTopK", 12);
        payload.put("keywordTopK", 6);
        payload.put("candidateTopK", 10);
        payload.put("rrfK", 40);
        payload.put("rerankEnabled", true);
        payload.put("neighborWindow", 1);
        payload.put("maxChunksPerDocument", 2);
        payload.put("evidenceLimit", 5);
        payload.put("perQuestionEvidenceCharLimit", 2800);
        payload.put("totalEvidenceCharLimit", 8400);
        payload.put("maxEvidenceContentChars", 1500);
        payload.put("minRelevanceScore", 0.42d);
        payload.put("answerConfidenceThreshold", 0.61d);
        payload.put("queryResultCacheEnabled", true);
        payload.put("queryResultCacheTtlSeconds", 45L);
        payload.put("noEvidenceMinResults", 2);
        payload.put("forceCitationEnabled", false);
        return payload;
    }
}
