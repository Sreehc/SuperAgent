package com.superagent.chat.domain;

import java.math.BigDecimal;

public record ConversationReference(
        long id,
        long tenantId,
        long exchangeId,
        long documentId,
        long chunkId,
        int ordinal,
        String title,
        String quote,
        BigDecimal score,
        String sourceUri
) {
}
