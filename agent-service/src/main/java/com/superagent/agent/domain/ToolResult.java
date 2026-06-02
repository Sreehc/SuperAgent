package com.superagent.agent.domain;

import java.util.Map;

public record ToolResult(
        String toolId,
        String status,
        String summary,
        Map<String, Object> output,
        String errorMessage
) {
}
