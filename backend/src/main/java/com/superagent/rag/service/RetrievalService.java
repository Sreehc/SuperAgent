package com.superagent.rag.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.knowledge.domain.KnowledgeBaseStatus;
import com.superagent.knowledge.repository.KnowledgeRepository;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeRepository knowledgeRepository;
    private final RagSupportService ragSupportService;
    private final RagQueryCache ragQueryCache;

    public RetrievalService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            EmbeddingClient embeddingClient,
            KnowledgeRepository knowledgeRepository,
            RagSupportService ragSupportService,
            RagQueryCache ragQueryCache
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.embeddingClient = embeddingClient;
        this.knowledgeRepository = knowledgeRepository;
        this.ragSupportService = ragSupportService;
        this.ragQueryCache = ragQueryCache;
    }

    public List<RetrievalResult> search(String query, Long knowledgeBaseId, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Query is required");
        }
        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (knowledgeBaseId != null) {
            var knowledgeBase = knowledgeRepository.getKnowledgeBase(tenantContext.tenantId(), knowledgeBaseId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge base not found"));
            if (!isAdmin(principal) && knowledgeBase.status() != KnowledgeBaseStatus.published) {
                throw new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Knowledge base not found");
            }
        }

        EmbeddingClient.EmbeddingResult embedding = embeddingClient.embed(List.of(query.trim()));
        int resolvedTopK = topK == null || topK < 1 ? 5 : Math.min(topK, 20);
        RagSupportService.EffectiveRagSettings settings = ragSupportService.resolveEffectiveSettings(null);
        return knowledgeRepository.findTopKByVector(
                tenantContext.tenantId(),
                knowledgeBaseId,
                embedding.vectors().getFirst(),
                resolvedTopK,
                settings.versionConsistencyEnabled()
        );
    }

    public List<RetrievalResult> searchVector(RagSearchQuery query) {
        TenantContext tenantContext = requireTenantContext();
        int resolvedTopK = Math.max(query.vectorTopK(), query.candidateTopK());
        return ragQueryCache.getOrLoad(
                "vector",
                tenantContext.tenantId(),
                query.knowledgeBaseId(),
                query.subQuestion(),
                resolvedTopK,
                query.versionConsistencyEnabled(),
                query.queryResultCacheEnabled(),
                query.queryResultCacheTtlSeconds(),
                () -> {
                    EmbeddingClient.EmbeddingResult embedding = embeddingClient.embed(List.of(query.subQuestion()));
                    return knowledgeRepository.findTopKByVector(
                            tenantContext.tenantId(),
                            query.knowledgeBaseId(),
                            embedding.vectors().getFirst(),
                            resolvedTopK,
                            query.versionConsistencyEnabled()
                    );
                }
        ).results();
    }

    public List<RetrievalResult> searchKeyword(RagSearchQuery query) {
        TenantContext tenantContext = requireTenantContext();
        int resolvedTopK = Math.max(query.keywordTopK(), query.candidateTopK());
        return ragQueryCache.getOrLoad(
                "keyword",
                tenantContext.tenantId(),
                query.knowledgeBaseId(),
                query.subQuestion(),
                resolvedTopK,
                query.versionConsistencyEnabled(),
                query.queryResultCacheEnabled(),
                query.queryResultCacheTtlSeconds(),
                () -> {
                    List<RetrievalResult> results = knowledgeRepository.findTopKByKeyword(
                            tenantContext.tenantId(),
                            query.knowledgeBaseId(),
                            query.subQuestion(),
                            resolvedTopK,
                            query.versionConsistencyEnabled()
                    );
                    if (!results.isEmpty()) {
                        return results;
                    }
                    return knowledgeRepository.findTopKByKeywordFallback(
                            tenantContext.tenantId(),
                            query.knowledgeBaseId(),
                            ragSupportService.extractKeywordTerms(query.subQuestion()),
                            resolvedTopK,
                            query.versionConsistencyEnabled()
                    );
                }
        ).results();
    }

    public List<RagEvidence> expandNeighbors(RagSearchQuery query, List<RagEvidence> evidences) {
        if (!query.neighborExpansionEnabled() || query.neighborWindow() <= 0 || evidences.isEmpty()) {
            return markBaseEvidence(evidences);
        }
        TenantContext tenantContext = requireTenantContext();
        List<RagEvidence> ranked = evidences.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(Math.max(1, query.candidateTopK()))
                .toList();
        Map<Long, List<RagEvidence>> byDocument = new LinkedHashMap<>();
        for (RagEvidence evidence : ranked) {
            byDocument.computeIfAbsent(evidence.documentId(), ignored -> new ArrayList<>()).add(evidence);
        }

        LinkedHashMap<Long, RagEvidence> merged = new LinkedHashMap<>();
        for (RagEvidence evidence : evidences) {
            merged.put(evidence.chunkId(), withNeighborFlags(evidence, false, null, 0));
        }
        for (Map.Entry<Long, List<RagEvidence>> entry : byDocument.entrySet()) {
            List<Integer> anchorChunkNos = entry.getValue().stream().map(RagEvidence::chunkNo).distinct().toList();
            List<RetrievalResult> neighbors = knowledgeRepository.findNeighborChunks(
                    tenantContext.tenantId(),
                    entry.getKey(),
                    anchorChunkNos,
                    query.neighborWindow(),
                    query.versionConsistencyEnabled()
            );
            for (RetrievalResult neighbor : neighbors) {
                if (merged.containsKey(neighbor.chunkId())) {
                    continue;
                }
                RagEvidence anchor = nearestAnchor(entry.getValue(), neighbor.chunkNo());
                int distance = Math.abs(anchor.chunkNo() - neighbor.chunkNo());
                double score = Math.max(0.05d, anchor.score() * Math.max(0.65d, 0.95d - (distance * 0.08d)));
                Map<String, Object> metadata = new LinkedHashMap<>(neighbor.metadata());
                metadata.put("neighborExpanded", true);
                metadata.put("neighborAnchorChunkNo", anchor.chunkNo());
                metadata.put("neighborDistance", distance);
                metadata.put("neighborSourceChannel", anchor.channel());
                merged.put(neighbor.chunkId(), new RagEvidence(
                        "neighbor",
                        neighbor.knowledgeBaseId(),
                        neighbor.documentId(),
                        neighbor.chunkId(),
                        neighbor.documentTitle(),
                        neighbor.chunkNo(),
                        neighbor.content(),
                        neighbor.sectionTitle(),
                        score,
                        metadata
                ));
            }
        }
        return merged.values().stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
    }

    private List<RagEvidence> markBaseEvidence(List<RagEvidence> evidences) {
        return evidences.stream()
                .map(evidence -> withNeighborFlags(evidence, false, null, 0))
                .toList();
    }

    private RagEvidence withNeighborFlags(RagEvidence evidence, boolean neighborExpanded, Integer anchorChunkNo, int distance) {
        Map<String, Object> metadata = new LinkedHashMap<>(evidence.metadata());
        metadata.putIfAbsent("neighborExpanded", neighborExpanded);
        if (anchorChunkNo != null) {
            metadata.put("neighborAnchorChunkNo", anchorChunkNo);
        }
        if (distance > 0) {
            metadata.put("neighborDistance", distance);
        }
        return new RagEvidence(
                evidence.channel(),
                evidence.knowledgeBaseId(),
                evidence.documentId(),
                evidence.chunkId(),
                evidence.documentTitle(),
                evidence.chunkNo(),
                evidence.content(),
                evidence.sectionTitle(),
                evidence.score(),
                metadata
        );
    }

    private RagEvidence nearestAnchor(List<RagEvidence> anchors, int chunkNo) {
        return anchors.stream()
                .min((left, right) -> Integer.compare(Math.abs(left.chunkNo() - chunkNo), Math.abs(right.chunkNo() - chunkNo)))
                .orElseGet(anchors::getFirst);
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.currentRole() == TenantRole.OWNER || principal.currentRole() == TenantRole.ADMIN;
    }
}
