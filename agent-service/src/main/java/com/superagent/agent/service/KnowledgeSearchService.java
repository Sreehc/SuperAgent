package com.superagent.agent.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KnowledgeSearchService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> search(long tenantId, String question, Long knowledgeBaseId, int limit) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("knowledgeBaseId", knowledgeBaseId)
                .addValue("query", normalized)
                .addValue("likeQuery", "%" + normalized + "%")
                .addValue("limit", Math.max(1, Math.min(limit, 20)));

        return jdbcTemplate.query("""
                        SELECT
                            kd.id AS document_id,
                            kd.knowledge_base_id,
                            kd.title AS document_title,
                            dc.id AS chunk_id,
                            dc.chunk_no,
                            dc.section_title,
                            dc.content,
                            GREATEST(
                                COALESCE(ts_rank_cd(dc.search_vector, plainto_tsquery('simple', :query)), 0),
                                CASE
                                    WHEN dc.content ILIKE :likeQuery THEN 0.60
                                    WHEN kd.title ILIKE :likeQuery THEN 0.50
                                    ELSE 0
                                END
                            ) AS score
                        FROM document_chunk dc
                        JOIN knowledge_document kd
                          ON kd.id = dc.document_id
                         AND kd.tenant_id = dc.tenant_id
                        WHERE dc.tenant_id = :tenantId
                          AND kd.deleted_at IS NULL
                          AND kd.status = 'ready'
                          AND (:knowledgeBaseId IS NULL OR kd.knowledge_base_id = :knowledgeBaseId)
                          AND (
                              dc.search_vector @@ plainto_tsquery('simple', :query)
                              OR dc.content ILIKE :likeQuery
                              OR kd.title ILIKE :likeQuery
                          )
                        ORDER BY score DESC, kd.updated_at DESC, dc.chunk_no ASC
                        LIMIT :limit
                        """,
                params,
                (rs, rowNum) -> Map.<String, Object>of(
                        "documentId", rs.getLong("document_id"),
                        "knowledgeBaseId", rs.getLong("knowledge_base_id"),
                        "chunkId", rs.getLong("chunk_id"),
                        "chunkNo", rs.getInt("chunk_no"),
                        "title", rs.getString("document_title"),
                        "sectionTitle", rs.getString("section_title") == null ? "" : rs.getString("section_title"),
                        "quote", excerpt(rs.getString("content")),
                        "score", rs.getBigDecimal("score") == null ? BigDecimal.ZERO : rs.getBigDecimal("score")
                )
        );
    }

    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 360 ? normalized : normalized.substring(0, 360);
    }
}
