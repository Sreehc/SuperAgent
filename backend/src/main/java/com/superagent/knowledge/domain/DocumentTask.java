package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;

public record DocumentTask(
        long id,
        long tenantId,
        long documentId,
        DocumentTaskType taskType,
        DocumentTaskStatus status,
        int attemptCount,
        String inputSummary,
        String outputSummary,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
