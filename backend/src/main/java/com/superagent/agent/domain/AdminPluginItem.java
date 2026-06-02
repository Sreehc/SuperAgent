package com.superagent.agent.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AdminPluginItem(
        long pluginId,
        String pluginKey,
        String version,
        String displayName,
        boolean enabled,
        String status,
        Map<String, Object> manifest,
        OffsetDateTime updatedAt
) {
}
