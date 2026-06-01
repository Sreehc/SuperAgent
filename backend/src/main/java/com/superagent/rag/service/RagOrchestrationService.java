package com.superagent.rag.service;

import com.superagent.chat.service.ConversationService;
import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagResponse;
import com.superagent.rag.domain.RagResponseDiagnostics;
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
        RagSupportService.EffectiveRagSettings effectiveSettings = ragSupportService.resolveEffectiveSettings(ragOptions);
        String rewrittenQuestion = ragSupportService.rewriteQuestion(question, recentMessages, effectiveSettings);
        List<String> subQuestions = ragSupportService.splitSubQuestions(rewrittenQuestion, effectiveSettings);
        RagSearchQuery rootQuery = ragSupportService.resolveSearchQuery(
                question,
                rewrittenQuestion,
                rewrittenQuestion,
                1,
                knowledgeBaseId,
                effectiveSettings
        );
        int perQuestionBudget = Math.max(1, rootQuery.evidenceLimit() / Math.max(1, subQuestions.size()));

        List<RagEvidence> fusedEvidences = new ArrayList<>();
        List<RagResponseDiagnostics.RetrievalStep> retrievalSteps = new ArrayList<>();
        for (int index = 0; index < subQuestions.size(); index++) {
            RagSearchQuery query = ragSupportService.resolveSearchQuery(
                    question,
                    rewrittenQuestion,
                    subQuestions.get(index),
                    index + 1,
                    knowledgeBaseId,
                    effectiveSettings
            );
            List<RetrievalResult> vectorResults = retrievalService.searchVector(query);
            List<RetrievalResult> keywordResults = retrievalService.searchKeyword(query);
            List<RagEvidence> fused = ragSupportService.fuseWithRrf(vectorResults, keywordResults, query.rrfK());
            List<RagEvidence> selected = ragSupportService.applyThresholdAndBudget(
                    query.subQuestion(),
                    deduplicateByChunk(fused),
                    query.minRelevanceScore(),
                    perQuestionBudget
            );
            fusedEvidences.addAll(selected);
            retrievalSteps.add(new RagResponseDiagnostics.RetrievalStep(
                    query,
                    vectorResults,
                    keywordResults,
                    fused,
                    selected
            ));
        }

        List<RagEvidence> filtered = ragSupportService.applyTotalBudget(
                deduplicateByChunk(fusedEvidences),
                rootQuery.evidenceLimit()
        );

        List<RagEvidence> reranked = filtered;
        RagResponseDiagnostics.RerankStep rerankStep;
        if (rootQuery.rerankEnabled()) {
            reranked = rerankClient.rerank(rewrittenQuestion, filtered);
            rerankStep = new RagResponseDiagnostics.RerankStep(
                    true,
                    "configured",
                    "configured",
                    "success",
                    null,
                    filtered.size(),
                    reranked.size()
            );
        } else {
            rerankStep = new RagResponseDiagnostics.RerankStep(
                    false,
                    null,
                    null,
                    "skipped",
                    "disabled_by_config",
                    filtered.size(),
                    filtered.size()
            );
        }

        RagAnswer answer = reranked.isEmpty()
                ? ragChatComposer.noEvidence(question)
                : ragChatComposer.answer(question, rewrittenQuestion, knowledgeBaseId, memoryContext, reranked);

        return new RagResponse(
                rewrittenQuestion,
                subQuestions,
                reranked,
                answer,
                new RagResponseDiagnostics(
                        summarizeMemory(recentMessages),
                        retrievalSteps,
                        rerankStep,
                        reranked.isEmpty() ? "no_evidence_fallback_prompt" : "rag_prompt_with_evidence_" + reranked.size(),
                        summarizeModelOutput(answer)
                )
        );
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

    private String summarizeMemory(List<String> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "recent_messages=0";
        }
        return "recent_messages=" + recentMessages.size() + ", latest=" + abbreviate(recentMessages.getLast(), 120);
    }

    private String summarizeModelOutput(RagAnswer answer) {
        return "answer_chars=" + answer.fullText().length() + ", recommendations=" + answer.recommendations().size();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
