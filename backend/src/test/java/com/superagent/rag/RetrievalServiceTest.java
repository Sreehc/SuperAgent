package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.rag.service.EmbeddingClient;
import com.superagent.rag.service.RagQueryCache;
import com.superagent.rag.service.RagSupportService;
import com.superagent.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RetrievalServiceTest {

    private CurrentAuthenticatedUser currentAuthenticatedUser;
    private KnowledgeRepository knowledgeRepository;
    private RagSupportService ragSupportService;
    private EmbeddingClient embeddingClient;

    @BeforeEach
    void setUp() {
        currentAuthenticatedUser = mock(CurrentAuthenticatedUser.class);
        knowledgeRepository = mock(KnowledgeRepository.class);
        ragSupportService = mock(RagSupportService.class);
        embeddingClient = inputs -> new EmbeddingClient.EmbeddingResult(
                "test-provider",
                "test-model",
                3,
                List.of(List.of(0.1d, 0.2d, 0.3d))
        );
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExpandNeighborChunksAroundTopEvidence() {
        RetrievalService retrievalService = new RetrievalService(
                currentAuthenticatedUser,
                embeddingClient,
                knowledgeRepository,
                ragSupportService,
                mock(RagQueryCache.class)
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
                null,
                null,
                null,
                List.of(),
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
                2800,
                8400,
                0.35d,
                0.55d,
                false,
                30L,
                false,
                1,
                true
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

    @Test
    void shouldReuseKeywordResultsFromShortTtlCache() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getRag().setQueryResultCacheEnabled(true);
        properties.getRag().setQueryResultCacheTtlSeconds(30L);
        RagQueryCache ragQueryCache = new RagQueryCache(
                properties,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class),
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
        RetrievalService retrievalService = new RetrievalService(
                currentAuthenticatedUser,
                embeddingClient,
                knowledgeRepository,
                ragSupportService,
                ragQueryCache
        );
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        RagSearchQuery query = new RagSearchQuery(
                "退款多久到账？",
                "退款多久到账？",
                "退款多久到账？",
                1,
                1L,
                null,
                null,
                null,
                List.of(),
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
                2800,
                8400,
                0.35d,
                0.55d,
                true,
                30L,
                false,
                1,
                true
        );
        List<RetrievalResult> repositoryResults = List.of(
                new RetrievalResult(
                        "keyword",
                        1L,
                        10L,
                        301L,
                        "退款规则",
                        4,
                        "退款一般在3个工作日内到账",
                        "到账时效",
                        0.88d,
                        Map.of("activeVersionNo", 2, "chunkVersionNo", 2)
                )
        );
        when(knowledgeRepository.findTopKByKeyword(
                10001L,
                1L,
                null,
                null,
                null,
                List.of(),
                "退款多久到账？",
                5,
                true
        )).thenReturn(repositoryResults);

        List<RetrievalResult> first = retrievalService.searchKeyword(query);
        List<RetrievalResult> second = retrievalService.searchKeyword(query);

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().metadata()).containsEntry("queryCacheHit", false);
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().metadata()).containsEntry("queryCacheHit", true);
        assertThat(second.getFirst().metadata()).containsEntry("queryCacheChannel", "keyword");
        verify(knowledgeRepository, times(1)).findTopKByKeyword(
                10001L,
                1L,
                null,
                null,
                null,
                List.of(),
                "退款多久到账？",
                5,
                true
        );
    }
}
