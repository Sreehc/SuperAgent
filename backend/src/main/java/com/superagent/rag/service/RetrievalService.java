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
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeRepository knowledgeRepository;
    private final RagSupportService ragSupportService;

    public RetrievalService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            EmbeddingClient embeddingClient,
            KnowledgeRepository knowledgeRepository,
            RagSupportService ragSupportService
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.embeddingClient = embeddingClient;
        this.knowledgeRepository = knowledgeRepository;
        this.ragSupportService = ragSupportService;
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
        return knowledgeRepository.findTopKByVector(
                tenantContext.tenantId(),
                knowledgeBaseId,
                embedding.vectors().getFirst(),
                resolvedTopK
        );
    }

    public List<RetrievalResult> searchVector(RagSearchQuery query) {
        TenantContext tenantContext = requireTenantContext();
        EmbeddingClient.EmbeddingResult embedding = embeddingClient.embed(List.of(query.subQuestion()));
        return knowledgeRepository.findTopKByVector(
                tenantContext.tenantId(),
                query.knowledgeBaseId(),
                embedding.vectors().getFirst(),
                query.vectorTopK()
        );
    }

    public List<RetrievalResult> searchKeyword(RagSearchQuery query) {
        TenantContext tenantContext = requireTenantContext();
        List<RetrievalResult> results = knowledgeRepository.findTopKByKeyword(
                tenantContext.tenantId(),
                query.knowledgeBaseId(),
                query.subQuestion(),
                query.keywordTopK()
        );
        if (!results.isEmpty()) {
            return results;
        }
        return knowledgeRepository.findTopKByKeywordFallback(
                tenantContext.tenantId(),
                query.knowledgeBaseId(),
                ragSupportService.extractKeywordTerms(query.subQuestion()),
                query.keywordTopK()
        );
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
