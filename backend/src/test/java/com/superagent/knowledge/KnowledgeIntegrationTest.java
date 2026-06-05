package com.superagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.common.exception.AppException;
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
class KnowledgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @BeforeEach
    void cleanKnowledgeTables() {
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
    void shouldCreateListUpdateAndDeleteKnowledgeBase() throws Exception {
        LoginSession owner = login("admin", "password123");

        MvcResult createResponse = mockMvc.perform(post("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "售后知识库",
                                "description", "售后说明",
                                "visibility", "tenant"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long knowledgeBaseId = created.path("data").path("id").asLong();

        MvcResult listResponse = mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(listed.path("data").path("items").get(0).path("name").asText()).isEqualTo("售后知识库");

        MvcResult patchResponse = mockMvc.perform(patch("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "更新后的描述",
                                "status", "published"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode patched = objectMapper.readTree(patchResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(patched.path("data").path("status").asText()).isEqualTo("published");

        MvcResult detailResponse = mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(detail.path("data").path("status").asText()).isEqualTo("published");

        MvcResult deleteResponse = mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(deleteResponse.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("deleted").asBoolean()).isTrue();
    }

    @Test
    void shouldAllowMemberToViewPublishedKnowledgeBaseOnly() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");

        long publishedId = createKnowledgeBase(owner, "成员可见", "published");
        long draftId = createKnowledgeBase(owner, "成员不可见", "draft");

        MvcResult listResponse = mockMvc.perform(get("/api/v1/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(listed.path("data").path("items").toString()).contains("成员可见");
        assertThat(listed.path("data").path("items").toString()).doesNotContain("成员不可见");

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", publishedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUploadDocumentAndCreateInitialTask() throws Exception {
        LoginSession owner = login("admin", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "文档上传测试", "published");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "refund-policy.md",
                "text/markdown",
                "# refund policy".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "退款规则")
                        .param("category", "售后")
                        .param("tags", "refund,policy")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long documentId = uploaded.path("data").path("id").asLong();
        long taskId = uploaded.path("data").path("taskId").asLong();
        assertThat(uploaded.path("data").path("status").asText()).isEqualTo("uploaded");

        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE id = ? AND status = 'uploaded'",
                Integer.class,
                documentId
        );
        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_task WHERE id = ? AND task_type = 'parse' AND status = 'pending'",
                Integer.class,
                taskId
        );
        assertThat(documentCount).isEqualTo(1);
        assertThat(taskCount).isEqualTo(1);

        MvcResult listResponse = mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = objectMapper.readTree(listResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(listed.path("data").path("items").get(0).path("title").asText()).isEqualTo("退款规则");
    }

    @Test
    void shouldRejectMemberUploadDocument() throws Exception {
        LoginSession owner = login("admin", "password123");
        LoginSession member = login("member", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "成员上传受限", "published");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "refund-policy.md",
                "text/markdown",
                "# refund policy".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + member.accessToken())
                        .header("X-Tenant-Id", member.tenantId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldProcessDocumentAndExposeChunksTasksAndReprocess() throws Exception {
        LoginSession owner = login("admin", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "文档处理测试", "published");
        long domainId = createKnowledgeDomain(owner, "ops", "运维域");
        long markdownProfileId = createChunkingProfile(owner, "md-heading", "Markdown 标题切块", "markdown_heading", true);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.txt",
                "text/markdown",
                ("# 第一章\n第一段说明。\n\n## 第二章\n第二段说明，包含更多文字。\n### 第三章\n第三段说明。").getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "处理链路文档")
                        .param("knowledgeDomainId", String.valueOf(domainId))
                        .param("chunkingProfileId", String.valueOf(markdownProfileId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long documentId = uploaded.path("data").path("id").asLong();
        long parseTaskId = uploaded.path("data").path("taskId").asLong();
        assertThat(uploaded.path("data").path("knowledgeDomainId").asLong()).isEqualTo(domainId);
        assertThat(uploaded.path("data").path("chunkingProfileId").asLong()).isEqualTo(markdownProfileId);
        assertThat(uploaded.path("data").path("activeVersionNo").asInt()).isEqualTo(1);

        JsonNode documentDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(documentDetail.path("data").path("title").asText()).isEqualTo("处理链路文档");
        assertThat(documentDetail.path("data").path("status").asText()).isEqualTo("uploaded");
        assertThat(documentDetail.path("data").path("knowledgeDomainId").asLong()).isEqualTo(domainId);
        assertThat(documentDetail.path("data").path("chunkingProfileId").asLong()).isEqualTo(markdownProfileId);
        assertThat(documentDetail.path("data").path("activeVersionNo").asInt()).isEqualTo(1);

        documentProcessingService.process(new DocumentTaskMessage(owner.tenantId(), documentId, parseTaskId, "test"));

        JsonNode chunks = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/chunks", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        JsonNode processedDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(processedDetail.path("data").path("parsedText").asText()).contains("第一段说明");
        assertThat(processedDetail.path("data").path("activeVersionNo").asInt()).isEqualTo(1);
        assertThat(chunks.path("data").path("total").asInt()).isGreaterThan(0);
        assertThat(chunks.path("data").path("items").get(0).path("content").asText()).contains("说明");
        assertThat(chunks.path("data").path("items").get(0).path("sectionTitle").asText()).isEqualTo("第一章");
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("strategy").asText()).isEqualTo("markdown_heading");
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("blockType").asText()).isEqualTo("heading_section");
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("headingLevel").asInt()).isEqualTo(1);
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("headingPath").get(0).asText()).isEqualTo("第一章");
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("chunkingProfileId").asLong()).isEqualTo(markdownProfileId);
        assertThat(chunks.path("data").path("items").get(0).path("metadata").path("versionNo").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT section_title FROM document_chunk WHERE document_id = ? AND chunk_no = 1",
                String.class,
                documentId
        )).isEqualTo("第一章");

        JsonNode tasks = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/tasks", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(tasks.path("data").toString()).contains("parse");
        assertThat(tasks.path("data").toString()).contains("chunk");
        assertThat(tasks.path("data").toString()).contains("embed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM knowledge_document WHERE id = ?",
                String.class,
                documentId
        )).isEqualTo("ready");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_embedding WHERE document_id = ?",
                Integer.class,
                documentId
        )).isGreaterThan(0);
        JsonNode versions = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/versions", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(versions.path("data").get(0).path("versionNo").asInt()).isEqualTo(1);
        assertThat(versions.path("data").get(0).path("chunkingProfileId").asLong()).isEqualTo(markdownProfileId);
        assertThat(versions.path("data").get(0).path("status").asText()).isEqualTo("ready");
        assertThat(versions.path("data").get(0).path("chunkCount").asInt()).isGreaterThan(0);
        JsonNode initialGraph = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/graph", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(initialGraph.path("data").path("nodes").isArray()).isTrue();
        assertThat(initialGraph.path("data").path("edges").isArray()).isTrue();
        assertThat(initialGraph.path("data").path("versionGraphSyncStatus").asText()).isEqualTo("ready");
        assertThat(initialGraph.toString()).contains("KnowledgeBase");
        assertThat(initialGraph.toString()).contains("Chunk");

        int originalChunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        );
        long slideProfileId = createChunkingProfile(owner, "slide-section", "Slide 分段切块", "slide_section", false);

        MvcResult reprocessResponse = mockMvc.perform(post("/api/v1/documents/{documentId}/reprocess", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "重试处理",
                                "chunkingProfileId", slideProfileId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reprocessed = objectMapper.readTree(reprocessResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long reprocessTaskId = reprocessed.path("data").path("taskId").asLong();

        documentProcessingService.process(new DocumentTaskMessage(owner.tenantId(), documentId, reprocessTaskId, "test_reprocess"));

        int processedChunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        );
        assertThat(processedChunkCount).isEqualTo(originalChunkCount);
        JsonNode reprocessedDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(reprocessedDetail.path("data").path("activeVersionNo").asInt()).isEqualTo(2);
        JsonNode versionListAfterReprocess = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/versions", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(versionListAfterReprocess.path("data").get(0).path("versionNo").asInt()).isEqualTo(2);
        assertThat(versionListAfterReprocess.path("data").get(0).path("chunkingProfileId").asLong()).isEqualTo(slideProfileId);
        assertThat(versionListAfterReprocess.path("data").get(0).path("status").asText()).isEqualTo("ready");
        Integer duplicateChunkNoCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT chunk_no
                    FROM document_chunk
                    WHERE document_id = ?
                    GROUP BY chunk_no
                    HAVING COUNT(*) > 1
                ) duplicated
                """, Integer.class, documentId);
        assertThat(duplicateChunkNoCount).isEqualTo(0);
        Integer embeddingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_embedding WHERE document_id = ?",
                Integer.class,
                documentId
        );
        assertThat(embeddingCount).isEqualTo(processedChunkCount);
        JsonNode chunksAfterReprocess = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/chunks", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("metadata").path("strategy").asText()).isEqualTo("slide_section");
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("metadata").path("versionNo").asInt()).isEqualTo(2);
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("metadata").path("chunkingProfileId").asLong()).isEqualTo(slideProfileId);
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("sectionTitle").asText()).isEqualTo("第一章");
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("metadata").path("blockType").asText()).isEqualTo("slide_section");
        assertThat(chunksAfterReprocess.path("data").path("items").get(0).path("metadata").path("headingLevel").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT section_title FROM document_chunk WHERE document_id = ? AND chunk_no = 1",
                String.class,
                documentId
        )).isEqualTo("第一章");
        JsonNode rebuiltGraph = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/{documentId}/graph/rebuild", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(rebuiltGraph.path("data").path("versionNo").asInt()).isEqualTo(2);
        assertThat(rebuiltGraph.path("data").path("documentGraphSyncStatus").asText()).isEqualTo("ready");
        assertThat(rebuiltGraph.path("data").path("nodes").isArray()).isTrue();
        assertThat(rebuiltGraph.path("data").path("edges").isArray()).isTrue();
        assertThat(rebuiltGraph.toString()).contains("MENTIONS");

        int parseAttemptCount = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM document_task WHERE id = ?",
                Integer.class,
                parseTaskId
        );
        documentProcessingService.process(new DocumentTaskMessage(owner.tenantId(), documentId, parseTaskId, "duplicate_delivery"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM document_task WHERE id = ?",
                Integer.class,
                parseTaskId
        )).isEqualTo(parseAttemptCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        )).isEqualTo(processedChunkCount);
    }

    @Test
    void shouldRecordFailureReasonInTasksWhenParsingFails() throws Exception {
        LoginSession owner = login("admin", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "失败处理测试", "published");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.txt",
                "text/plain",
                new byte[0]
        );

        MvcResult uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "空文档")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        assertThat(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Document file is required");
    }

    @Test
    void shouldRecordFailureReasonInTasksForUnsupportedParsedContent() throws Exception {
        LoginSession owner = login("admin", "password123");
        long knowledgeBaseId = createKnowledgeBase(owner, "失败日志测试", "published");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.md",
                "text/markdown",
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResponse = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .file(file)
                        .param("title", "空白文档")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResponse.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long documentId = uploaded.path("data").path("id").asLong();
        long parseTaskId = uploaded.path("data").path("taskId").asLong();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                documentProcessingService.process(new DocumentTaskMessage(owner.tenantId(), documentId, parseTaskId, "failure_test")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Document content is empty after parsing");

        JsonNode documentDetail = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(documentDetail.path("data").path("status").asText()).isEqualTo("failed");
        assertThat(documentDetail.path("data").path("errorMessage").asText()).contains("empty after parsing");

        JsonNode tasks = objectMapper.readTree(mockMvc.perform(get("/api/v1/documents/{documentId}/tasks", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .header("X-Tenant-Id", owner.tenantId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(tasks.path("data").toString()).contains("failed");
        assertThat(tasks.path("data").toString()).contains("Document content is empty after parsing");
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
