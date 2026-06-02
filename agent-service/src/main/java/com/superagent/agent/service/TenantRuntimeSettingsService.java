package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TenantRuntimeSettingsService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper;

    public TenantRuntimeSettingsService(AgentRunRepository agentRunRepository, ObjectMapper objectMapper) {
        this.agentRunRepository = agentRunRepository;
        this.objectMapper = objectMapper;
    }

    public ToolPolicy resolveToolPolicy(long tenantId) {
        Map<String, Object> agent = readSection(tenantId, "agent");
        Map<String, Object> tools = readSection(tenantId, "tools");
        return new ToolPolicy(
                getBoolean(agent, "webSearchEnabled", getBoolean(tools, "webSearchEnabled", true)),
                getBoolean(agent, "httpToolEnabled", getBoolean(tools, "httpToolEnabled", false)),
                getBoolean(agent, "graphToolEnabled", getBoolean(tools, "graphToolEnabled", false)),
                getBoolean(agent, "codeExecutionEnabled", getBoolean(tools, "codeExecutionEnabled", false)),
                getInt(agent, "toolTimeoutMs", getInt(tools, "toolTimeoutMs", 10_000)),
                getString(tools, "searchProvider", "tavily"),
                getStringList(agent, "allowedHttpDomains", getStringList(tools, "allowedHttpDomains", List.of()))
        );
    }

    public ExecutionPolicy resolveExecutionPolicy(long tenantId) {
        Map<String, Object> agent = readSection(tenantId, "agent");
        return new ExecutionPolicy(
                getBoolean(agent, "enabled", true),
                getInt(agent, "maxModelSteps", 6),
                getInt(agent, "maxToolCalls", 6),
                getBoolean(agent, "checkpointEnabled", true)
        );
    }

    private Map<String, Object> readSection(long tenantId, String key) {
        return agentRunRepository.findRuntimeSetting(tenantId, key)
                .map(row -> row.get("configJson"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::parseJson)
                .orElse(Map.of());
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return defaultValue;
    }

    public record ToolPolicy(
            boolean webSearchEnabled,
            boolean httpToolEnabled,
            boolean graphToolEnabled,
            boolean codeExecutionEnabled,
            int toolTimeoutMs,
            String searchProvider,
            List<String> allowedHttpDomains
    ) {
    }

    public record ExecutionPolicy(
            boolean enabled,
            int maxModelSteps,
            int maxToolCalls,
            boolean checkpointEnabled
    ) {
    }
}
