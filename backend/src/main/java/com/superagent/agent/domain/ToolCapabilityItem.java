package com.superagent.agent.domain;

import java.util.List;

public record ToolCapabilityItem(
        String toolId,
        String name,
        String kind,
        String riskLevel,
        boolean enabled,
        boolean executable,
        boolean requiresConfirmation,
        String reason,
        String description,
        List<String> configuredSecrets
) {
}
