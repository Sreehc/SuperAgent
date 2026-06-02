package com.superagent.knowledge.service;

import com.superagent.infra.config.SuperAgentProperties;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class Neo4jDocumentGraphStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jDocumentGraphStore.class);

    private static final List<String> NODE_RESERVED_KEYS = List.of("nodeId", "tenantId", "documentId", "versionNo", "label");
    private static final List<String> EDGE_RESERVED_KEYS = List.of("tenantId", "documentId", "versionNo");

    private final SuperAgentProperties properties;
    private final Environment environment;
    private volatile Driver driver;

    public Neo4jDocumentGraphStore(
            SuperAgentProperties properties,
            Environment environment
    ) {
        this.properties = properties;
        this.environment = environment;
    }

    public boolean isEnabled() {
        return resolveDriver() != null;
    }

    public void replaceDocumentGraph(long tenantId, long documentId, int versionNo, DocumentGraphService.DocumentGraphSnapshot graph) {
        Driver driver = resolveDriver();
        if (driver == null) {
            return;
        }
        try (Session session = driver.session()) {
            session.executeWrite(transaction -> {
                transaction.run(
                        "MATCH (n {tenantId: $tenantId, documentId: $documentId}) DETACH DELETE n",
                        Map.of("tenantId", tenantId, "documentId", documentId)
                );
                persistNodes(transaction, tenantId, documentId, versionNo, graph.nodes());
                persistEdges(transaction, tenantId, documentId, versionNo, graph.edges());
                return null;
            });
        } catch (Exception exception) {
            log.warn("Neo4j graph persistence unavailable for document {}", documentId, exception);
        }
    }

    public Optional<DocumentGraphService.DocumentGraphSnapshot> loadDocumentGraph(
            long tenantId,
            long documentId,
            int versionNo,
            String documentGraphSyncStatus,
            String versionGraphSyncStatus
    ) {
        Driver driver = resolveDriver();
        if (driver == null) {
            return Optional.empty();
        }

        try (Session session = driver.session()) {
            List<DocumentGraphService.GraphNode> nodes = session.executeRead(transaction -> transaction.run(
                    """
                    MATCH (n {tenantId: $tenantId, documentId: $documentId})
                    RETURN labels(n)[0] AS type,
                           n.nodeId AS nodeId,
                           coalesce(n.label, n.title, n.text, n.nodeId) AS label,
                           properties(n) AS props
                    ORDER BY n.nodeId
                    """,
                    Map.of("tenantId", tenantId, "documentId", documentId)
            ).list(this::mapNode));
            if (nodes.isEmpty()) {
                return Optional.empty();
            }
            List<DocumentGraphService.GraphEdge> edges = session.executeRead(transaction -> transaction.run(
                    """
                    MATCH (source {tenantId: $tenantId, documentId: $documentId})-[r]->(target {tenantId: $tenantId, documentId: $documentId})
                    RETURN source.nodeId AS sourceId,
                           target.nodeId AS targetId,
                           type(r) AS type,
                           properties(r) AS props
                    ORDER BY source.nodeId, target.nodeId, type(r)
                    """,
                    Map.of("tenantId", tenantId, "documentId", documentId)
            ).list(this::mapEdge));
            return Optional.of(new DocumentGraphService.DocumentGraphSnapshot(
                    documentId,
                    resolveVersionNo(nodes, versionNo),
                    documentGraphSyncStatus,
                    versionGraphSyncStatus,
                    nodes,
                    edges
            ));
        } catch (Exception exception) {
            log.warn("Neo4j graph load unavailable for document {}", documentId, exception);
            return Optional.empty();
        }
    }

    private void persistNodes(
            TransactionContext transaction,
            long tenantId,
            long documentId,
            int versionNo,
            List<DocumentGraphService.GraphNode> nodes
    ) {
        Map<String, List<Map<String, Object>>> grouped = nodes.stream()
                .collect(Collectors.groupingBy(DocumentGraphService.GraphNode::type, LinkedHashMap::new, Collectors.mapping(
                        node -> toNodeProperties(tenantId, documentId, versionNo, node),
                        Collectors.toList()
                )));
        grouped.forEach((type, rows) -> {
            if (!rows.isEmpty()) {
                transaction.run(
                        "UNWIND $rows AS row CREATE (n:" + labelFor(type) + ") SET n = row",
                        Map.of("rows", rows)
                );
            }
        });
    }

    private void persistEdges(
            TransactionContext transaction,
            long tenantId,
            long documentId,
            int versionNo,
            List<DocumentGraphService.GraphEdge> edges
    ) {
        Map<String, List<Map<String, Object>>> grouped = edges.stream()
                .collect(Collectors.groupingBy(DocumentGraphService.GraphEdge::type, LinkedHashMap::new, Collectors.mapping(
                        edge -> toEdgeProperties(tenantId, documentId, versionNo, edge),
                        Collectors.toList()
                )));
        grouped.forEach((type, rows) -> {
            if (!rows.isEmpty()) {
                transaction.run("""
                        UNWIND $rows AS row
                        MATCH (source {tenantId: $tenantId, documentId: $documentId, nodeId: row.sourceId})
                        MATCH (target {tenantId: $tenantId, documentId: $documentId, nodeId: row.targetId})
                        CREATE (source)-[r:%s]->(target)
                        SET r = row.props
                        """.formatted(typeFor(type)),
                        Map.of(
                                "tenantId", tenantId,
                                "documentId", documentId,
                                "rows", rows
                        )
                );
            }
        });
    }

    private Map<String, Object> toNodeProperties(
            long tenantId,
            long documentId,
            int versionNo,
            DocumentGraphService.GraphNode node
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nodeId", node.id());
        row.put("tenantId", tenantId);
        row.put("documentId", documentId);
        row.put("versionNo", versionNo);
        row.put("label", node.label());
        if (node.metadata() != null) {
            node.metadata().forEach((key, value) -> {
                Object normalized = normalize(value);
                if (normalized != null) {
                    row.put(key, normalized);
                }
            });
        }
        return row;
    }

    private Map<String, Object> toEdgeProperties(
            long tenantId,
            long documentId,
            int versionNo,
            DocumentGraphService.GraphEdge edge
    ) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tenantId", tenantId);
        props.put("documentId", documentId);
        props.put("versionNo", versionNo);
        if (edge.metadata() != null) {
            edge.metadata().forEach((key, value) -> {
                Object normalized = normalize(value);
                if (normalized != null) {
                    props.put(key, normalized);
                }
            });
        }
        return Map.of(
                "sourceId", edge.sourceId(),
                "targetId", edge.targetId(),
                "props", props
        );
    }

    private DocumentGraphService.GraphNode mapNode(Record record) {
        return new DocumentGraphService.GraphNode(
                record.get("nodeId").asString(),
                record.get("type").asString(),
                record.get("label").asString(),
                filterReserved(record.get("props").asMap(this::convert), NODE_RESERVED_KEYS)
        );
    }

    private DocumentGraphService.GraphEdge mapEdge(Record record) {
        return new DocumentGraphService.GraphEdge(
                record.get("sourceId").asString(),
                record.get("targetId").asString(),
                record.get("type").asString(),
                filterReserved(record.get("props").asMap(this::convert), EDGE_RESERVED_KEYS)
        );
    }

    private Map<String, Object> filterReserved(Map<String, Object> source, List<String> reservedKeys) {
        Map<String, Object> filtered = new LinkedHashMap<>(source);
        reservedKeys.forEach(filtered::remove);
        return filtered;
    }

    private int resolveVersionNo(List<DocumentGraphService.GraphNode> nodes, int fallbackVersionNo) {
        return nodes.stream()
                .map(DocumentGraphService.GraphNode::metadata)
                .map(metadata -> metadata.get("versionNo"))
                .map(this::toInteger)
                .filter(value -> value > 0)
                .findFirst()
                .orElse(fallbackVersionNo);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Object convert(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return normalize(value.asObject());
        } catch (Exception exception) {
            return value.toString();
        }
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::normalize)
                    .filter(item -> item != null)
                    .toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                Object normalizedValue = normalize(item);
                if (normalizedValue != null) {
                    normalized.put(String.valueOf(key), normalizedValue);
                }
            });
            return normalized;
        }
        return String.valueOf(value);
    }

    private String labelFor(String type) {
        return switch (type) {
            case "KnowledgeBase" -> "KnowledgeBase";
            case "Document" -> "Document";
            case "Section" -> "Section";
            case "Chunk" -> "Chunk";
            case "Entity" -> "Entity";
            default -> throw new IllegalArgumentException("Unsupported graph node type: " + type);
        };
    }

    private String typeFor(String type) {
        return switch (type) {
            case "CONTAINS" -> "CONTAINS";
            case "NEXT" -> "NEXT";
            case "MENTIONS" -> "MENTIONS";
            case "RELATES_TO" -> "RELATES_TO";
            default -> throw new IllegalArgumentException("Unsupported graph edge type: " + type);
        };
    }

    private Driver resolveDriver() {
        if (!Boolean.TRUE.equals(properties.getGraph().getEnabled()) || isTestProfile()) {
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
