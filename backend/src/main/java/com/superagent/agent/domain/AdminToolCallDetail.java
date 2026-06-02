package com.superagent.agent.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AdminToolCallDetail(
        long id,
        long agentRunId,
        String toolId,
        Long pluginId,
        String pluginVersion,
        String requestSummary,
        String responseSummary,
        String status,
        Integer latencyMs,
        String errorMessage,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
