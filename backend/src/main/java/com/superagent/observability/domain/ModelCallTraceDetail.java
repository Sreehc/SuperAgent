package com.superagent.observability.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record ModelCallTraceDetail(
        long id,
        Long stageId,
        String provider,
        String model,
        String callType,
        String promptSummary,
        String outputSummary,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String status,
        String errorMessage,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
