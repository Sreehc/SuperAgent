package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ToolAccessPolicyService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> HIGH_RISK_TOOLS = Set.of("http.request", "python.sandbox");

    private final TenantRuntimeSettingsService runtimeSettingsService;
    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper;

    public ToolAccessPolicyService(
            TenantRuntimeSettingsService runtimeSettingsService,
            AgentRunRepository agentRunRepository,
            ObjectMapper objectMapper
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.agentRunRepository = agentRunRepository;
        this.objectMapper = objectMapper;
    }

    public ToolAccessPolicy resolve(long tenantId, String actorRole, ToolSpec toolSpec) {
        TenantRuntimeSettingsService.ToolPolicy runtimePolicy = runtimeSettingsService.resolveToolPolicy(tenantId);
        AgentRunRepository.TenantToolBindingRow binding = agentRunRepository.findToolBinding(tenantId, toolSpec.id()).orElse(null);
        Map<String, Object> bindingConfig = parseConfig(binding == null ? null : binding.configJson());
        boolean ownerOrAdmin = isOwnerOrAdmin(actorRole);
        boolean highRisk = HIGH_RISK_TOOLS.contains(toolSpec.id()) || "high".equalsIgnoreCase(toolSpec.riskLevel());
        boolean featureEnabled = isFeatureEnabled(toolSpec.id(), runtimePolicy);
        boolean bindingEnabled = binding == null ? !highRisk : binding.enabled();
        List<String> allowedDomains = getStringList(bindingConfig, "allowedDomains", runtimePolicy.allowedHttpDomains());
        List<String> allowedWriteDomains = getStringList(bindingConfig, "allowedWriteDomains", allowedDomains);
        return new ToolAccessPolicy(
                featureEnabled && bindingEnabled && (!highRisk || ownerOrAdmin),
                featureEnabled,
                bindingEnabled,
                highRisk,
                ownerOrAdmin,
                Math.max(100, getInt(bindingConfig, "timeoutMs", Math.max(runtimePolicy.toolTimeoutMs(), toolSpec.timeoutMs()))),
                Math.max(0, getInt(bindingConfig, "maxRetries", parseRetries(toolSpec.retryPolicy()))),
                firstNonBlank(getString(bindingConfig, "searchProvider", runtimePolicy.searchProvider()), runtimePolicy.searchProvider()),
                Math.max(1, getInt(bindingConfig, "maxResults", 5)),
                getMap(bindingConfig.get("parameterTemplate")),
                resolveSecrets(tenantId, toolSpec.id(), bindingConfig),
                allowedDomains,
                allowedWriteDomains,
                uppercase(getStringList(bindingConfig, "allowedMethods", List.of("GET")))
        );
    }

    private Map<String, String> resolveSecrets(long tenantId, String toolId, Map<String, Object> bindingConfig) {
        Map<String, String> storedSecrets = agentRunRepository.findToolSecrets(tenantId, toolId);
        Map<String, String> resolved = new LinkedHashMap<>(storedSecrets);
        Object secretRefsValue = bindingConfig.get("secretRefs");
        if (secretRefsValue instanceof Map<?, ?> refs) {
            refs.forEach((alias, secretKey) -> {
                if (alias != null && secretKey != null) {
                    String secret = storedSecrets.get(String.valueOf(secretKey));
                    if (secret != null) {
                        resolved.put(String.valueOf(alias), secret);
                    }
                }
            });
        }
        return resolved;
    }

    private boolean isFeatureEnabled(String toolId, TenantRuntimeSettingsService.ToolPolicy runtimePolicy) {
        return switch (toolId) {
            case "web.search", "web.fetch" -> runtimePolicy.webSearchEnabled();
            case "http.request" -> runtimePolicy.httpToolEnabled();
            case "graph.query" -> runtimePolicy.graphToolEnabled();
            case "python.sandbox" -> runtimePolicy.codeExecutionEnabled();
            default -> true;
        };
    }

    private boolean isOwnerOrAdmin(String actorRole) {
        if (actorRole == null) {
            return false;
        }
        String normalized = actorRole.trim().toUpperCase(Locale.ROOT);
        return "OWNER".equals(normalized) || "ADMIN".equals(normalized);
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> getMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            if (key != null) {
                copy.put(String.valueOf(key), entryValue);
            }
        });
        return copy;
    }

    private List<String> getStringList(Map<String, Object> map, String key, List<String> fallback) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return fallback;
    }

    private List<String> uppercase(List<String> values) {
        return values.stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private int getInt(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String getString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int parseRetries(String retryPolicy) {
        if (retryPolicy == null || retryPolicy.isBlank() || "none".equalsIgnoreCase(retryPolicy)) {
            return 0;
        }
        if (retryPolicy.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(retryPolicy);
        }
        if (retryPolicy.startsWith("retry:")) {
            try {
                return Integer.parseInt(retryPolicy.substring("retry:".length()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 1;
    }

    public record ToolAccessPolicy(
            boolean executable,
            boolean featureEnabled,
            boolean bindingEnabled,
            boolean highRisk,
            boolean ownerOrAdmin,
            int timeoutMs,
            int maxRetries,
            String searchProvider,
            int maxResults,
            Map<String, Object> parameterTemplate,
            Map<String, String> secrets,
            List<String> allowedDomains,
            List<String> allowedWriteDomains,
            List<String> allowedMethods
    ) {
    }
}
