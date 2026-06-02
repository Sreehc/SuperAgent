package com.superagent.settings.domain;

import java.util.List;

public record ToolSettings(
        boolean webSearchEnabled,
        boolean httpToolEnabled,
        boolean graphToolEnabled,
        boolean codeExecutionEnabled,
        int toolTimeoutMs,
        String searchProvider,
        List<String> allowedHttpDomains
) {
}
