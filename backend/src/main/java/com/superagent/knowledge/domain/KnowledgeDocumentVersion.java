package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record KnowledgeDocumentVersion(
        long id,
        long tenantId,
        long documentId,
        int versionNo,
        Long chunkingProfileId,
        String status,
        int chunkCount,
        String graphSyncStatus,
        Map<String, Object> metadata,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
