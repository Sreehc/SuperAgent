package com.superagent.rag.domain;

public record RagSearchQuery(
        String originalQuestion,
        String rewrittenQuestion,
        String subQuestion,
        int subQuestionNo,
        Long knowledgeBaseId,
        String answerMode,
        String queryUnderstandingSource,
        double queryUnderstandingConfidence,
        int vectorTopK,
        int keywordTopK,
        int rrfK,
        int evidenceLimit,
        double minRelevanceScore,
        boolean rerankEnabled
) {
}
