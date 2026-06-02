package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeDocument(
        long id,
        long tenantId,
        long knowledgeBaseId,
        String title,
        String fileName,
        String fileType,
        long fileSize,
        String objectKey,
        String contentHash,
        KnowledgeDocumentStatus status,
        int chunkCount,
        String errorMessage,
        String parsedText,
        String category,
        List<String> tags,
        long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
