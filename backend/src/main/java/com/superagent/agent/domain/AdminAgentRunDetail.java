package com.superagent.agent.domain;

import java.util.List;

public record AdminAgentRunDetail(
        AdminAgentRunSummary summary,
        List<AdminAgentRunStep> steps,
        List<AdminAgentCheckpoint> checkpoints,
        List<AdminToolCallDetail> toolCalls
) {
}
