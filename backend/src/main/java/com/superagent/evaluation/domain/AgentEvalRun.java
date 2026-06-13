package com.superagent.evaluation.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentEvalRun(
        long id,
        long suiteId,
        String suiteKey,
        String status,
        int passedCount,
        int failedCount,
        Map<String, Object> report,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime finishedAt
) {
}
