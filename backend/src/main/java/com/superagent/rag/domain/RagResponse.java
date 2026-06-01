package com.superagent.rag.domain;

import java.util.List;

public record RagResponse(
        String rewrittenQuestion,
        List<String> subQuestions,
        List<RagEvidence> evidences,
        RagAnswer answer
) {
}
