package com.superagent.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern CN_RELATION_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,8}|[A-Za-z][A-Za-z0-9_-]{1,31})\\s*(?:和|与|跟|同)\\s*([\\u4e00-\\u9fa5]{2,8}|[A-Za-z][A-Za-z0-9_-]{1,31})(?=之间|的?(?:关系|联系|路径))(?:之间)?(?:的)?(?:关系|联系|路径)");
    private static final Pattern EN_RELATION_PATTERN = Pattern.compile("(?:relationship|relation|path)\\s+(?:between|from)\\s+([A-Za-z][A-Za-z0-9_-]{1,31})\\s+(?:and|to)\\s+([A-Za-z][A-Za-z0-9_-]{1,31})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARROW_RELATION_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,8}|[A-Za-z][A-Za-z0-9_-]{1,31})\\s*(?:->|→)\\s*([\\u4e00-\\u9fa5]{2,8}|[A-Za-z][A-Za-z0-9_-]{1,31})");
    private static final Pattern MAX_HOPS_PATTERN = Pattern.compile("(\\d+)\\s*(?:跳|hops?)", Pattern.CASE_INSENSITIVE);

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
        GraphQueryRequest request = buildRequest(question);
        Optional<Neo4jGraphQueryRepository.GraphQueryPayload> neo4jResult =
                neo4jGraphQueryRepository.query(tenantId, request, knowledgeBaseId, documentId, limit);
        if (neo4jResult.isPresent()) {
            return Map.of(
                    "terms", request.terms(),
                    "source", "neo4j",
                    "queryMode", request.pathQuery() ? "path" : "entity_search",
                    "evidence", neo4jResult.get().evidence(),
                    "nodes", neo4jResult.get().nodes(),
                    "edges", neo4jResult.get().edges()
            );
        }

        List<GraphHit> hits = findHits(tenantId, knowledgeBaseId, documentId, request.terms(), limit);
        List<Map<String, Object>> evidence = hits.stream()
                .map(hit -> buildFallbackEvidence(request, hit))
                .toList();
        return Map.of(
                "terms", request.terms(),
                "source", "postgres_fallback",
                "queryMode", request.pathQuery() ? "path" : "entity_search",
                "evidence", evidence,
                "nodes", buildNodes(request, hits),
                "edges", buildEdges(request, hits)
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

    private Map<String, Object> buildFallbackEvidence(GraphQueryRequest request, GraphHit hit) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("knowledgeBaseId", hit.knowledgeBaseId());
        evidence.put("documentId", hit.documentId());
        evidence.put("documentTitle", hit.documentTitle());
        evidence.put("chunkId", hit.chunkId());
        evidence.put("chunkNo", hit.chunkNo());
        evidence.put("content", hit.content());
        evidence.put("score", hit.score());
        evidence.put("queryMode", request.pathQuery() ? "path" : "entity_search");
        if (request.pathQuery()) {
            evidence.put("sourceEntity", request.sourceEntity());
            evidence.put("targetEntity", request.targetEntity());
            evidence.put("path", request.sourceEntity() + " -> " + "Chunk " + hit.chunkNo() + " -> " + request.targetEntity());
            evidence.put("hopCount", 2);
        }
        return evidence;
    }

    private List<Map<String, Object>> buildNodes(GraphQueryRequest request, List<GraphHit> hits) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String term : request.terms()) {
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

    private List<Map<String, Object>> buildEdges(GraphQueryRequest request, List<GraphHit> hits) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GraphHit hit : hits) {
            String documentId = "document:" + hit.documentId();
            String chunkId = "chunk:" + hit.chunkId();
            edges.add(Map.of("sourceId", documentId, "targetId", chunkId, "type", "CONTAINS"));
            for (String term : request.terms()) {
                if (hit.content().contains(term)) {
                    edges.add(Map.of(
                            "sourceId", chunkId,
                            "targetId", "entity:" + slugify(term),
                            "type", "MENTIONS"
                    ));
                }
            }
        }
        if (request.pathQuery() && request.sourceEntity() != null && request.targetEntity() != null) {
            edges.add(Map.of(
                    "sourceId", "entity:" + slugify(request.sourceEntity()),
                    "targetId", "entity:" + slugify(request.targetEntity()),
                    "type", "RELATES_TO"
            ));
        }
        for (int index = 0; index < request.terms().size() - 1; index++) {
            edges.add(Map.of(
                    "sourceId", "entity:" + slugify(request.terms().get(index)),
                    "targetId", "entity:" + slugify(request.terms().get(index + 1)),
                    "type", "RELATES_TO"
            ));
        }
        return edges;
    }

    private GraphQueryRequest buildRequest(String question) {
        List<String> terms = extractTerms(question);
        List<String> normalizedTerms = terms.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        RelationIntent relationIntent = extractRelationIntent(question);
        if (relationIntent == null) {
            return new GraphQueryRequest(terms, normalizedTerms, null, null, false, 4);
        }

        Set<String> mergedTerms = new LinkedHashSet<>();
        mergedTerms.add(relationIntent.sourceEntity());
        mergedTerms.add(relationIntent.targetEntity());
        mergedTerms.addAll(terms);
        List<String> enrichedTerms = List.copyOf(mergedTerms);
        return new GraphQueryRequest(
                enrichedTerms,
                enrichedTerms.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList(),
                relationIntent.sourceEntity(),
                relationIntent.targetEntity(),
                true,
                relationIntent.maxHops()
        );
    }

    private RelationIntent extractRelationIntent(String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isBlank()) {
            return null;
        }

        Matcher matcher = CN_RELATION_PATTERN.matcher(normalizedQuestion);
        if (matcher.find()) {
            return new RelationIntent(normalizeEntityCandidate(matcher.group(1)), normalizeEntityCandidate(matcher.group(2)), extractMaxHops(normalizedQuestion));
        }
        matcher = EN_RELATION_PATTERN.matcher(normalizedQuestion);
        if (matcher.find()) {
            return new RelationIntent(normalizeEntityCandidate(matcher.group(1)), normalizeEntityCandidate(matcher.group(2)), extractMaxHops(normalizedQuestion));
        }
        matcher = ARROW_RELATION_PATTERN.matcher(normalizedQuestion);
        if (matcher.find()) {
            return new RelationIntent(normalizeEntityCandidate(matcher.group(1)), normalizeEntityCandidate(matcher.group(2)), extractMaxHops(normalizedQuestion));
        }
        return null;
    }

    private int extractMaxHops(String question) {
        Matcher matcher = MAX_HOPS_PATTERN.matcher(question == null ? "" : question);
        if (matcher.find()) {
            try {
                return Math.max(2, Math.min(6, Integer.parseInt(matcher.group(1))));
            } catch (NumberFormatException ignored) {
                return 4;
            }
        }
        return 4;
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
        return value == null ? "unknown" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-").replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeEntityCandidate(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("(之间的|之间|的)$", "")
                .trim();
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

    private record RelationIntent(
            String sourceEntity,
            String targetEntity,
            int maxHops
    ) {
    }
}
