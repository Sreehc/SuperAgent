package com.superagent.settings.domain;

import com.superagent.chat.domain.MemoryStrategy;
import java.util.List;

public record AgentSettings(
        boolean enabled,
        int maxModelSteps,
        int maxToolCalls,
        boolean checkpointEnabled,
        MemoryStrategy defaultMemoryStrategy,
        boolean webSearchEnabled,
        boolean httpToolEnabled,
        boolean graphToolEnabled,
        boolean codeExecutionEnabled,
        int toolTimeoutMs,
        List<String> allowedHttpDomains
) {
}
