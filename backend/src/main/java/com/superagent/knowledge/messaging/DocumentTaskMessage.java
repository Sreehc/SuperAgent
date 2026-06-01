package com.superagent.knowledge.messaging;

public record DocumentTaskMessage(
        long tenantId,
        long documentId,
        long taskId,
        String trigger
) {
}
