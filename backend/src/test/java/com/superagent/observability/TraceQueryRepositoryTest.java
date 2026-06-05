package com.superagent.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.superagent.chat.TestChatModelClientConfiguration;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import com.superagent.observability.repository.TraceQueryRepository;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import com.superagent.rag.TestRerankClientConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import({TestEmbeddingClientConfiguration.class, TestChatModelClientConfiguration.class, TestRerankClientConfiguration.class})
class TraceQueryRepositoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private TraceQueryRepository traceQueryRepository;

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
    void shouldListRetrievalsDirectlyFromRepository() throws Exception {
        var login = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "password123"))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/settings/rag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rerankEnabled", true,
                                "perQuestionEvidenceCharLimit", 2800,
                                "totalEvidenceCharLimit", 8400,
                                "noEvidenceMinResults", 2,
                                "forceCitationEnabled", false
                        ))))
                .andReturn();

        long knowledgeBaseId = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Trace Repo", "description", "desc", "visibility", "tenant"))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "published"))))
                .andReturn();

        MockMultipartFile file = new MockMultipartFile("file", "trace-guide.txt", "text/plain", "退款需在7日内提交申请，并提供订单截图。".getBytes(StandardCharsets.UTF_8));
        var uploaded = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "trace-guide.txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        documentProcessingService.process(new DocumentTaskMessage(
                tenantId,
                uploaded.path("data").path("id").asLong(),
                uploaded.path("data").path("taskId").asLong(),
                "test"
        ));

        long sessionId = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Trace 对话", "memoryStrategy", "SLIDING_WINDOW", "knowledgeBaseId", knowledgeBaseId))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asLong();

        var streamResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "退款规则是什么？"))))
                .andReturn();
        long deadline = System.currentTimeMillis() + 5_000L;
        String body;
        do {
            body = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:done")) {
                break;
            }
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        long exchangeId = Long.parseLong(body.substring(body.indexOf("\"exchangeId\":") + 13, body.indexOf(",", body.indexOf("\"exchangeId\":") + 13)).trim());

        var retrievals = traceQueryRepository.listRetrievals(tenantId, exchangeId, null, 1, 20);
        assertThat(retrievals).isNotEmpty();
        assertThat(retrievals.getFirst().items()).isNotEmpty();
        assertThat(retrievals.getFirst().latencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(retrievals.getFirst().filters())
                .containsEntry("originalQuestion", "退款规则是什么？")
                .containsEntry("rewrittenQuestion", "退款规则是什么？")
                .containsEntry("answerMode", "single_question")
                .containsEntry("queryUnderstandingSource", "provider_unavailable")
                .containsEntry("versionConsistencyEnabled", true)
                .containsEntry("neighborExpansionEnabled", true)
                .containsEntry("neighborWindow", 1)
                .containsEntry("perQuestionEvidenceCharLimit", 2800)
                .containsEntry("totalEvidenceCharLimit", 8400)
                .containsEntry("answerConfidenceThreshold", 0.55d)
                .containsEntry("noEvidenceMinResults", 2)
                .containsEntry("forceCitationEnabled", false)
                .containsEntry("hybridRetrievalEnabled", true)
                .containsEntry("diversityLimited", false)
                .containsEntry("belowThresholdFilteredCount", 0)
                .containsEntry("perDocumentTrimmedCount", 0)
                .containsEntry("charBudgetTrimmedCount", 0)
                .containsEntry("evidenceLimitTrimmedCount", 0);
        assertThat(retrievals.getFirst().items().getFirst().metadata())
                .containsEntry("documentTitle", "trace-guide.txt")
                .containsEntry("activeVersionNo", 1)
                .containsEntry("chunkVersionNo", 1);
    }

    @Test
    void shouldExposeMetadataFiltersInRetrievalTrace() throws Exception {
        var login = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "password123"))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        String token = login.path("data").path("accessToken").asText();
        long tenantId = login.path("data").path("defaultTenant").path("id").asLong();
        long domainId = createKnowledgeDomain(token, tenantId, "after_sale", "售后域");
        long profileId = createChunkingProfile(token, tenantId, "md-heading", "Markdown Heading", "markdown_heading", false);

        long knowledgeBaseId = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "过滤 Trace Repo", "description", "desc", "visibility", "tenant"))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "published"))))
                .andReturn();

        MockMultipartFile file = new MockMultipartFile("file", "trace-filter-guide.txt", "text/plain", "退款需在7日内提交申请，并提供订单截图。".getBytes(StandardCharsets.UTF_8));
        var uploaded = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "trace-filter-guide.txt")
                        .param("category", "售后")
                        .param("tags", "refund,priority")
                        .param("knowledgeDomainId", String.valueOf(domainId))
                        .param("chunkingProfileId", String.valueOf(profileId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        documentProcessingService.process(new DocumentTaskMessage(
                tenantId,
                uploaded.path("data").path("id").asLong(),
                uploaded.path("data").path("taskId").asLong(),
                "test"
        ));

        long sessionId = objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Trace 过滤对话", "memoryStrategy", "SLIDING_WINDOW", "knowledgeBaseId", knowledgeBaseId))))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asLong();

        var streamResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/conversations/{sessionId}/messages/stream", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Tenant-Id", tenantId)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "退款规则是什么？",
                                "ragOptions", Map.of(
                                        "knowledgeDomainId", domainId,
                                        "chunkingProfileId", profileId,
                                        "category", "售后",
                                        "tags", List.of("refund")
                                )
                        ))))
                .andReturn();
        long deadline = System.currentTimeMillis() + 5_000L;
        String body;
        do {
            body = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:done")) {
                break;
            }
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        long exchangeId = Long.parseLong(body.substring(body.indexOf("\"exchangeId\":") + 13, body.indexOf(",", body.indexOf("\"exchangeId\":") + 13)).trim());

        var retrievals = traceQueryRepository.listRetrievals(tenantId, exchangeId, null, 1, 20);
        assertThat(retrievals).isNotEmpty();
        assertThat(((Number) retrievals.getFirst().filters().get("knowledgeDomainId")).longValue()).isEqualTo(domainId);
        assertThat(((Number) retrievals.getFirst().filters().get("chunkingProfileId")).longValue()).isEqualTo(profileId);
        assertThat(retrievals.getFirst().filters()).containsEntry("category", "售后");
        assertThat(retrievals.getFirst().filters().get("tags")).isEqualTo(List.of("refund"));
        assertThat(retrievals.getFirst().items().getFirst().metadata())
                .containsEntry("category", "售后");
        assertThat(((Number) retrievals.getFirst().items().getFirst().metadata().get("knowledgeDomainId")).longValue()).isEqualTo(domainId);
        assertThat(((Number) retrievals.getFirst().items().getFirst().metadata().get("chunkingProfileId")).longValue()).isEqualTo(profileId);
        assertThat(retrievals.getFirst().items().getFirst().metadata())
                .containsEntry("category", "售后");
    }

    private long createKnowledgeDomain(String token, long tenantId, String code, String name) throws Exception {
        MvcResult response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/knowledge-domains")
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
        MvcResult response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/chunking-profiles")
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
}
