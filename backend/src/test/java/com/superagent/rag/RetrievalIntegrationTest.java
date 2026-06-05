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
import java.util.List;
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
        assertThat(firstItem.path("metadata").path("activeVersionNo").asInt()).isEqualTo(1);
        assertThat(firstItem.path("metadata").path("chunkVersionNo").asInt()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "内部草稿")
                        .param("knowledgeBaseId", String.valueOf(draftKnowledgeBaseId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExcludeChunksWhenChunkVersionDoesNotMatchActiveVersion() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "版本过滤知识库", "published");
        long documentId = uploadAndProcessDocument(owner, knowledgeBaseId, "versioned-guide.txt", "版本说明", "退款规则需要订单截图和七日内申请");

        jdbcTemplate.update("""
                        UPDATE document_chunk
                        SET metadata = jsonb_set(metadata, '{versionNo}', '0'::jsonb, true)
                        WHERE document_id = ?
                        """,
                documentId
        );

        JsonNode retrievalResponse = objectMapper.readTree(mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "退款规则")
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("topK", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(retrievalResponse.path("data").path("items")).isEmpty();
    }

    @Test
    void shouldFilterRetrievalsByDocumentMetadata() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "元数据过滤知识库", "published");
        long domainA = createKnowledgeDomain(owner, "after_sale", "售后域");
        long domainB = createKnowledgeDomain(owner, "logistics", "物流域");
        long profileA = createChunkingProfile(owner, "md-heading", "Markdown Heading", "markdown_heading", false);
        long profileB = createChunkingProfile(owner, "slide-section", "Slide Section", "slide_section", false);

        uploadAndProcessDocument(
                owner,
                knowledgeBaseId,
                "refund-guide.txt",
                "退款规则",
                "退款规则需要订单截图和七日内申请",
                "售后",
                List.of("refund", "priority"),
                domainA,
                profileA
        );
        uploadAndProcessDocument(
                owner,
                knowledgeBaseId,
                "shipping-guide.txt",
                "配送规则",
                "配送规则说明与物流时效",
                "物流",
                List.of("shipping"),
                domainB,
                profileB
        );

        JsonNode retrievalResponse = objectMapper.readTree(mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "规则")
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("knowledgeDomainId", String.valueOf(domainA))
                        .param("chunkingProfileId", String.valueOf(profileA))
                        .param("category", "售后")
                        .param("tags", "refund")
                        .param("topK", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(retrievalResponse.path("data").path("items")).hasSize(1);
        JsonNode firstItem = retrievalResponse.path("data").path("items").get(0);
        assertThat(firstItem.path("documentTitle").asText()).isEqualTo("退款规则");
        assertThat(firstItem.path("metadata").path("knowledgeDomainId").asLong()).isEqualTo(domainA);
        assertThat(firstItem.path("metadata").path("chunkingProfileId").asLong()).isEqualTo(profileA);
        assertThat(firstItem.path("metadata").path("category").asText()).isEqualTo("售后");
        assertThat(firstItem.path("metadata").path("tags").toString()).contains("refund");
    }

    @Test
    void shouldRetrieveByDocumentTitleAndSectionTitleWhenContentIsShared() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "结构检索知识库", "published");
        long markdownProfileId = createChunkingProfile(owner, "md-structure", "Markdown Structure", "markdown_heading", false);
        String sharedBody = """
                # 办理说明
                所有售后申请都通过统一入口提交，并由客服审核处理。

                ## 处理规则
                用户需根据页面指引提交材料，系统会自动分配对应工单。
                """;

        uploadAndProcessDocument(
                owner,
                knowledgeBaseId,
                "refund-structure.md",
                "退款规则手册",
                sharedBody,
                null,
                List.of(),
                null,
                markdownProfileId
        );
        uploadAndProcessDocument(
                owner,
                knowledgeBaseId,
                "shipping-structure.md",
                "配送规则手册",
                sharedBody,
                null,
                List.of(),
                null,
                markdownProfileId
        );

        JsonNode titleRetrieval = objectMapper.readTree(mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "退款规则")
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("topK", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(titleRetrieval.path("data").path("items")).isNotEmpty();
        assertThat(titleRetrieval.path("data").path("items").get(0).path("documentTitle").asText()).isEqualTo("退款规则手册");

        JsonNode sectionRetrieval = objectMapper.readTree(mockMvc.perform(get("/api/v1/retrievals")
                        .param("query", "处理规则")
                        .param("knowledgeBaseId", String.valueOf(knowledgeBaseId))
                        .param("topK", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(sectionRetrieval.path("data").path("items")).isNotEmpty();
        assertThat(sectionRetrieval.path("data").path("items").get(0).path("sectionTitle").asText()).isEqualTo("处理规则");
        assertThat(sectionRetrieval.path("data").path("items").get(0).path("metadata").path("blockType").asText()).isEqualTo("heading_section");
    }

    private long uploadAndProcessDocument(
            LoginSession owner,
            long knowledgeBaseId,
            String fileName,
            String title,
            String content
    ) throws Exception {
        return uploadAndProcessDocument(owner, knowledgeBaseId, fileName, title, content, null, List.of(), null, null);
    }

    private long uploadAndProcessDocument(
            LoginSession owner,
            long knowledgeBaseId,
            String fileName,
            String title,
            String content,
            String category,
            List<String> tags,
            Long knowledgeDomainId,
            Long chunkingProfileId
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );
        var request = multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .file(file)
                .param("title", title)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                .header("X-Tenant-Id", owner.tenantId());
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

    private long createKnowledgeDomain(LoginSession owner, String code, String name) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/admin/knowledge-domains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
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

    private long createChunkingProfile(LoginSession owner, String code, String name, String strategy, boolean isDefault) throws Exception {
        MvcResult response = mockMvc.perform(post("/api/v1/admin/chunking-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
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
