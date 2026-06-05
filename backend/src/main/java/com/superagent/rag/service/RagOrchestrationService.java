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
    private final QueryUnderstandingService queryUnderstandingService;
    private final RerankClient rerankClient;
    private final RagChatComposer ragChatComposer;
    private final RagRuntimeMetrics ragRuntimeMetrics;

    public RagOrchestrationService(
            RetrievalService retrievalService,
            RagSupportService ragSupportService,
            QueryUnderstandingService queryUnderstandingService,
            RerankClient rerankClient,
            RagChatComposer ragChatComposer,
            RagRuntimeMetrics ragRuntimeMetrics
    ) {
        this.retrievalService = retrievalService;
        this.ragSupportService = ragSupportService;
        this.queryUnderstandingService = queryUnderstandingService;
        this.rerankClient = rerankClient;
        this.ragChatComposer = ragChatComposer;
        this.ragRuntimeMetrics = ragRuntimeMetrics;
    }

    public RagResponse answer(
            String question,
            Long knowledgeBaseId,
            List<String> recentMessages,
            ConversationService.RagOptions ragOptions
    ) {
        String memoryContext = ragSupportService.assembleMemory(recentMessages, question);
        RagSupportService.EffectiveRagSettings effectiveSettings = ragSupportService.resolveEffectiveSettings(ragOptions);
        QueryUnderstandingService.QueryUnderstandingResult understanding = queryUnderstandingService.understand(
                question,
                recentMessages,
                effectiveSettings,
                ragSupportService
        );
        String rewrittenQuestion = understanding.rewrittenQuestion();
        List<String> subQuestions = understanding.subQuestions();
        RagSearchQuery rootQuery = ragSupportService.resolveSearchQuery(
                question,
                rewrittenQuestion,
                rewrittenQuestion,
                1,
                knowledgeBaseId,
                ragOptions,
                understanding.answerMode(),
                understanding.source(),
                understanding.confidence(),
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
                    ragOptions,
                    understanding.answerMode(),
                    understanding.source(),
                    understanding.confidence(),
                    effectiveSettings
            );
            long vectorStartedAt = System.nanoTime();
            List<RetrievalResult> vectorResults = retrievalService.searchVector(query);
            Integer vectorLatencyMs = elapsedMillis(vectorStartedAt);

            long keywordStartedAt = System.nanoTime();
            List<RetrievalResult> keywordResults = retrievalService.searchKeyword(query);
            Integer keywordLatencyMs = elapsedMillis(keywordStartedAt);

            long fusedStartedAt = System.nanoTime();
            List<RagEvidence> fused = ragSupportService.fuseWithRrf(vectorResults, keywordResults, query.rrfK());
            List<RagEvidence> expanded = retrievalService.expandNeighbors(query, deduplicateByChunk(fused));
            RagSupportService.BudgetedEvidenceResult selectedBudget = ragSupportService.applyThresholdAndBudget(
                    query.subQuestion(),
                    expanded,
                    query.minRelevanceScore(),
                    perQuestionBudget,
                    query.maxChunksPerDocument(),
                    query.perQuestionEvidenceCharLimit()
            );
            Integer fusedLatencyMs = elapsedMillis(fusedStartedAt);
            List<RagEvidence> selected = selectedBudget.evidences();

            RagResponseDiagnostics.RetrievalStep retrievalStep = new RagResponseDiagnostics.RetrievalStep(
                    query,
                    vectorResults,
                    keywordResults,
                    expanded,
                    selected,
                    vectorLatencyMs,
                    keywordLatencyMs,
                    fusedLatencyMs,
                    selectedBudget.diversityLimited(),
                    selectedBudget.belowThresholdFilteredCount(),
                    selectedBudget.perDocumentTrimmedCount(),
                    selectedBudget.charBudgetTrimmedCount(),
                    selectedBudget.evidenceLimitTrimmedCount()
            );
            fusedEvidences.addAll(selected);
            retrievalSteps.add(retrievalStep);
            ragRuntimeMetrics.recordRetrievalStep(retrievalStep);
        }

        RagSupportService.BudgetedEvidenceResult finalBudget = ragSupportService.applyTotalBudget(
                deduplicateByChunk(fusedEvidences),
                rootQuery.evidenceLimit(),
                rootQuery.maxChunksPerDocument(),
                rootQuery.totalEvidenceCharLimit()
        );
        List<RagEvidence> filtered = finalBudget.evidences();

        List<RagEvidence> reranked = filtered;
        RagResponseDiagnostics.RerankStep rerankStep;
        String fallbackReason = null;
        if (rootQuery.rerankEnabled()) {
            long rerankStartedAt = System.nanoTime();
            try {
                RerankClient.RerankResult rerankResult = rerankClient.rerank(rewrittenQuestion, filtered);
                reranked = rerankResult.status().equals("success") ? rerankResult.evidences() : filtered;
                if (!"success".equals(rerankResult.status())) {
                    fallbackReason = "rerank_" + rerankResult.status() + "_used_filtered";
                }
                rerankStep = new RagResponseDiagnostics.RerankStep(
                        true,
                        rerankResult.provider(),
                        rerankResult.model(),
                        rerankResult.status(),
                        rerankResult.skippedReason(),
                        rerankResult.errorMessage(),
                        rerankResult.latencyMs(),
                        filtered.size(),
                        reranked.size()
                );
            } catch (Exception exception) {
                reranked = filtered;
                fallbackReason = "rerank_error_used_filtered";
                rerankStep = new RagResponseDiagnostics.RerankStep(
                        true,
                        null,
                        null,
                        "error",
                        null,
                        exception.getMessage(),
                        elapsedMillis(rerankStartedAt),
                        filtered.size(),
                        filtered.size()
                );
            }
        } else {
            rerankStep = new RagResponseDiagnostics.RerankStep(
                    false,
                    null,
                    null,
                    "skipped",
                    "disabled_by_config",
                    null,
                    null,
                filtered.size(),
                filtered.size()
            );
        }

        double answerConfidenceScore = ragSupportService.calculateAnswerConfidence(reranked);
        boolean noEvidence = reranked.isEmpty()
                || reranked.size() < rootQuery.noEvidenceMinResults()
                || answerConfidenceScore < rootQuery.answerConfidenceThreshold();
        if (reranked.isEmpty()) {
            fallbackReason = "no_selected_evidence";
        } else if (reranked.size() < rootQuery.noEvidenceMinResults()) {
            fallbackReason = "insufficient_evidence_results";
        } else if (answerConfidenceScore < rootQuery.answerConfidenceThreshold()) {
            fallbackReason = "low_answer_confidence";
        }
        List<RagEvidence> finalEvidences = noEvidence ? List.of() : reranked;
        RagAnswer answer = noEvidence
                ? ragChatComposer.noEvidence(question)
                : ragChatComposer.answer(
                        question,
                        rewrittenQuestion,
                        knowledgeBaseId,
                        memoryContext,
                        finalEvidences,
                        rootQuery.forceCitationEnabled()
                );

        RagResponse response = new RagResponse(
                rewrittenQuestion,
                subQuestions,
                finalEvidences,
                answer,
                new RagResponseDiagnostics(
                        summarizeMemory(recentMessages),
                        retrievalSteps,
                        rerankStep,
                        noEvidence ? "no_evidence_fallback_prompt" : "rag_prompt_with_evidence_" + reranked.size(),
                        summarizeModelOutput(answer),
                        fallbackReason,
                        answer.citationAppended(),
                        answerConfidenceScore,
                        rootQuery.answerConfidenceThreshold()
                )
        );
        ragRuntimeMetrics.recordAnswer(response);
        return response;
    }

    private Integer elapsedMillis(long startedAtNanos) {
        return (int) Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
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
        return "provider=" + answer.provider()
                + ", model=" + answer.model()
                + ", answer_chars=" + answer.fullText().length()
                + ", recommendations=" + answer.recommendations().size();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
