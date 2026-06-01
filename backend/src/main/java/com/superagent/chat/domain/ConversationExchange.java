package com.superagent.chat.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ConversationExchange(
        long id,
        long tenantId,
        long sessionId,
        long userMessageId,
        Long assistantMessageId,
        ExecutionMode executionMode,
        String status,
        String routeReason,
        BigDecimal routeConfidence,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
