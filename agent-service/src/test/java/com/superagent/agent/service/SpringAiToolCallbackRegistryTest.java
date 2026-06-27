package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class SpringAiToolCallbackRegistryTest {

    @Test
    void shouldExposeToolCallbacksThatExecuteThroughCurrentToolExecutionService() {
        ToolRegistryService toolRegistryService = mock(ToolRegistryService.class);
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
        ToolSpec spec = new ToolSpec(
                "web.search",
                1L,
                "core-tools",
                "0.1.0",
                "search",
                Map.of("type", "object", "properties", Map.of("question", Map.of("type", "string"))),
                Map.of(),
                10_000,
                "none",
                "standard",
                false
        );
        when(toolRegistryService.listEnabledTools(10001L)).thenReturn(Map.of("web.search", spec));
        when(toolExecutionService.execute(
                spec,
                new ToolInvocation(10001L, 20002L, 30003L, "OWNER", "web.search", Map.of("question", "Spring AI"))
        )).thenReturn(new ToolResult("web.search", "success", "返回 1 条网页搜索结果", Map.of("count", 1), null));

        SpringAiToolCallbackRegistry registry = new SpringAiToolCallbackRegistry(
                toolRegistryService,
                toolExecutionService,
                new ObjectMapper()
        );

        List<ToolCallback> callbacks = registry.toolCallbacks(10001L, 20002L, 30003L, "OWNER");
        String result = callbacks.getFirst().call("{\"question\":\"Spring AI\"}");

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.getFirst().getToolDefinition().name()).isEqualTo("web.search");
        assertThat(callbacks.getFirst().getToolDefinition().inputSchema()).contains("question");
        assertThat(result).contains("\"status\":\"success\"");
        assertThat(result).contains("\"count\":1");
    }
}
