package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;

public record KnowledgeBase(
        long id,
        long tenantId,
        String name,
        String description,
        KnowledgeBaseVisibility visibility,
        KnowledgeBaseStatus status,
        long ownerId,
        int documentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
