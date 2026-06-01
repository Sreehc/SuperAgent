package com.superagent.settings.domain;

public record RagSettings(
        boolean rewriteEnabled,
        boolean subQuestionEnabled,
        int maxSubQuestions,
        int vectorTopK,
        int keywordTopK,
        int rrfK,
        boolean rerankEnabled,
        int evidenceLimit,
        int perQuestionEvidenceCharLimit,
        int totalEvidenceCharLimit,
        double minRelevanceScore
) {
}
