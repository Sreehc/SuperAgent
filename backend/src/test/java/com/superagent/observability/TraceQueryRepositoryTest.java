package com.superagent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.chat.TestChatModelClientConfiguration;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
import com.superagent.observability.repository.TraceQueryRepository;
import com.superagent.rag.TestEmbeddingClientConfiguration;
import com.superagent.rag.TestRerankClientConfiguration;
import java.nio.charset.StandardCharsets;
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
                        .content(objectMapper.writeValueAsString(Map.of("rerankEnabled", true))))
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
        assertThat(retrievals.getFirst().filters())
                .containsEntry("originalQuestion", "退款规则是什么？")
                .containsEntry("rewrittenQuestion", "退款规则是什么？")
                .containsEntry("hybridRetrievalEnabled", true);
        assertThat(retrievals.getFirst().items().getFirst().metadata())
                .containsEntry("documentTitle", "trace-guide.txt");
    }
}
