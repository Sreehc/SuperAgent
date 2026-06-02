package com.superagent.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GraphQueryService {

    private static final Pattern TERM_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,}|[\\u4e00-\\u9fa5]{2,8}");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Neo4jGraphQueryRepository neo4jGraphQueryRepository;

    public GraphQueryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            Neo4jGraphQueryRepository neo4jGraphQueryRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.neo4jGraphQueryRepository = neo4jGraphQueryRepository;
    }

    public Map<String, Object> query(long tenantId, String question, Long knowledgeBaseId, Long documentId, int limit) {
        List<String> terms = extractTerms(question);
        List<String> normalizedTerms = terms.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
        Optional<Neo4jGraphQueryRepository.GraphQueryPayload> neo4jResult =
                neo4jGraphQueryRepository.query(tenantId, normalizedTerms, knowledgeBaseId, documentId, limit);
        if (neo4jResult.isPresent()) {
            return Map.of(
                    "terms", terms,
                    "source", "neo4j",
                    "evidence", neo4jResult.get().evidence(),
                    "nodes", neo4jResult.get().nodes(),
                    "edges", neo4jResult.get().edges()
            );
        }
        List<GraphHit> hits = findHits(tenantId, knowledgeBaseId, documentId, terms, limit);
        List<Map<String, Object>> evidence = hits.stream()
                .map(hit -> Map.<String, Object>of(
                        "knowledgeBaseId", hit.knowledgeBaseId(),
                        "documentId", hit.documentId(),
                        "documentTitle", hit.documentTitle(),
                        "chunkId", hit.chunkId(),
                        "chunkNo", hit.chunkNo(),
                        "content", hit.content(),
                        "score", hit.score()
                ))
                .toList();
        return Map.of(
                "terms", terms,
                "source", "postgres_fallback",
                "evidence", evidence,
                "nodes", buildNodes(terms, hits),
                "edges", buildEdges(terms, hits)
        );
    }

    private List<GraphHit> findHits(long tenantId, Long knowledgeBaseId, Long documentId, List<String> terms, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    kd.knowledge_base_id,
                    kd.id AS document_id,
                    kd.title AS document_title,
                    dc.id AS chunk_id,
                    dc.chunk_no,
                    dc.content,
                    (
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", Math.max(1, limit));

        if (terms.isEmpty()) {
            sql.append("1");
        } else {
            for (int index = 0; index < terms.size(); index++) {
                if (index > 0) {
                    sql.append(" + ");
                }
                String key = "term" + index;
                sql.append("CASE WHEN dc.content ILIKE :").append(key).append(" THEN 1 ELSE 0 END");
                params.addValue(key, "%" + terms.get(index) + "%");
            }
        }
        sql.append("""
                    )::double precision AS score
                FROM document_chunk dc
                JOIN knowledge_document kd ON kd.id = dc.document_id
                JOIN knowledge_base kb ON kb.id = kd.knowledge_base_id
                WHERE dc.tenant_id = :tenantId
                  AND kd.tenant_id = :tenantId
                  AND kd.status = 'ready'
                  AND kd.deleted_at IS NULL
                  AND kb.status = 'published'
                  AND kb.deleted_at IS NULL
                """);
        if (knowledgeBaseId != null) {
            sql.append(" AND kd.knowledge_base_id = :knowledgeBaseId");
            params.addValue("knowledgeBaseId", knowledgeBaseId);
        }
        if (documentId != null) {
            sql.append(" AND kd.id = :documentId");
            params.addValue("documentId", documentId);
        }
        if (!terms.isEmpty()) {
            sql.append(" AND (");
            for (int index = 0; index < terms.size(); index++) {
                if (index > 0) {
                    sql.append(" OR ");
                }
                sql.append("dc.content ILIKE :term").append(index);
            }
            sql.append(")");
        }
        sql.append(" ORDER BY score DESC, dc.chunk_no ASC LIMIT :limit");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new GraphHit(
                rs.getLong("knowledge_base_id"),
                rs.getLong("document_id"),
                rs.getString("document_title"),
                rs.getLong("chunk_id"),
                rs.getInt("chunk_no"),
                rs.getString("content"),
                rs.getDouble("score")
        ));
    }

    private List<Map<String, Object>> buildNodes(List<String> terms, List<GraphHit> hits) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String term : terms) {
            String id = "entity:" + slugify(term);
            if (seen.add(id)) {
                nodes.add(Map.of("id", id, "type", "Entity", "label", term));
            }
        }
        for (GraphHit hit : hits) {
            String documentId = "document:" + hit.documentId();
            if (seen.add(documentId)) {
                nodes.add(Map.of("id", documentId, "type", "Document", "label", hit.documentTitle()));
            }
            String chunkId = "chunk:" + hit.chunkId();
            if (seen.add(chunkId)) {
                nodes.add(Map.of("id", chunkId, "type", "Chunk", "label", "Chunk " + hit.chunkNo()));
            }
        }
        return nodes;
    }

    private List<Map<String, Object>> buildEdges(List<String> terms, List<GraphHit> hits) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GraphHit hit : hits) {
            String documentId = "document:" + hit.documentId();
            String chunkId = "chunk:" + hit.chunkId();
            edges.add(Map.of("sourceId", documentId, "targetId", chunkId, "type", "CONTAINS"));
            for (String term : terms) {
                if (hit.content().contains(term)) {
                    edges.add(Map.of(
                            "sourceId", chunkId,
                            "targetId", "entity:" + slugify(term),
                            "type", "MENTIONS"
                    ));
                }
            }
        }
        for (int index = 0; index < terms.size() - 1; index++) {
            edges.add(Map.of(
                    "sourceId", "entity:" + slugify(terms.get(index)),
                    "targetId", "entity:" + slugify(terms.get(index + 1)),
                    "type", "RELATES_TO"
            ));
        }
        return edges;
    }

    private List<String> extractTerms(String question) {
        Matcher matcher = TERM_PATTERN.matcher(question == null ? "" : question);
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            terms.add(matcher.group().trim());
            if (terms.size() >= 6) {
                break;
            }
        }
        return List.copyOf(terms);
    }

    private String slugify(String value) {
        return value == null ? "unknown" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-").replaceAll("(^-|-$)", "").toLowerCase();
    }

    private record GraphHit(
            long knowledgeBaseId,
            long documentId,
            String documentTitle,
            long chunkId,
            int chunkNo,
            String content,
            double score
    ) {
    }
}
