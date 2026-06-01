package com.superagent.observability.domain;

import java.time.OffsetDateTime;

public record AdminTraceSummary(
        long exchangeId,
        long sessionId,
        long userId,
        String executionMode,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        long durationMs
) {
}
