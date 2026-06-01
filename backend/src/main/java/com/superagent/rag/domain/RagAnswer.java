package com.superagent.rag.domain;

import java.util.List;

public record RagAnswer(
        String fullText,
        List<String> deltas,
        List<String> recommendations
) {
}
