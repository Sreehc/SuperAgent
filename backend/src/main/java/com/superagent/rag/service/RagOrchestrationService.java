package com.superagent.rag.service;

import com.superagent.chat.service.ConversationService;
import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagResponse;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RagOrchestrationService {

    private final RetrievalService retrievalService;
    private final RagSupportService ragSupportService;
    private final RerankClient rerankClient;
    private final RagChatComposer ragChatComposer;

    public RagOrchestrationService(
            RetrievalService retrievalService,
            RagSupportService ragSupportService,
            RerankClient rerankClient,
            RagChatComposer ragChatComposer
    ) {
        this.retrievalService = retrievalService;
        this.ragSupportService = ragSupportService;
        this.rerankClient = rerankClient;
        this.ragChatComposer = ragChatComposer;
    }

    public RagResponse answer(
            String question,
            Long knowledgeBaseId,
            List<String> recentMessages,
            ConversationService.RagOptions ragOptions
    ) {
        String memoryContext = ragSupportService.assembleMemory(recentMessages, question);
        String rewrittenQuestion = ragSupportService.rewriteQuestion(question, recentMessages, ragOptions);
        List<String> subQuestions = ragSupportService.splitSubQuestions(rewrittenQuestion, ragOptions);
        RagSearchQuery rootQuery = ragSupportService.resolveSearchQuery(
                question,
                rewrittenQuestion,
                rewrittenQuestion,
                1,
                knowledgeBaseId,
                ragOptions
        );
        int perQuestionBudget = Math.max(1, rootQuery.evidenceLimit() / Math.max(1, subQuestions.size()));

        List<RagEvidence> fusedEvidences = new ArrayList<>();
        for (int index = 0; index < subQuestions.size(); index++) {
            RagSearchQuery query = ragSupportService.resolveSearchQuery(
                    question,
                    rewrittenQuestion,
                    subQuestions.get(index),
                    index + 1,
                    knowledgeBaseId,
                    ragOptions
            );
            List<RetrievalResult> vectorResults = retrievalService.searchVector(query);
            List<RetrievalResult> keywordResults = retrievalService.searchKeyword(query);
            List<RagEvidence> fused = ragSupportService.fuseWithRrf(vectorResults, keywordResults, query.rrfK());
            fusedEvidences.addAll(ragSupportService.applyThresholdAndBudget(
                    query.subQuestion(),
                    deduplicateByChunk(fused),
                    query.minRelevanceScore(),
                    perQuestionBudget
            ));
        }

        List<RagEvidence> filtered = ragSupportService.applyTotalBudget(
                deduplicateByChunk(fusedEvidences),
                rootQuery.evidenceLimit()
        );

        List<RagEvidence> reranked = filtered;
        if (rootQuery.rerankEnabled()) {
            reranked = rerankClient.rerank(rewrittenQuestion, filtered);
        }

        RagAnswer answer = reranked.isEmpty()
                ? ragChatComposer.noEvidence(question)
                : ragChatComposer.answer(question, rewrittenQuestion, knowledgeBaseId, memoryContext, reranked);

        return new RagResponse(rewrittenQuestion, subQuestions, reranked, answer);
    }

    private List<RagEvidence> deduplicateByChunk(List<RagEvidence> evidences) {
        java.util.LinkedHashMap<Long, RagEvidence> deduplicated = new java.util.LinkedHashMap<>();
        for (RagEvidence evidence : evidences.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList()) {
            deduplicated.putIfAbsent(evidence.chunkId(), evidence);
        }
        return deduplicated.values().stream().toList();
    }
}
