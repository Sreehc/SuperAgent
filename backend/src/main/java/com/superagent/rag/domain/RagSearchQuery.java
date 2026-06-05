package com.superagent.rag.domain;

import java.util.List;

public record RagSearchQuery(
        String originalQuestion,
        String rewrittenQuestion,
        String subQuestion,
        int subQuestionNo,
        Long knowledgeBaseId,
        Long knowledgeDomainId,
        Long chunkingProfileId,
        String category,
        List<String> tags,
        String answerMode,
        String queryUnderstandingSource,
        double queryUnderstandingConfidence,
        boolean versionConsistencyEnabled,
        boolean neighborExpansionEnabled,
        int vectorTopK,
        int keywordTopK,
        int candidateTopK,
        int rrfK,
        int neighborWindow,
        int maxChunksPerDocument,
        int evidenceLimit,
        int perQuestionEvidenceCharLimit,
        int totalEvidenceCharLimit,
        double minRelevanceScore,
        double answerConfidenceThreshold,
        boolean queryResultCacheEnabled,
        long queryResultCacheTtlSeconds,
        boolean rerankEnabled,
        int noEvidenceMinResults,
        boolean forceCitationEnabled
) {
}
