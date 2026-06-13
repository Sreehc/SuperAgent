package com.superagent.feedback.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record ConversationFeedback(
        long id,
        long tenantId,
        long sessionId,
        Long exchangeId,
        long messageId,
        long actorUserId,
        String rating,
        String comment,
        String correction,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
