package com.superagent.agent.domain;

import java.util.Map;

public record ToolInvocation(
        long tenantId,
        long runId,
        long stepId,
        String actorRole,
        String toolId,
        Map<String, Object> input
) {
}
