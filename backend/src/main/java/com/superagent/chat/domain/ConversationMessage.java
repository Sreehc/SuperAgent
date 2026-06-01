package com.superagent.chat.domain;

import java.time.OffsetDateTime;

public record ConversationMessage(
        long id,
        long tenantId,
        long sessionId,
        MessageRole role,
        String content,
        String status,
        Integer tokenCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
