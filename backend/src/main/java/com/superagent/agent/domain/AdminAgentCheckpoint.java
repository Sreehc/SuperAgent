package com.superagent.agent.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AdminAgentCheckpoint(
        long id,
        int checkpointNo,
        Long stepId,
        String checkpointType,
        boolean stable,
        Map<String, Object> payload,
        OffsetDateTime createdAt
) {
}
