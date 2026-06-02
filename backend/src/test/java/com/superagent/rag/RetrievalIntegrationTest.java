package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.knowledge.messaging.DocumentTaskMessage;
import com.superagent.knowledge.service.DocumentProcessingService;
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
class RetrievalIntegrationTest {

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
    void shouldReturnVectorRetrievalResultsWithTenantAndKnowledgeBaseFiltering() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");

        long publishedKnowledgeBaseId = createKnowledgeBase(owner, "检索知识库", "published");
        long draftKnowledgeBaseId = createKnowledgeBase(owner, "草稿知识库", "draft");

        long documentId = uploadAndProcessDocument(owner, publishedKnowledgeBaseId, "shipping-guide.txt", "物流说明", "物流时效和配送规则");
        uploadAndProcessDocument(owner, draftKnowledgeBaseId, "draft-guide.txt", "草稿说明", "内部草稿知识");

        JsonNode retrievalResponse = objectMapper.readTree(mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "配送规则")
                        .param("knowledgeBaseId", String.valueOf(publishedKnowledgeBaseId))
                        .param("topK", "3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(retrievalResponse.path("data").path("items").size()).isGreaterThan(0);
        JsonNode firstItem = retrievalResponse.path("data").path("items").get(0);
        assertThat(firstItem.path("channel").asText()).isEqualTo("vector");
        assertThat(firstItem.path("documentId").asLong()).isEqualTo(documentId);
        assertThat(firstItem.path("knowledgeBaseId").asLong()).isEqualTo(publishedKnowledgeBaseId);
        assertThat(firstItem.path("score").asDouble()).isGreaterThan(0.0d);

        mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "内部草稿")
                        .param("knowledgeBaseId", String.valueOf(draftKnowledgeBaseId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isNotFound());
    }

    private long uploadAndProcessDocument(
            LoginSession owner,
            long knowledgeBaseId,
            String fileName,
            String title,
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
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long documentId = uploaded.path("data").path("id").asLong();
        long taskId = uploaded.path("data").path("taskId").asLong();
        documentProcessingService.process(new DocumentTaskMessage(owner.tenantId(), documentId, taskId, "test"));
        return documentId;
    }

    private long createKnowledgeBase(LoginSession owner, String name, String status) throws Exception {
        MvcResult createResponse = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
        return knowledgeBaseId;
    }

    private LoginSession login(String username, String password) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new LoginSession(
                json.path("data").path("accessToken").asText(),
                json.path("data").path("defaultTenant").path("id").asLong()
        );
    }

    private record LoginSession(String accessToken, long tenantId) {
    }
}
