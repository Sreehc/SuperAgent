package com.superagent.agent.domain;

import java.util.Map;

public record ToolSpec(
        String id,
        Long pluginId,
        String pluginKey,
        String pluginVersion,
        String kind,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        int timeoutMs,
        String retryPolicy,
        String riskLevel,
        boolean supportsStreaming
) {
}
