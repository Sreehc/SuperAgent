package com.superagent.rag.domain;

import java.util.Map;

public record RetrievalResult(
        String channel,
        long knowledgeBaseId,
        long documentId,
        long chunkId,
        String documentTitle,
        int chunkNo,
        String content,
        String sectionTitle,
        double score,
        Map<String, Object> metadata
) {
}
