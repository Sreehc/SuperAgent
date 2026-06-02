package com.superagent.agent.domain;

import java.time.OffsetDateTime;

public record AdminAgentRunSummary(
        long runId,
        long sessionId,
        Long exchangeId,
        String status,
        String memoryStrategy,
        String routeReason,
        int modelStepCount,
        int toolCallCount,
        int latestCheckpointNo,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
