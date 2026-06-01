package com.superagent.chat.domain;

import java.time.OffsetDateTime;

public record ExchangeTraceStage(
        long id,
        long tenantId,
        long exchangeId,
        String stageCode,
        String status,
        String inputSummary,
        String outputSummary,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
