package com.superagent.observability.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record RerankTraceDetail(
        long id,
        String provider,
        String model,
        boolean enabled,
        String skippedReason,
        int inputCount,
        int outputCount,
        Integer latencyMs,
        String status,
        String errorMessage,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
