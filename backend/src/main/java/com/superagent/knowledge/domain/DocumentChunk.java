package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentChunk(
        long id,
        long tenantId,
        long documentId,
        Long parentChunkId,
        int chunkNo,
        String sectionTitle,
        String content,
        String contentHash,
        int charCount,
        Integer tokenCount,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
