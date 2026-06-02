package com.superagent.agent.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AdminAgentRunStep(
        long id,
        int stepNo,
        String phase,
        String status,
        String decisionSummary,
        String observationSummary,
        String selectedToolId,
        String selectedToolReason,
        String errorMessage,
        Map<String, Object> metadata,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
