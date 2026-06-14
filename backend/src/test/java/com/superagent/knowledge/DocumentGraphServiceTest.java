package com.superagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.superagent.knowledge.domain.DocumentChunk;
import com.superagent.knowledge.domain.KnowledgeBase;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.domain.KnowledgeBaseVisibility;
import com.superagent.knowledge.domain.KnowledgeDocument;
import com.superagent.knowledge.domain.KnowledgeDocumentStatus;
import com.superagent.knowledge.domain.KnowledgeDocumentVersion;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.knowledge.service.DocumentGraphService;
import com.superagent.knowledge.service.Neo4jDocumentGraphStore;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentGraphServiceTest {

    @Mock
    private KnowledgeRepository knowledgeRepository;

    @Mock
    private Neo4jDocumentGraphStore neo4jDocumentGraphStore;

    @Test
    void shouldPreferPersistedNeo4jGraphWhenAvailable() {
        DocumentGraphService service = new DocumentGraphService(knowledgeRepository, neo4jDocumentGraphStore);
        KnowledgeBase knowledgeBase = knowledgeBase();
        KnowledgeDocument document = knowledgeDocument();
        KnowledgeDocumentVersion version = knowledgeDocumentVersion();
        DocumentGraphService.DocumentGraphSnapshot persisted = new DocumentGraphService.DocumentGraphSnapshot(
                document.id(),
                version.versionNo(),
                "ready",
                "ready",
                List.of(new DocumentGraphService.GraphNode("doc:301", "Document", "退款指南", Map.of("status", "ready"))),
                List.of()
        );

        when(knowledgeRepository.findLatestDocumentVersion(10001L, document.id())).thenReturn(Optional.of(version));
        when(neo4jDocumentGraphStore.loadDocumentGraph(10001L, document.id(), version.versionNo(), "ready", "ready"))
                .thenReturn(Optional.of(persisted));

        DocumentGraphService.DocumentGraphSnapshot result = service.buildGraph(10001L, knowledgeBase, document);

        assertThat(result).isEqualTo(persisted);
        verify(knowledgeRepository, never()).listAllDocumentChunks(anyLong(), anyLong());
    }

    @Test
    void shouldPersistDerivedGraphToNeo4jDuringSynchronization() {
        DocumentGraphService service = new DocumentGraphService(knowledgeRepository, neo4jDocumentGraphStore);
        KnowledgeBase knowledgeBase = knowledgeBase();
        KnowledgeDocument document = knowledgeDocument();
        KnowledgeDocumentVersion version = knowledgeDocumentVersion();
        List<DocumentChunk> chunks = List.of(new DocumentChunk(
                901L,
                10001L,
                document.id(),
                null,
                1,
                "退款规则",
                "退款规则说明，涉及订单和客户关系。",
                "hash",
                18,
                null,
                Map.of(),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        when(knowledgeRepository.findLatestDocumentVersion(10001L, document.id())).thenReturn(Optional.of(version));
        when(knowledgeRepository.listAllDocumentChunks(10001L, document.id())).thenReturn(chunks);
        when(neo4jDocumentGraphStore.loadDocumentGraph(10001L, document.id(), version.versionNo(), "ready", "ready"))
                .thenReturn(Optional.empty());

        DocumentGraphService.DocumentGraphSnapshot result = service.synchronizeGraph(10001L, knowledgeBase, document);

        ArgumentCaptor<DocumentGraphService.DocumentGraphSnapshot> graphCaptor =
                ArgumentCaptor.forClass(DocumentGraphService.DocumentGraphSnapshot.class);
        verify(neo4jDocumentGraphStore).replaceDocumentGraph(eq(10001L), eq(document.id()), eq(version.versionNo()), graphCaptor.capture());
        verify(knowledgeRepository).updateDocumentVersion(eq(10001L), eq(version.id()), eq(version.status()), eq(version.chunkCount()), eq("ready"), anyMap());
        verify(knowledgeRepository).updateDocumentStatus(
                eq(10001L),
                eq(document.id()),
                eq(document.status()),
                eq(document.chunkCount()),
                eq(document.errorMessage()),
                eq(document.parsedText()),
                eq(version.versionNo()),
                eq("ready"),
                eq(null)
        );

        DocumentGraphService.DocumentGraphSnapshot persistedGraph = graphCaptor.getValue();
        assertThat(persistedGraph.nodes()).extracting(DocumentGraphService.GraphNode::type)
                .contains("KnowledgeBase", "Document", "Chunk", "Entity");
        assertThat(persistedGraph.edges()).extracting(DocumentGraphService.GraphEdge::type)
                .contains("CONTAINS", "MENTIONS");
        assertThat(result.documentGraphSyncStatus()).isEqualTo("ready");
        assertThat(result.versionGraphSyncStatus()).isEqualTo("ready");
    }

    private KnowledgeBase knowledgeBase() {
        return new KnowledgeBase(
                201L,
                10001L,
                "退款知识库",
                "知识库描述",
                KnowledgeBaseVisibility.tenant,
                KnowledgeBaseStatus.published,
                5001L,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private KnowledgeDocument knowledgeDocument() {
        return new KnowledgeDocument(
                301L,
                10001L,
                201L,
                null,
                null,
                "ready",
                null,
                1,
                "退款指南",
                "refund.md",
                "md",
                512L,
                "object-key",
                "hash",
                KnowledgeDocumentStatus.ready,
                1,
                null,
                "退款说明",
                "ops",
                List.of("refund"),
                5001L,
                null,
                null,
                "approved",
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private KnowledgeDocumentVersion knowledgeDocumentVersion() {
        return new KnowledgeDocumentVersion(
                401L,
                10001L,
                301L,
                1,
                null,
                "ready",
                1,
                "ready",
                Map.of(),
                5001L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
