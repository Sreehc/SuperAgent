package com.superagent.rag.domain;

import java.util.List;

public record RagAnswer(
        String fullText,
        List<String> deltas,
        List<String> recommendations,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        String finishReason,
        boolean citationAppended
) {
}
