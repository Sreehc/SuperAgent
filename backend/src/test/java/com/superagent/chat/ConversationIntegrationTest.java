package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import(TestEmbeddingClientConfiguration.class)
class ConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM conversation_reference");
        jdbcTemplate.execute("DELETE FROM retrieval_trace");
        jdbcTemplate.execute("DELETE FROM model_call_trace");
        jdbcTemplate.execute("DELETE FROM exchange_trace_stage");
        jdbcTemplate.execute("DELETE FROM conversation_exchange");
        jdbcTemplate.execute("DELETE FROM conversation_memory_summary");
        jdbcTemplate.execute("DELETE FROM conversation_message");
        jdbcTemplate.execute("DELETE FROM conversation_session");
        jdbcTemplate.execute("DELETE FROM document_task");
        jdbcTemplate.execute("DELETE FROM document_embedding");
        jdbcTemplate.execute("DELETE FROM document_chunk");
        jdbcTemplate.execute("DELETE FROM knowledge_document");
        jdbcTemplate.execute("DELETE FROM knowledge_base");
    }

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
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "售后知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "refund-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "流式测试", knowledgeBaseId).path("data").path("id").asLong();

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
        assertThat(body).contains("event:trace_stage");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("event:reference");
        assertThat(body).contains("event:recommendation");
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

    @Test
    void shouldReturnNoEvidenceResponseWhenKnowledgeBaseHasNoRelevantEvidence() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "空证据知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "ops-guide.txt", "这里只描述系统部署步骤和日志目录。");
        long sessionId = createConversation(token, tenantId, "无证据测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "退款规则是什么？",
                                "knowledgeBaseId", knowledgeBaseId
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("未检索到足够证据");
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
        return createConversation(token, tenantId, title, null);
    }

    private JsonNode createConversation(String token, long tenantId, String title, Long knowledgeBaseId) throws Exception {
        java.util.Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("title", title);
        requestBody.put("memoryStrategy", "SLIDING_WINDOW");
        if (knowledgeBaseId != null) {
            requestBody.put("knowledgeBaseId", knowledgeBaseId);
        }
        MvcResult response = mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    private long createKnowledgeBase(String token, long tenantId, String name, String status) throws Exception {
        MvcResult createResponse = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "description", name + "描述",
                                "visibility", "tenant"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long knowledgeBaseId = objectMapper.readTree(createResponse.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        mockMvc.perform(patch("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
        return knowledgeBaseId;
    }

    private void uploadAndProcessKnowledgeDocument(
            String token,
            long tenantId,
            long knowledgeBaseId,
            String fileName,
            String content
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );
        MvcResult uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", fileName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        documentProcessingService.process(new DocumentTaskMessage(
                tenantId,
                uploaded.path("data").path("id").asLong(),
                uploaded.path("data").path("taskId").asLong(),
                "test"
        ));
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
