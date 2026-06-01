package com.superagent.observability.domain;

import java.time.OffsetDateTime;

public record TraceStageDetail(
        long stageId,
        String stageCode,
        String status,
        String inputSummary,
        String outputSummary,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        long durationMs
) {
}
