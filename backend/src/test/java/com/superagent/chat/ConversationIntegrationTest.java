package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
class ConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateUpdateListAndDeleteConversation() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        JsonNode created = createConversation(token, tenantId, "退款规则咨询");
        long sessionId = created.path("data").path("id").asLong();

        MvcResult listResponse = mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listJson = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(listJson.path("data").path("items").get(0).path("title").asText()).isEqualTo("退款规则咨询");

        MvcResult patchResponse = mockMvc.perform(patch("/api/v1/conversations/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "售后规则咨询",
                                "status", "archived"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode patchJson = objectMapper.readTree(patchResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(patchJson.path("data").path("title").asText()).isEqualTo("售后规则咨询");
        assertThat(patchJson.path("data").path("status").asText()).isEqualTo("archived");

        MvcResult deleteResponse = mockMvc.perform(delete("/api/v1/conversations/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode deleteJson = objectMapper.readTree(deleteResponse.getResponse().getContentAsString());
        assertThat(deleteJson.path("data").path("deleted").asBoolean()).isTrue();
    }

    @Test
    void shouldStreamMessageAndPersistAssistantReply() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "流式测试").path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "退款规则是什么？"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("event:start");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("event:done");

        Integer messageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_message WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(messageCount).isNotNull().isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldRejectConcurrentStreamOnSameSession() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "并发测试").path("data").path("id").asLong();

        MvcResult first = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "第一条消息"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "第二条消息"))))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondJson.path("code").asText()).isEqualTo("CONFLICT");

        assertThat(awaitStreamBody(first, "event:done")).contains("event:done");
    }

    @Test
    void shouldStopRunningConversation() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "停止测试").path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "请持续输出一段说明文本"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult stopResponse = mockMvc.perform(post("/api/v1/conversations/{sessionId}/stop", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stopJson = objectMapper.readTree(stopResponse.getResponse().getContentAsString());
        assertThat(stopJson.path("data").path("stopped").asBoolean()).isTrue();

        assertThat(awaitStreamBody(streamResult, "\"stopped\":true")).contains("event:done");
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

    private JsonNode createConversation(String token, long tenantId, String title) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "memoryStrategy", "SLIDING_WINDOW"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    private String awaitStreamBody(MvcResult result, String marker) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains(marker)) {
                return body;
            }
            Thread.sleep(50L);
        }
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains(marker);
        return body;
    }
}
