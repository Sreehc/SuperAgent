package com.superagent.knowledge.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentVersion;
import com.superagent.knowledge.repository.KnowledgeRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DocumentGraphService {

    private static final Pattern ENTITY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,}|[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");

    private final KnowledgeRepository knowledgeRepository;
    private final Neo4jDocumentGraphStore neo4jDocumentGraphStore;

    public DocumentGraphService(
            KnowledgeRepository knowledgeRepository,
            Neo4jDocumentGraphStore neo4jDocumentGraphStore
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.neo4jDocumentGraphStore = neo4jDocumentGraphStore;
    }

    public DocumentGraphSnapshot buildGraph(long tenantId, KnowledgeBase knowledgeBase, KnowledgeDocument document) {
        KnowledgeDocumentVersion version = knowledgeRepository.findLatestDocumentVersion(tenantId, document.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document version not found"));
        Optional<DocumentGraphSnapshot> persisted = neo4jDocumentGraphStore.loadDocumentGraph(
                tenantId,
                document.id(),
                version.versionNo(),
                document.graphSyncStatus(),
                version.graphSyncStatus()
        );
        if (persisted.isPresent()) {
            return persisted.get();
        }
        List<DocumentChunk> chunks = knowledgeRepository.listAllDocumentChunks(tenantId, document.id());
        return buildDerivedGraph(knowledgeBase, document, version, chunks);
    }

    public DocumentGraphSnapshot synchronizeGraph(long tenantId, KnowledgeBase knowledgeBase, KnowledgeDocument document) {
        KnowledgeDocumentVersion version = knowledgeRepository.findLatestDocumentVersion(tenantId, document.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Document version not found"));
        List<DocumentChunk> chunks = knowledgeRepository.listAllDocumentChunks(tenantId, document.id());
        DocumentGraphSnapshot graph = buildDerivedGraph(knowledgeBase, document, version, chunks);
        try {
            neo4jDocumentGraphStore.replaceDocumentGraph(tenantId, document.id(), version.versionNo(), graph);
            knowledgeRepository.updateDocumentVersion(
                    tenantId,
                    version.id(),
                    version.status(),
                    version.chunkCount(),
                    "ready",
                    mergeMetadata(version.metadata(), Map.of(
                            "graphNodeCount", graph.nodes().size(),
                            "graphEdgeCount", graph.edges().size()
                    ))
            );
            knowledgeRepository.updateDocumentStatus(
                    tenantId,
                    document.id(),
                    document.status(),
                    document.chunkCount(),
                    document.errorMessage(),
                    document.parsedText(),
                    version.versionNo(),
                    "ready",
                    null
            );
            return neo4jDocumentGraphStore.loadDocumentGraph(
                    tenantId,
                    document.id(),
                    version.versionNo(),
                    "ready",
                    "ready"
            ).orElse(new DocumentGraphSnapshot(
                    graph.documentId(),
                    graph.versionNo(),
                    "ready",
                    "ready",
                    graph.nodes(),
                    graph.edges()
            ));
        } catch (Exception exception) {
            knowledgeRepository.updateDocumentVersion(
                    tenantId,
                    version.id(),
                    version.status(),
                    version.chunkCount(),
                    "failed",
                    mergeMetadata(version.metadata(), Map.of("graphError", safeError(exception)))
            );
            knowledgeRepository.updateDocumentStatus(
                    tenantId,
                    document.id(),
                    document.status(),
                    document.chunkCount(),
                    document.errorMessage(),
                    document.parsedText(),
                    version.versionNo(),
                    "failed",
                    safeError(exception)
            );
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, safeError(exception));
        }
    }

    private DocumentGraphSnapshot buildDerivedGraph(
            KnowledgeBase knowledgeBase,
            KnowledgeDocument document,
            KnowledgeDocumentVersion version,
            List<DocumentChunk> chunks
    ) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        String knowledgeBaseNodeId = "kb:" + knowledgeBase.id();
        String documentNodeId = "doc:" + document.id();
        nodes.add(new GraphNode(knowledgeBaseNodeId, "KnowledgeBase", knowledgeBase.name(), Map.of(
                "knowledgeBaseId", knowledgeBase.id(),
                "versionNo", version.versionNo()
        )));
        nodes.add(new GraphNode(documentNodeId, "Document", document.title(), Map.of(
                "knowledgeBaseId", knowledgeBase.id(),
                "documentId", document.id(),
                "title", document.title(),
                "versionNo", version.versionNo(),
                "status", document.status().name()
        )));
        edges.add(new GraphEdge(knowledgeBaseNodeId, documentNodeId, "CONTAINS", Map.of()));

        Set<String> emittedSections = new LinkedHashSet<>();
        Set<String> emittedEntities = new LinkedHashSet<>();
        List<String> orderedChunkNodeIds = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            String chunkNodeId = "chunk:" + chunk.id();
            orderedChunkNodeIds.add(chunkNodeId);
            String sectionTitle = resolveSectionTitle(chunk);
            Map<String, Object> chunkMetadata = new LinkedHashMap<>();
            chunkMetadata.put("knowledgeBaseId", knowledgeBase.id());
            chunkMetadata.put("documentId", document.id());
            chunkMetadata.put("chunkId", chunk.id());
            chunkMetadata.put("chunkNo", chunk.chunkNo());
            chunkMetadata.put("charCount", chunk.charCount());
            chunkMetadata.put("contentPreview", preview(chunk.content(), 600));
            chunkMetadata.put("versionNo", version.versionNo());
            if (sectionTitle != null) {
                chunkMetadata.put("sectionTitle", sectionTitle);
            }
            nodes.add(new GraphNode(chunkNodeId, "Chunk", "Chunk " + chunk.chunkNo(), chunkMetadata));

            if (sectionTitle != null) {
                String sectionNodeId = "section:" + document.id() + ":" + slugify(sectionTitle);
                if (emittedSections.add(sectionNodeId)) {
                    nodes.add(new GraphNode(sectionNodeId, "Section", sectionTitle, Map.of(
                            "knowledgeBaseId", knowledgeBase.id(),
                            "documentId", document.id(),
                            "sectionTitle", sectionTitle,
                            "versionNo", version.versionNo()
                    )));
                    edges.add(new GraphEdge(documentNodeId, sectionNodeId, "CONTAINS", Map.of()));
                }
                edges.add(new GraphEdge(sectionNodeId, chunkNodeId, "CONTAINS", Map.of()));
            } else {
                edges.add(new GraphEdge(documentNodeId, chunkNodeId, "CONTAINS", Map.of()));
            }

            List<String> entities = extractEntities(chunk.content());
            for (String entity : entities) {
                String entityNodeId = "entity:" + document.id() + ":" + slugify(entity);
                if (emittedEntities.add(entityNodeId)) {
                    nodes.add(new GraphNode(entityNodeId, "Entity", entity, Map.of(
                            "knowledgeBaseId", knowledgeBase.id(),
                            "documentId", document.id(),
                            "text", entity,
                            "entityKey", slugify(entity),
                            "versionNo", version.versionNo()
                    )));
                }
                edges.add(new GraphEdge(chunkNodeId, entityNodeId, "MENTIONS", Map.of()));
            }
            for (int index = 0; index < entities.size() - 1; index++) {
                edges.add(new GraphEdge(
                        "entity:" + document.id() + ":" + slugify(entities.get(index)),
                        "entity:" + document.id() + ":" + slugify(entities.get(index + 1)),
                        "RELATES_TO",
                        Map.of("documentId", document.id())
                ));
            }
        }

        for (int index = 0; index < orderedChunkNodeIds.size() - 1; index++) {
            edges.add(new GraphEdge(orderedChunkNodeIds.get(index), orderedChunkNodeIds.get(index + 1), "NEXT", Map.of()));
        }

        return new DocumentGraphSnapshot(
                document.id(),
                version.versionNo(),
                document.graphSyncStatus(),
                version.graphSyncStatus(),
                nodes,
                edges
        );
    }

    private String resolveSectionTitle(DocumentChunk chunk) {
        if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
            return chunk.sectionTitle().trim();
        }
        Matcher matcher = HEADING_PATTERN.matcher(chunk.content());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private List<String> extractEntities(String content) {
        Matcher matcher = ENTITY_PATTERN.matcher(content == null ? "" : content);
        Set<String> entities = new LinkedHashSet<>();
        while (matcher.find()) {
            String candidate = matcher.group().trim();
            if (candidate.length() >= 2) {
                entities.add(candidate);
            }
            if (entities.size() >= 8) {
                break;
            }
        }
        return List.copyOf(entities);
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        patch.forEach((key, value) -> {
            if (value != null) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private String slugify(String value) {
        return value == null ? "unknown" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-").replaceAll("(^-|-$)", "").toLowerCase();
    }

    private String preview(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        return content.length() <= maxLength ? content : content.substring(0, maxLength);
    }

    private String safeError(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Graph synchronization failed"
                : exception.getMessage();
    }

    public record DocumentGraphSnapshot(
            long documentId,
            int versionNo,
            String documentGraphSyncStatus,
            String versionGraphSyncStatus,
            List<GraphNode> nodes,
            List<GraphEdge> edges
    ) {
    }

    public record GraphNode(String id, String type, String label, Map<String, Object> metadata) {
    }

    public record GraphEdge(String sourceId, String targetId, String type, Map<String, Object> metadata) {
    }
}
