package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.rag.service.EmbeddingClient;
import com.superagent.rag.service.RagSupportService;
import com.superagent.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetrievalServiceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExpandNeighborChunksAroundTopEvidence() {
        CurrentAuthenticatedUser currentAuthenticatedUser = mock(CurrentAuthenticatedUser.class);
        KnowledgeRepository knowledgeRepository = mock(KnowledgeRepository.class);
        RagSupportService ragSupportService = mock(RagSupportService.class);
        EmbeddingClient embeddingClient = inputs -> new EmbeddingClient.EmbeddingResult(
                "test-provider",
                "test-model",
                3,
                List.of(List.of(0.1d, 0.2d, 0.3d))
        );
        RetrievalService retrievalService = new RetrievalService(
                currentAuthenticatedUser,
                embeddingClient,
                knowledgeRepository,
                ragSupportService
        );
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        AuthenticatedUserPrincipal principal = mock(AuthenticatedUserPrincipal.class);
        when(principal.currentRole()).thenReturn(TenantRole.OWNER);
        when(currentAuthenticatedUser.get()).thenReturn(principal);

        when(knowledgeRepository.findNeighborChunks(10001L, 10L, List.of(2), 1, true)).thenReturn(List.of(
                new RetrievalResult(
                        "neighbor",
                        1L,
                        10L,
                        201L,
                        "退款规则",
                        1,
                        "前置上下文：退款申请入口",
                        null,
                        0.0d,
                        Map.of("activeVersionNo", 2, "chunkVersionNo", 2)
                ),
                new RetrievalResult(
                        "neighbor",
                        1L,
                        10L,
                        203L,
                        "退款规则",
                        3,
                        "后置上下文：退款到账时效",
                        null,
                        0.0d,
                        Map.of("activeVersionNo", 2, "chunkVersionNo", 2)
                )
        ));

        RagSearchQuery query = new RagSearchQuery(
                "退款规则是什么？",
                "退款规则是什么？",
                "退款规则是什么？",
                1,
                1L,
                "single_question",
                "provider_unavailable",
                0.5d,
                true,
                true,
                5,
                5,
                5,
                60,
                1,
                3,
                3,
                0.35d,
                false
        );
        List<RagEvidence> base = List.of(
                new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        202L,
                        "退款规则",
                        2,
                        "核心内容：退款需在7日内申请",
                        null,
                        0.9d,
                        Map.of("channels", List.of("vector", "keyword"), "activeVersionNo", 2, "chunkVersionNo", 2)
                )
        );

        List<RagEvidence> expanded = retrievalService.expandNeighbors(query, base);

        assertThat(expanded).hasSize(3);
        assertThat(expanded).extracting(RagEvidence::chunkNo).containsExactly(2, 1, 3);
        assertThat(expanded.get(1).metadata()).containsEntry("neighborExpanded", true);
        assertThat(expanded.get(1).metadata()).containsEntry("neighborAnchorChunkNo", 2);
        assertThat(expanded.get(2).metadata()).containsEntry("neighborExpanded", true);
    }
}
