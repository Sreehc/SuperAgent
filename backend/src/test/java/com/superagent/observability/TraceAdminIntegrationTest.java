package com.superagent.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.chat.TestChatModelClientConfiguration;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import com.superagent.rag.TestRerankClientConfiguration;
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
@Import({TestEmbeddingClientConfiguration.class, TestChatModelClientConfiguration.class, TestRerankClientConfiguration.class})
class TraceAdminIntegrationTest {

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
    void shouldAllowAdminToListAndInspectTraces() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "perQuestionEvidenceCharLimit", 2800,
                                "totalEvidenceCharLimit", 8400,
                                "maxEvidenceContentChars", 1500,
                                "answerConfidenceThreshold", 0.61d,
                                "noEvidenceMinResults", 1,
                                "forceCitationEnabled", true
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "trace-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());

        MvcResult listResponse = mockMvc.perform(get("/api/v1/admin/traces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listJson = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(listJson.path("data").path("items").isArray()).isTrue();
        assertThat(listJson.path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);

        MvcResult detailResponse = mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detailJson = objectMapper.readTree(detailResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(detailJson.path("data").path("stages").isArray()).isTrue();
        assertThat(detailJson.path("data").path("modelCalls").isArray()).isTrue();
        assertThat(detailJson.path("data").path("retrievals").isArray()).isTrue();
        assertThat(detailJson.path("data").path("modelCalls").get(0).path("provider").asText()).isEqualTo("test-provider");
        assertThat(detailJson.path("data").path("reranks").get(0).path("provider").asText()).isEqualTo("test-rerank-provider");
        assertThat(detailJson.path("data").path("retrievals").get(0).path("latencyMs").isNumber()).isTrue();
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("perQuestionEvidenceCharLimit").asInt()).isEqualTo(2800);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("totalEvidenceCharLimit").asInt()).isEqualTo(8400);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("maxEvidenceContentChars").asInt()).isEqualTo(1500);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("answerConfidenceThreshold").asDouble()).isEqualTo(0.61d);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("noEvidenceMinResults").asInt()).isEqualTo(1);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("forceCitationEnabled").asBoolean()).isTrue();
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("diversityLimited").asBoolean()).isFalse();
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("citationAppended").asBoolean()).isFalse();
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("answerConfidenceThreshold").asDouble()).isEqualTo(0.61d);
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("answerConfidenceScore").asDouble()).isGreaterThan(0.61d);
        assertThat(detailJson.toString()).doesNotContain("sk-");
        assertThat(detailJson.toString()).doesNotContain("rk-");
        assertThat(detailJson.toString()).doesNotContain("Authorization");

        JsonNode retrievalList = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/retrievals")
                        .param("exchangeId", String.valueOf(exchangeId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(retrievalList.path("data").path("items").isArray()).isTrue();
        assertThat(retrievalList.path("data").path("items").get(0).path("items").isArray()).isTrue();
        assertThat(retrievalList.path("data").path("items").get(0).path("latencyMs").isNumber()).isTrue();

        JsonNode modelCallList = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/model-calls")
                        .param("exchangeId", String.valueOf(exchangeId))
                        .param("provider", "test-provider")
                        .param("model", "test-chat-model")
                        .param("status", "success")
                        .param("callType", "chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(modelCallList.path("data").path("items").isArray()).isTrue();
        assertThat(modelCallList.path("data").path("items").get(0).path("provider").asText()).isEqualTo("test-provider");
        assertThat(modelCallList.path("data").path("items").get(0).path("callType").asText()).isEqualTo("chat");

        JsonNode rerankList = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/reranks")
                        .param("exchangeId", String.valueOf(exchangeId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(rerankList.path("data").path("items").isArray()).isTrue();
        assertThat(rerankList.path("data").path("items").get(0).path("provider").asText()).isEqualTo("test-rerank-provider");
    }

    @Test
    void shouldExposeFallbackReasonWhenTraceFallsBackToNoEvidence() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "noEvidenceMinResults", 2,
                                "forceCitationEnabled", true
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Fallback Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "trace-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "Fallback Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());

        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(detailJson.path("data").path("modelCalls").get(0).path("provider").asText()).isEqualTo("system");
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("fallbackReason").asText())
                .isEqualTo("insufficient_evidence_results");
    }

    @Test
    void shouldExposeLowConfidenceFallbackReasonWhenConfidenceThresholdRejectsAnswer() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "noEvidenceMinResults", 1,
                                "answerConfidenceThreshold", 0.99d
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Low Confidence Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "trace-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "Low Confidence Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());

        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(detailJson.path("data").path("modelCalls").get(0).path("provider").asText()).isEqualTo("system");
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("fallbackReason").asText())
                .isEqualTo("low_answer_confidence");
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("answerConfidenceThreshold").asDouble())
                .isEqualTo(0.99d);
    }

    @Test
    void shouldExposeHighRiskGuardMetadataWhenQuestionNeedsStricterGrounding() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "noEvidenceMinResults", 1,
                                "answerConfidenceThreshold", 0.55d,
                                "forceCitationEnabled", false
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "High Risk Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "trace-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "High Risk Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "这个退款规则在法律上一定合法吗？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());

        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(detailJson.path("data").path("modelCalls").get(0).path("provider").asText()).isEqualTo("system");
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("highRiskGuardApplied").asBoolean()).isTrue();
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("questionRiskLevel").asText()).isEqualTo("high");
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("questionRiskReasons").toString()).contains("legal");
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("baseNoEvidenceMinResults").asInt()).isEqualTo(1);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("noEvidenceMinResults").asInt()).isEqualTo(2);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("baseForceCitationEnabled").asBoolean()).isFalse();
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("forceCitationEnabled").asBoolean()).isTrue();
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("highRiskGuardApplied").asBoolean()).isTrue();
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("questionRiskLevel").asText()).isEqualTo("high");
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("fallbackReason").asText())
                .isEqualTo("insufficient_evidence_results");
    }

    @Test
    void shouldExposeEvidenceContentTrimWhenLongChunkIsCapped() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "noEvidenceMinResults", 1,
                                "forceCitationEnabled", true,
                                "maxEvidenceContentChars", 24
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Trim Trace 知识库");
        uploadAndProcessKnowledgeDocument(
                token,
                tenantId,
                knowledgeBaseId,
                "trace-guide.txt",
                "退款需在7日内提交申请，并提供订单截图、售后工单编号、支付流水和补充说明材料。"
        );
        long sessionId = createConversation(token, tenantId, "Trim Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());

        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("maxEvidenceContentChars").asInt()).isEqualTo(24);
        assertThat(detailJson.path("data").path("retrievals").get(0).path("filters").path("contentTrimmedCount").asInt()).isGreaterThan(0);
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("maxEvidenceContentChars").asInt()).isEqualTo(24);
    }

    @Test
    void shouldExposeNeighborExpandedEvidenceInTrace() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "forceCitationEnabled", true,
                                "noEvidenceMinResults", 1,
                                "vectorTopK", 1,
                                "keywordTopK", 1,
                                "candidateTopK", 1,
                                "neighborExpansionEnabled", true,
                                "neighborWindow", 1,
                                "evidenceLimit", 4,
                                "maxChunksPerDocument", 4
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Neighbor Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "neighbor-trace.txt", buildNeighborExpansionDocument());
        long sessionId = createConversation(token, tenantId, "Neighbor Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        long exchangeId = extractExchangeId(awaitStreamBody(streamResult, "event:done"));
        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        JsonNode rrfRetrieval = findRetrievalByChannel(detailJson.path("data").path("retrievals"), "rrf");
        assertThat(rrfRetrieval.isMissingNode()).isFalse();
        assertThat(rrfRetrieval.path("filters").path("neighborExpansionEnabled").asBoolean()).isTrue();
        assertThat(rrfRetrieval.path("filters").path("neighborExpanded").asBoolean()).isTrue();
        assertThat(rrfRetrieval.path("items").isArray()).isTrue();
        assertThat(rrfRetrieval.path("items"))
                .anyMatch(item -> item.path("metadata").path("neighborExpanded").asBoolean());
        assertThat(rrfRetrieval.path("items"))
                .anyMatch(item -> "neighbor".equals(item.path("metadata").path("channel").asText()));
    }

    @Test
    void shouldFallbackToFilteredEvidenceWhenRerankThrowsException() throws Exception {
        JsonNode login = login("admin", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        mockMvc.perform(patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "noEvidenceMinResults", 1,
                                "forceCitationEnabled", true
                        ))))
                .andExpect(status().isOk());
        long knowledgeBaseId = createKnowledgeBase(token, tenantId, "Rerank Error Trace 知识库");
        uploadAndProcessKnowledgeDocument(token, tenantId, knowledgeBaseId, "trace-guide.txt", "退款需在7日内提交申请，并提供订单截图。");
        long sessionId = createConversation(token, tenantId, "Rerank Error Trace 对话", knowledgeBaseId);

        MvcResult streamResult = mockMvc.perform(post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "rerank异常退款规则是什么？"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStreamBody(streamResult, "event:done");
        assertThat(body).contains("event:reference");
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        long exchangeId = Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());
        String assistantContent = jdbcTemplate.queryForObject(
                "SELECT content FROM conversation_message WHERE role = 'assistant' ORDER BY id DESC LIMIT 1",
                String.class
        );
        assertThat(assistantContent).contains("退款规则包括在 7 日内提交申请并提供订单截图");

        JsonNode detailJson = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/traces/{exchangeId}", exchangeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(detailJson.path("data").path("modelCalls").get(0).path("provider").asText()).isEqualTo("test-provider");
        assertThat(detailJson.path("data").path("reranks").get(0).path("status").asText()).isEqualTo("error");
        assertThat(detailJson.path("data").path("reranks").get(0).path("metadata").path("fallbackReason").asText())
                .isEqualTo("rerank_error_used_filtered");
        assertThat(detailJson.path("data").path("reranks").get(0).path("errorMessage").asText())
                .contains("simulated rerank failure");
    }

    @Test
    void shouldForbidMemberFromAccessingTraceAdminApis() throws Exception {
        JsonNode login = login("member", "password123");
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        mockMvc.perform(get("/api/v1/admin/traces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/retrievals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/reranks")
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
        return objectMapper.readTree(response.getResponse().getContentAsString());
    }

    private long createKnowledgeBase(String token, long tenantId, String name) throws Exception {
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
                        .content(objectMapper.writeValueAsString(Map.of("status", "published"))))
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

    private long createConversation(String token, long tenantId, String title, long knowledgeBaseId) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "memoryStrategy", "SLIDING_WINDOW",
                                "knowledgeBaseId", knowledgeBaseId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long extractExchangeId(String body) {
        int exchangeIdStart = body.indexOf("\"exchangeId\":") + 13;
        return Long.parseLong(body.substring(exchangeIdStart, body.indexOf(",", exchangeIdStart)).trim());
    }

    private JsonNode findRetrievalByChannel(JsonNode retrievals, String channel) {
        for (JsonNode retrieval : retrievals) {
            if (channel.equals(retrieval.path("channel").asText())) {
                return retrieval;
            }
        }
        return objectMapper.getNodeFactory().missingNode();
    }

    private String buildNeighborExpansionDocument() {
        return String.join(
                "\n\n",
                repeatSentence("第一部分介绍售后受理背景、工单流转、客服登记、用户身份核验、订单状态校验和历史工单归档说明。", 42),
                repeatSentence("第二部分详细说明唯一退款规则锚点：退款规则锚点ZXQ-2026要求申请需在7日内提交，并提供订单截图、支付流水和售后工单编号。", 42),
                repeatSentence("第三部分补充退款审核材料、客服回访、到账时效、补充说明要求、异常单升级路径和财务复核说明。", 42)
        );
    }

    private String repeatSentence(String sentence, int times) {
        return (sentence + "\n").repeat(times);
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
