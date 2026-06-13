package com.superagent.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.domain.MessageRole;
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
class ConversationFeedbackIntegrationTest {

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
        jdbcTemplate.execute("DELETE FROM conversation_feedback");
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
    }

    @Test
    void shouldUpsertListDeleteAndAdminQueryFeedback() throws Exception {
        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        long tenantId = memberLogin.path("data").path("defaultTenant").path("id").asLong();
        long memberId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE username = 'member'", Long.class);

        var session = conversationRepository.createSession(tenantId, memberId, "Feedback Session", MemoryStrategy.SUMMARY_PLUS_WINDOW, null);
        var userMessage = conversationRepository.createMessage(tenantId, session.id(), MessageRole.user, "退款规则是什么？", "success", null);
        var assistantMessage = conversationRepository.createMessage(tenantId, session.id(), MessageRole.assistant, "退款规则回答", "success", null);
        var exchange = conversationRepository.createExchange(
                tenantId,
                session.id(),
                userMessage.id(),
                ExecutionMode.RAG_QA,
                "success",
                "knowledge_base_selected",
                BigDecimal.valueOf(0.9)
        );
        conversationRepository.completeExchange(exchange.id(), tenantId, assistantMessage.id(), "success");

        JsonNode upsert = readJson(mockMvc.perform(put("/api/v1/messages/{messageId}/feedback", assistantMessage.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"down","comment":"引用不准确","correction":"请引用退款政策第二条"}
                                """)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(upsert.path("data").path("messageId").asLong()).isEqualTo(assistantMessage.id());
        assertThat(upsert.path("data").path("rating").asText()).isEqualTo("down");
        assertThat(upsert.path("data").path("exchangeId").asLong()).isEqualTo(exchange.id());

        JsonNode mine = readJson(mockMvc.perform(get("/api/v1/conversations/{sessionId}/feedbacks", session.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(mine.path("data")).hasSize(1);
        assertThat(mine.path("data").get(0).path("rating").asText()).isEqualTo("down");

        JsonNode adminLogin = login("admin", "password123");
        String adminToken = adminLogin.path("data").path("accessToken").asText();
        JsonNode adminList = readJson(mockMvc.perform(get("/api/v1/admin/feedbacks")
                        .param("rating", "down")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(adminList.path("data").path("total").asLong()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/feedbacks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());

        JsonNode deleteResponse = readJson(mockMvc.perform(delete("/api/v1/messages/{messageId}/feedback", assistantMessage.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(deleteResponse.path("data").path("deleted").asBoolean()).isTrue();
        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversation_feedback", Integer.class);
        assertThat(remaining).isZero();
    }

    @Test
    void shouldRejectFeedbackForUserMessage() throws Exception {
        JsonNode memberLogin = login("member", "password123");
        String memberToken = memberLogin.path("data").path("accessToken").asText();
        long tenantId = memberLogin.path("data").path("defaultTenant").path("id").asLong();
        long memberId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE username = 'member'", Long.class);
        var session = conversationRepository.createSession(tenantId, memberId, "Feedback Session", MemoryStrategy.SUMMARY_PLUS_WINDOW, null);
        var userMessage = conversationRepository.createMessage(tenantId, session.id(), MessageRole.user, "退款规则是什么？", "success", null);

        mockMvc.perform(put("/api/v1/messages/{messageId}/feedback", userMessage.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"up\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
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
