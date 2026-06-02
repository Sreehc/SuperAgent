package com.superagent.agent.service;

import com.superagent.agent.config.AgentServiceProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class Neo4jGraphQueryRepository {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphQueryRepository.class);

    private final AgentServiceProperties properties;
    private final Environment environment;
    private volatile Driver driver;

    public Neo4jGraphQueryRepository(
            AgentServiceProperties properties,
            Environment environment
    ) {
        this.properties = properties;
        this.environment = environment;
    }

    public Optional<GraphQueryPayload> query(long tenantId, List<String> normalizedTerms, Long knowledgeBaseId, Long documentId, int limit) {
        Driver driver = resolveDriver();
        if (driver == null) {
            return Optional.empty();
        }

        try (Session session = driver.session()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("tenantId", tenantId);
            params.put("knowledgeBaseId", knowledgeBaseId);
            params.put("documentId", documentId);
            params.put("terms", normalizedTerms);
            params.put("limit", Math.max(1, limit));
            List<Record> records = session.executeRead(transaction -> transaction.run("""
                    MATCH (document:Document {tenantId: $tenantId})
                    WHERE ($knowledgeBaseId IS NULL OR document.knowledgeBaseId = $knowledgeBaseId)
                      AND ($documentId IS NULL OR document.documentId = $documentId)
                    MATCH path=(document)-[:CONTAINS*1..2]->(chunk:Chunk {tenantId: $tenantId, documentId: document.documentId})
                    OPTIONAL MATCH (chunk)-[mention:MENTIONS]->(entity:Entity {tenantId: $tenantId, documentId: document.documentId})
                    OPTIONAL MATCH (entity)-[related:RELATES_TO]->(relatedEntity:Entity {tenantId: $tenantId, documentId: document.documentId})
                    WITH document, chunk, entity, relatedEntity, mention, related, path
                    WHERE size($terms) = 0
                       OR any(term IN $terms WHERE toLower(coalesce(entity.text, '')) CONTAINS term
                                             OR toLower(coalesce(entity.entityKey, '')) CONTAINS term
                                             OR toLower(coalesce(chunk.contentPreview, '')) CONTAINS term
                                             OR toLower(coalesce(chunk.sectionTitle, '')) CONTAINS term
                                             OR toLower(coalesce(document.title, '')) CONTAINS term)
                    RETURN document.documentId AS documentId,
                           document.knowledgeBaseId AS knowledgeBaseId,
                           document.title AS documentTitle,
                           chunk.chunkId AS chunkId,
                           chunk.chunkNo AS chunkNo,
                           chunk.contentPreview AS content,
                           CASE WHEN entity IS NULL THEN NULL ELSE entity.text END AS entityText,
                           CASE WHEN relatedEntity IS NULL THEN NULL ELSE relatedEntity.text END AS relatedEntityText,
                           [node IN nodes(path) | {
                               id: node.nodeId,
                               type: labels(node)[0],
                               label: coalesce(node.label, node.title, node.text, node.nodeId),
                               props: properties(node)
                           }] AS pathNodes,
                           [edge IN relationships(path) | {
                               sourceId: startNode(edge).nodeId,
                               targetId: endNode(edge).nodeId,
                               type: type(edge),
                               props: properties(edge)
                           }] AS pathEdges,
                           CASE WHEN mention IS NULL THEN NULL ELSE {
                               sourceId: startNode(mention).nodeId,
                               targetId: endNode(mention).nodeId,
                               type: type(mention),
                               props: properties(mention)
                           } END AS mentionEdge,
                           CASE WHEN entity IS NULL THEN NULL ELSE {
                               id: entity.nodeId,
                               type: labels(entity)[0],
                               label: coalesce(entity.label, entity.text, entity.nodeId),
                               props: properties(entity)
                           } END AS entityNode,
                           CASE WHEN related IS NULL THEN NULL ELSE {
                               sourceId: startNode(related).nodeId,
                               targetId: endNode(related).nodeId,
                               type: type(related),
                               props: properties(related)
                           } END AS relatedEdge,
                           CASE WHEN relatedEntity IS NULL THEN NULL ELSE {
                               id: relatedEntity.nodeId,
                               type: labels(relatedEntity)[0],
                               label: coalesce(relatedEntity.label, relatedEntity.text, relatedEntity.nodeId),
                               props: properties(relatedEntity)
                           } END AS relatedEntityNode
                    ORDER BY chunk.chunkNo ASC
                    LIMIT $limit
                    """, params).list());

            if (records.isEmpty()) {
                return Optional.empty();
            }

            List<Map<String, Object>> evidence = new ArrayList<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            Set<String> nodeIds = new LinkedHashSet<>();
            Set<String> edgeIds = new LinkedHashSet<>();

            for (Record record : records) {
                evidence.add(buildEvidence(record));
                collectNodes(record, nodes, nodeIds);
                collectEdges(record, edges, edgeIds);
            }

            return Optional.of(new GraphQueryPayload(evidence, nodes, edges));
        } catch (Exception exception) {
            log.warn("Neo4j graph query unavailable for tenant {}", tenantId, exception);
            return Optional.empty();
        }
    }

    private Map<String, Object> buildEvidence(Record record) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("knowledgeBaseId", record.get("knowledgeBaseId").isNull() ? null : record.get("knowledgeBaseId").asLong());
        evidence.put("documentId", record.get("documentId").isNull() ? null : record.get("documentId").asLong());
        evidence.put("documentTitle", record.get("documentTitle").isNull() ? null : record.get("documentTitle").asString());
        evidence.put("chunkId", record.get("chunkId").isNull() ? null : record.get("chunkId").asLong());
        evidence.put("chunkNo", record.get("chunkNo").isNull() ? null : record.get("chunkNo").asInt());
        evidence.put("content", record.get("content").isNull() ? "" : record.get("content").asString());
        evidence.put("entity", record.get("entityText").isNull() ? null : record.get("entityText").asString());
        evidence.put("relatedEntity", record.get("relatedEntityText").isNull() ? null : record.get("relatedEntityText").asString());
        return evidence;
    }

    private void collectNodes(Record record, List<Map<String, Object>> nodes, Set<String> seen) {
        appendNodeMaps(record.get("pathNodes").asList(this::convertMap), nodes, seen);
        appendNodeMap(convertNullableMap(record.get("entityNode")), nodes, seen);
        appendNodeMap(convertNullableMap(record.get("relatedEntityNode")), nodes, seen);
    }

    private void collectEdges(Record record, List<Map<String, Object>> edges, Set<String> seen) {
        appendEdgeMaps(record.get("pathEdges").asList(this::convertMap), edges, seen);
        appendEdgeMap(convertNullableMap(record.get("mentionEdge")), edges, seen);
        appendEdgeMap(convertNullableMap(record.get("relatedEdge")), edges, seen);
    }

    private void appendNodeMaps(List<Map<String, Object>> source, List<Map<String, Object>> target, Set<String> seen) {
        for (Map<String, Object> node : source) {
            appendNodeMap(node, target, seen);
        }
    }

    private void appendNodeMap(Map<String, Object> node, List<Map<String, Object>> target, Set<String> seen) {
        if (node == null) {
            return;
        }
        String id = String.valueOf(node.get("id"));
        if (seen.add(id)) {
            target.add(node);
        }
    }

    private void appendEdgeMaps(List<Map<String, Object>> source, List<Map<String, Object>> target, Set<String> seen) {
        for (Map<String, Object> edge : source) {
            appendEdgeMap(edge, target, seen);
        }
    }

    private void appendEdgeMap(Map<String, Object> edge, List<Map<String, Object>> target, Set<String> seen) {
        if (edge == null) {
            return;
        }
        String id = edge.get("sourceId") + "->" + edge.get("targetId") + ":" + edge.get("type");
        if (seen.add(id)) {
            target.add(edge);
        }
    }

    private Map<String, Object> convertNullableMap(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return convertMap(value);
    }

    private Map<String, Object> convertMap(Value value) {
        Map<String, Object> raw = value.asMap(this::convert);
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if ("props".equals(key) && item instanceof Map<?, ?> props) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                props.forEach((propKey, propValue) -> {
                    String field = String.valueOf(propKey);
                    if (!List.of("tenantId", "documentId", "versionNo", "nodeId", "label").contains(field)) {
                        filtered.put(field, propValue);
                    }
                });
                normalized.put("metadata", filtered);
            } else {
                normalized.put(key, item);
            }
        });
        return normalized;
    }

    private Object convert(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        Object raw = value.asObject();
        if (raw instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        return normalize(raw);
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
            return normalized;
        }
        return String.valueOf(value);
    }

    public record GraphQueryPayload(
            List<Map<String, Object>> evidence,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges
    ) {
    }

    private Driver resolveDriver() {
        if (!properties.getGraph().isEnabled() || isTestProfile()) {
            return null;
        }
        if (driver == null) {
            synchronized (this) {
                if (driver == null) {
                    driver = GraphDatabase.driver(
                            properties.getGraph().getNeo4jUri(),
                            AuthTokens.basic(
                                    properties.getGraph().getNeo4jUsername(),
                                    properties.getGraph().getNeo4jPassword()
                            )
                    );
                }
            }
        }
        return driver;
    }

    private boolean isTestProfile() {
        return List.of(environment.getActiveProfiles()).contains("test");
    }

    @PreDestroy
    void closeDriver() {
        if (driver != null) {
            driver.close();
        }
    }
}
