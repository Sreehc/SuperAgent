package com.superagent.agent.domain;

import java.util.Map;

public record AgentDecision(
        String thoughtSummary,
        Action action,
        String toolId,
        Map<String, Object> toolInput,
        String finalAnswer,
        Double confidence
) {
    public enum Action {
        CALL_TOOL,
        FINAL_ANSWER,
        ASK_CLARIFICATION,
        STOP_WITH_ERROR
    }

    public static AgentDecision callTool(String thought, String toolId, Map<String, Object> input, double confidence) {
        return new AgentDecision(thought, Action.CALL_TOOL, toolId, input == null ? Map.of() : input, null, confidence);
    }

    public static AgentDecision finalAnswer(String thought, String answer, double confidence) {
        return new AgentDecision(thought, Action.FINAL_ANSWER, null, Map.of(), answer, confidence);
    }

    public static AgentDecision stopWithError(String thought, String reason) {
        return new AgentDecision(thought, Action.STOP_WITH_ERROR, null, Map.of(), reason, 0.0);
    }
}
