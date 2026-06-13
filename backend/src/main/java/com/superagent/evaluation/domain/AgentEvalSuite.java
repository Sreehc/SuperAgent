package com.superagent.evaluation.domain;

import java.time.OffsetDateTime;

public record AgentEvalSuite(
        long id,
        Long tenantId,
        String suiteKey,
        String name,
        String description,
        int caseCount,
        int runCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
