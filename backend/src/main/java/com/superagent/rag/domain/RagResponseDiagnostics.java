package com.superagent.rag.domain;

import java.util.List;

public record RagResponseDiagnostics(
        String memorySummary,
        List<RetrievalStep> retrievalSteps,
        RerankStep rerankStep,
        String promptSummary,
        String modelSummary,
        String fallbackReason,
        boolean citationAppended,
        double answerConfidenceScore,
        double answerConfidenceThreshold
) {

    public record RetrievalStep(
            RagSearchQuery query,
            List<RetrievalResult> vectorResults,
            List<RetrievalResult> keywordResults,
            List<RagEvidence> fusedResults,
            List<RagEvidence> selectedResults,
            Integer vectorLatencyMs,
            Integer keywordLatencyMs,
            Integer fusedLatencyMs,
            boolean diversityLimited,
            int belowThresholdFilteredCount,
            int perDocumentTrimmedCount,
            int charBudgetTrimmedCount,
            int evidenceLimitTrimmedCount
    ) {
    }

    public record RerankStep(
            boolean enabled,
            String provider,
            String model,
            String status,
            String skippedReason,
            String errorMessage,
            Integer latencyMs,
            int inputCount,
            int outputCount
    ) {
    }
}
