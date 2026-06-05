package com.superagent.settings.domain;

public record RagSettings(
        boolean queryUnderstandingEnabled,
        boolean decompositionEnabled,
        boolean rewriteEnabled,
        boolean subQuestionEnabled,
        boolean versionConsistencyEnabled,
        boolean neighborExpansionEnabled,
        int maxSubQuestions,
        int vectorTopK,
        int keywordTopK,
        int candidateTopK,
        int rrfK,
        boolean rerankEnabled,
        int neighborWindow,
        int maxChunksPerDocument,
        int evidenceLimit,
        int perQuestionEvidenceCharLimit,
        int totalEvidenceCharLimit,
        double minRelevanceScore,
        int noEvidenceMinResults,
        boolean forceCitationEnabled
) {
}
