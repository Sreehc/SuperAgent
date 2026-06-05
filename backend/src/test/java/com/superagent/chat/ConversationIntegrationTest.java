package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.service.AgentGatewayClient;
import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.TestChatModelClientConfiguration;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import com.superagent.rag.TestRerankClientConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import({TestEmbeddingClientConfiguration.class, TestChatModelClientConfiguration.class, TestRerankClientConfiguration.class})
class ConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @MockBean
    private AgentGatewayClient agentGatewayClient;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("DELETE FROM tenant_runtime_setting");
        jdbcTemplate.execute("DELETE FROM tool_call_artifact");
        jdbcTemplate.execute("DELETE FROM tool_call_trace");
        jdbcTemplate.execute("DELETE FROM agent_checkpoint");
        jdbcTemplate.execute("DELETE FROM agent_run_step");
        jdbcTemplate.execute("DELETE FROM agent_run");
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
        jdbcTemplate.execute("DELETE FROM document_task");
        jdbcTemplate.execute("DELETE FROM document_embedding");
        jdbcTemplate.execute("DELETE FROM document_chunk");
        jdbcTemplate.execute("DELETE FROM knowledge_document_version");
        jdbcTemplate.execute("DELETE FROM knowledge_document");
        jdbcTemplate.execute("DELETE FROM chunking_profile");
        jdbcTemplate.execute("DELETE FROM knowledge_domain");
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
        assertThat(listJson.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/conversations/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(detailJson.path("data").path("title").asText()).isEqualTo("退款规则咨询");

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
        assertThat(body).contains("execution_planning");
        assertThat(body).contains("[1]");

        Integer messageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM conversation_message WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(messageCount).isNotNull().isGreaterThanOrEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT route_reason FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).isEqualTo("knowledge_base_selected");
    }

    @Test
    void shouldRouteAmbiguousMessageToClarificationMode() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "澄清测试").path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "这个怎么配？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("请补充更明确的对象");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT execution_mode FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).isEqualTo("CLARIFICATION");
    }

    @Test
    void shouldRouteOpenEndedMessageToAgentAndBridgeAgentEvents() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "Agent 路由测试").path("data").path("id").asLong();

        when(agentGatewayClient.createRun(any())).thenReturn(88L);
        doAnswer(invocation -> {
            AgentGatewayClient.AgentEventConsumer consumer = invocation.getArgument(2);
            consumer.accept("agent_step", "{\"runId\":88,\"phase\":\"PLAN\",\"status\":\"success\",\"summary\":\"Agent 已开始规划\"}");
            consumer.accept("tool_start", "{\"toolId\":\"web.search\",\"summary\":\"开始搜索\"}");
            consumer.accept("delta", "{\"text\":\"这是 Agent 回答。\"}");
            consumer.accept("recommendation", "{\"items\":[\"继续追问这个主题\"]}");
            return null;
        }).when(agentGatewayClient).streamRun(anyLong(), any(BooleanSupplier.class), any(AgentGatewayClient.AgentEventConsumer.class));

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "请联网搜索今天最新的退款政策变化"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("event:agent_step");
        assertThat(body).contains("event:tool_start");
        assertThat(body).contains("event:recommendation");
        assertThat(body).contains("这是 Agent 回答。");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT execution_mode FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).isEqualTo("REACT_AGENT");
        verify(agentGatewayClient).createRun(any());
        verify(agentGatewayClient).streamRun(eq(88L), any(BooleanSupplier.class), any(AgentGatewayClient.AgentEventConsumer.class));
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
    void shouldResumeLatestAgentRunForConversation() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long sessionId = createConversation(token, tenantId, "恢复测试").path("data").path("id").asLong();

        Long userMessageId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_message (tenant_id, session_id, role, content, status, metadata)
                        VALUES (?, ?, 'user', '请恢复刚才的 Agent 执行', 'success', ?::jsonb)
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                sessionId,
                "{\"userId\":1}"
        );
        Long exchangeId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversation_exchange (
                            tenant_id, session_id, user_message_id, execution_mode, status, route_reason, started_at
                        ) VALUES (?, ?, ?, ?, 'running', 'open_ended_or_realtime_request_routed_to_agent', NOW())
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                sessionId,
                userMessageId,
                ExecutionMode.REACT_AGENT.name()
        );
        Long runId = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_run (
                            tenant_id, session_id, exchange_id, trigger_message_id, status, memory_strategy, question, route_reason, started_at
                        ) VALUES (?, ?, ?, ?, 'running', 'SUMMARY_PLUS_WINDOW', '请恢复刚才的 Agent 执行', 'resume_requested', NOW())
                        RETURNING id
                        """,
                Long.class,
                tenantId,
                sessionId,
                exchangeId,
                userMessageId
        );

        when(agentGatewayClient.resumeRun(eq(runId))).thenReturn(true);

        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/v1/conversations/{sessionId}/resume", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.path("data").path("resumed").asBoolean()).isTrue();
        assertThat(response.path("data").path("runId").asLong()).isEqualTo(runId);
        verify(agentGatewayClient).resumeRun(eq(runId));
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
        assertThat(body).doesNotContain("event:reference");
    }

    @Test
    void shouldFallbackWhenEvidenceCountIsBelowConfiguredThreshold() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "阈值知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "refund-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noEvidenceMinResults", 2,
                                "forceCitationEnabled", true
                        ))))
                .andExpect(status().isOk());
        long sessionId = createConversation(token, tenantId, "阈值测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("未检索到足够证据");
        assertThat(body).doesNotContain("event:reference");
    }

    @Test
    void shouldFallbackWhenAnswerConfidenceIsBelowConfiguredThreshold() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "置信度知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "refund-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noEvidenceMinResults", 1,
                                "answerConfidenceThreshold", 0.99d
                        ))))
                .andExpect(status().isOk());
        long sessionId = createConversation(token, tenantId, "置信度测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("未检索到足够证据");
        assertThat(body).doesNotContain("event:reference");
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
