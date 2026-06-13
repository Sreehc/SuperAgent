package com.superagent.evaluation.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentEvalCase(
        long id,
        long suiteId,
        String caseKey,
        Map<String, Object> input,
        Map<String, Object> expected,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
