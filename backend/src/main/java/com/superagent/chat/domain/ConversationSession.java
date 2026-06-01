package com.superagent.chat.domain;

import java.time.OffsetDateTime;

public record ConversationSession(
        long id,
        long tenantId,
        long ownerId,
        String title,
        MemoryStrategy memoryStrategy,
        Long knowledgeBaseId,
        ConversationStatus status,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
