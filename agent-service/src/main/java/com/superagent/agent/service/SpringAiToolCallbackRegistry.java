package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

@Component
public class SpringAiToolCallbackRegistry {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ToolRegistryService toolRegistryService;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    public SpringAiToolCallbackRegistry(
            ToolRegistryService toolRegistryService,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper
    ) {
        this.toolRegistryService = toolRegistryService;
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
    }

    public List<ToolCallback> toolCallbacks(long tenantId, long runId, long stepId, String actorRole) {
        return toolRegistryService.listEnabledTools(tenantId).values().stream()
                .map(spec -> toCallback(spec, tenantId, runId, stepId, actorRole))
                .toList();
    }

    private ToolCallback toCallback(ToolSpec spec, long tenantId, long runId, long stepId, String actorRole) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(spec.id())
                        .description(spec.kind() == null || spec.kind().isBlank() ? spec.id() : spec.kind())
                        .inputSchema(toJson(spec.inputSchema()))
                        .build();
            }

            @Override
            public String call(String toolInput) {
                Map<String, Object> input = parseInput(toolInput);
                ToolResult result = toolExecutionService.execute(
                        spec,
                        new ToolInvocation(tenantId, runId, stepId, actorRole, spec.id(), input)
                );
                return toJson(result);
            }
        };
    }

    private Map<String, Object> parseInput(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of("rawInput", toolInput);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return "{\"status\":\"failed\",\"errorMessage\":\"tool_result_serialization_failed\"}";
        }
    }
}
