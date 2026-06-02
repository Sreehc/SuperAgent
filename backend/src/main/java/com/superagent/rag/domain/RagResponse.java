package com.superagent.rag.domain;

import java.util.List;

public record RagResponse(
        String rewrittenQuestion,
        List<String> subQuestions,
        List<RagEvidence> evidences,
        RagAnswer answer,
        RagResponseDiagnostics diagnostics
) {

    public static RagResponse clarification(String originalQuestion, String clarificationPrompt, String planSummary) {
        return new RagResponse(
                originalQuestion,
                List.of(originalQuestion),
                List.of(),
                new RagAnswer(
                        clarificationPrompt,
                        slice(clarificationPrompt, 18),
                        List.of("补充具体对象", "补充知识库范围"),
                        "system",
                        "clarification-fallback",
                        null,
                        null,
                        "stop"
                ),
                new RagResponseDiagnostics(
                        "recent_messages=0",
                        List.of(),
                        new RagResponseDiagnostics.RerankStep(false, null, null, "skipped", "not_applicable", null, null, 0, 0),
                        planSummary,
                        "clarification_response"
                )
        );
    }

    private static List<String> slice(String text, int chunkSize) {
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }
}
