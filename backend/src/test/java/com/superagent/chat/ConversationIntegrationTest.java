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
import java.util.List;
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
    void shouldListMemberOwnedConversations() throws Exception {
        JsonNode login = login("member", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        JsonNode created = createConversation(token, tenantId, "成员自己的会话");
        long sessionId = created.path("data").path("id").asLong();

        MvcResult listResponse = mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listJson = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(listJson.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(listJson.path("data").path("items").get(0).path("id").asLong()).isEqualTo(sessionId);
        assertThat(listJson.path("data").path("items").get(0).path("title").asText()).isEqualTo("成员自己的会话");
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

    @Test
    void shouldFallbackForHighRiskQuestionWhenEvidenceIsTooThin() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "高风险知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "refund-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noEvidenceMinResults", 1,
                                "answerConfidenceThreshold", 0.55d,
                                "forceCitationEnabled", false
                        ))))
                .andExpect(status().isOk());
        long sessionId = createConversation(token, tenantId, "高风险测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "这个退款规则在法律上一定合法吗？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("未检索到足够证据");
        assertThat(body).doesNotContain("event:reference");
    }

    @Test
    void shouldUseMultiTurnContextForFollowUpRagQuestion() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "多轮知识库", "published");
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "refund-guide.txt",
                "退款规则：需在7日内提交申请。申请材料：需提供订单截图和退款原因说明。"
        );
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "queryUnderstandingEnabled", false,
                                "rewriteEnabled", true,
                                "subQuestionEnabled", true
                        ))))
                .andExpect(status().isOk());
        long sessionId = createConversation(token, tenantId, "多轮指代测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult firstTurn = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        String firstBody = awaitStreamBody(firstTurn, "event:done");
        assertThat(firstBody).contains("event:delta");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM conversation_message WHERE session_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).contains("退款规则包括在 7 日内提交申请");

        MvcResult secondTurn = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "那申请材料呢？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(secondTurn, "event:done");
        assertThat(body).contains("event:delta");
        assertThat(body).contains("event:reference");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT execution_mode FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).isEqualTo("RAG_QA");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM conversation_message WHERE session_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).contains("申请材料包括订单截图和退款原因说明");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT output_summary FROM exchange_trace_stage WHERE exchange_id = (SELECT id FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1) AND stage_code = 'query_rewrite' ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).contains("申请材料");
    }

    @Test
    void shouldSplitCompositeQuestionIntoMultipleRetrievalSteps() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "复合问题知识库", "published");
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "refund-policy.txt",
                "退款规则：需在7日内提交申请。"
        );
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "refund-materials.txt",
                "申请材料：需提供订单截图和退款原因说明。"
        );
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "queryUnderstandingEnabled", false,
                                "rewriteEnabled", false,
                                "decompositionEnabled", true,
                                "subQuestionEnabled", true
                        ))))
                .andExpect(status().isOk());
        long sessionId = createConversation(token, tenantId, "复合问题测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "退款规则是什么？申请材料需要什么？"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("event:trace_stage");
        assertThat(body).contains("sub_question_split");
        assertThat(body).contains("event:delta");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM conversation_message WHERE session_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
                String.class,
                sessionId
        )).contains("退款规则需要在 7 日内提交申请")
                .contains("申请材料包括订单截图和退款原因说明");

        Long latestExchangeId = jdbcTemplate.queryForObject(
                "SELECT id FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                sessionId
        );
        assertThat(latestExchangeId).isNotNull();
        List<Integer> subQuestionNos = jdbcTemplate.queryForList(
                "SELECT DISTINCT sub_question_no FROM retrieval_trace WHERE exchange_id = ? ORDER BY sub_question_no ASC",
                Integer.class,
                latestExchangeId
        );
        assertThat(subQuestionNos).contains(1, 2);
    }

    @Test
    void shouldApplyMetadataFiltersToRagConversationFlow() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "过滤问答知识库", "published");
        long afterSaleDomainId = createKnowledgeDomain(token, tenantId, "after_sale", "售后域");
        long logisticsDomainId = createKnowledgeDomain(token, tenantId, "logistics", "物流域");
        long markdownProfileId = createChunkingProfile(token, tenantId, "md-heading", "Markdown Heading", "markdown_heading", false);
        long slideProfileId = createChunkingProfile(token, tenantId, "slide-section", "Slide Section", "slide_section", false);

        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "refund-guide.txt",
                "退款需在7日内提交申请，并提供订单截图。",
                "售后",
                List.of("refund", "priority"),
                afterSaleDomainId,
                markdownProfileId
        );
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "shipping-guide.txt",
                "配送时效为48小时内出库，支持物流催办。",
                "物流",
                List.of("shipping"),
                logisticsDomainId,
                slideProfileId
        );
        long sessionId = createConversation(token, tenantId, "过滤问答测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "退款规则是什么？",
                                "knowledgeBaseId", knowledgeBaseId,
                                "ragOptions", Map.of(
                                        "knowledgeDomainId", afterSaleDomainId,
                                        "chunkingProfileId", markdownProfileId,
                                        "category", "售后",
                                        "tags", List.of("refund")
                                )
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("退款需在7日内提交申请");
        assertThat(body).doesNotContain("配送时效为48小时内出库");
        assertThat(body).contains("event:reference");

        Long referencedDocumentId = jdbcTemplate.queryForObject(
                "SELECT document_id FROM conversation_reference ORDER BY id DESC LIMIT 1",
                Long.class
        );
        assertThat(referencedDocumentId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM knowledge_document WHERE id = ?",
                String.class,
                referencedDocumentId
        )).isEqualTo("refund-guide.txt");
    }

    @Test
    void shouldNormalizeOutOfRangeCitationWhileKeepingRealReferences() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "引用归一化知识库", "published");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "refund-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "引用归一化测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "请回答这个越界引用退款规则问题",
                                "knowledgeBaseId", knowledgeBaseId
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("event:delta");
        assertThat(body).doesNotContain("[9]");
        assertThat(body).contains("event:reference");

        Integer latestOrdinal = jdbcTemplate.queryForObject(
                "SELECT ordinal FROM conversation_reference ORDER BY id DESC LIMIT 1",
                Integer.class
        );
        assertThat(latestOrdinal).isEqualTo(1);
        String assistantContent = jdbcTemplate.queryForObject(
                "SELECT content FROM conversation_message WHERE role = 'assistant' ORDER BY id DESC LIMIT 1",
                String.class
        );
        assertThat(assistantContent).contains("参考依据：[1]");
        assertThat(assistantContent).doesNotContain("[9]");
    }

    @Test
    void shouldPreferStructuredDocumentWhenQuestionMatchesTitleAndSection() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "结构排序知识库", "published");
        long markdownProfileId = createChunkingProfile(token, tenantId, "md-structure", "Markdown Structure", "markdown_heading", false);
        String sharedBody = """
                # 办理说明
                所有售后申请都通过统一入口提交，并由客服审核处理。

                ## 处理规则
                用户需根据页面指引提交材料，系统会自动分配对应工单。
                """;

        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "退款规则手册.md",
                sharedBody,
                null,
                List.of(),
                null,
                markdownProfileId
        );
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "配送规则手册.md",
                sharedBody,
                null,
                List.of(),
                null,
                markdownProfileId
        );
        long sessionId = createConversation(token, tenantId, "结构排序测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("退款规则手册.md");
        Integer topReferenceDocumentCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM conversation_reference
                        WHERE exchange_id = (
                            SELECT id FROM conversation_exchange WHERE session_id = ? ORDER BY id DESC LIMIT 1
                        )
                          AND ordinal = 1
                          AND title = '退款规则手册.md'
                        """,
                Integer.class,
                sessionId
        );
        assertThat(topReferenceDocumentCount).isEqualTo(1);
    }

    @Test
    void shouldAnswerRagQuestionFromPdfDocument() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "PDF 场景知识库", "published");
        uploadAndProcessBinaryKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "sample.pdf",
                "application/pdf",
                getClass().getResourceAsStream("/documents/sample.pdf").readAllBytes()
        );
        long sessionId = createConversation(token, tenantId, "PDF 场景测试", knowledgeBaseId).path("data").path("id").asLong();

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "pdf sample guide 中提到什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("pdf sample guide");
        assertThat(body).contains("event:reference");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM conversation_reference ORDER BY id DESC LIMIT 1",
                String.class
        )).isEqualTo("sample.pdf");
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
        uploadAndProcessBinaryKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                fileName,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8),
                null,
                List.of(),
                null,
                null
        );
    }

    private void uploadAndProcessKnowledgeDocument(
            String token,
            long tenantId,
            long knowledgeBaseId,
            String fileName,
            String content,
            String category,
            List<String> tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) throws Exception {
        uploadAndProcessBinaryKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                fileName,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8),
                category,
                tags,
                knowledgeDomainId,
                chunkingProfileId
        );
    }

    private void uploadAndProcessBinaryKnowledgeDocument(
            String token,
            long tenantId,
            long knowledgeBaseId,
            String fileName,
            String contentType,
            byte[] content
    ) throws Exception {
        uploadAndProcessBinaryKnowledgeDocument(token, tenantId, knowledgeBaseId, fileName, contentType, content, null, List.of(), null, null);
    }

    private void uploadAndProcessBinaryKnowledgeDocument(
            String token,
            long tenantId,
            long knowledgeBaseId,
            String fileName,
            String contentType,
            byte[] content,
            String category,
            List<String> tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                content
        );
        var request = multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .file(file)
                .param("title", fileName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-Id", tenantId);
        if (category != null) {
            request.param("category", category);
        }
        if (tags != null && !tags.isEmpty()) {
            request.param("tags", String.join(",", tags));
        }
        if (knowledgeDomainId != null) {
            request.param("knowledgeDomainId", String.valueOf(knowledgeDomainId));
        }
        if (chunkingProfileId != null) {
            request.param("chunkingProfileId", String.valueOf(chunkingProfileId));
        }
        MvcResult uploadResponse = mockMvc.perform(request)
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

    private long createKnowledgeDomain(String token, long tenantId, String code, String name) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/admin/knowledge-domains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "name", name,
                                "description", name + "描述"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private long createChunkingProfile(String token, long tenantId, String code, String name, String strategy, boolean isDefault) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/admin/chunking-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "name", name,
                                "strategy", strategy,
                                "isDefault", isDefault,
                                "config", Map.of()
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
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
