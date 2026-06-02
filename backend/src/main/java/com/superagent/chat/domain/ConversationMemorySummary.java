package com.superagent.chat.domain;

import java.time.OffsetDateTime;

public record ConversationMemorySummary(
        long id,
        long tenantId,
        long sessionId,
        String summaryText,
        Long coveredMessageId,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
