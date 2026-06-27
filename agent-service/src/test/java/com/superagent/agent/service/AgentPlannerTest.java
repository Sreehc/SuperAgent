package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.AgentDecision;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.domain.ToolSpec;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class AgentPlannerTest {

    @Test
    void shouldUseSpringAiToolCallAsAgentDecisionWhenModelSelectsTool() {
        ToolCallback callback = toolCallback("web.search");
        SpringAiToolCallbackRegistry registry = mock(SpringAiToolCallbackRegistry.class);
        when(registry.toolCallbacks(10001L, 20002L, 0L, "OWNER")).thenReturn(List.of(callback));
        AtomicReference<List<ToolCallback>> capturedCallbacks = new AtomicReference<>();
        AgentPlanner.ChatModelClient client = new AgentPlanner.ChatModelClient() {
            @Override
            public String generate(String prompt) {
                throw new UnsupportedOperationException("structured path should not be used");
            }

            @Override
            public AgentPlanner.ModelDecisionResponse generateDecision(String prompt, List<ToolCallback> toolCallbacks) {
                capturedCallbacks.set(toolCallbacks);
                return AgentPlanner.ModelDecisionResponse.toolCall("web.search", "{\"question\":\"Spring AI\"}");
            }
        };
        AgentPlanner planner = new AgentPlanner(
                provider(client),
                provider(registry),
                new ObjectMapper(),
                true
        );

        AgentDecision decision = planner.decideInitial(
                request(),
                Map.of("web.search", spec("web.search")),
                20002L,
                0L
        );

        assertThat(capturedCallbacks.get()).containsExactly(callback);
        assertThat(decision.action()).isEqualTo(AgentDecision.Action.CALL_TOOL);
        assertThat(decision.toolId()).isEqualTo("web.search");
        assertThat(decision.toolInput()).containsEntry("question", "Spring AI");
    }

    @Test
    void shouldIncludeSpringAiStructuredOutputFormatInDecisionPrompt() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AgentPlanner.ChatModelClient client = prompt -> {
            capturedPrompt.set(prompt);
            return """
                    {"thoughtSummary":"answer directly","action":"FINAL_ANSWER","toolId":null,"toolInput":{},"finalAnswer":"ok","confidence":0.9}
                    """;
        };
        AgentPlanner planner = new AgentPlanner(
                provider(client),
                emptyProvider(),
                new ObjectMapper(),
                true
        );

        AgentDecision decision = planner.decideInitial(request(), Map.of("web.search", spec("web.search")));

        assertThat(capturedPrompt.get()).contains("JSON Schema");
        assertThat(capturedPrompt.get()).contains("thoughtSummary");
        assertThat(decision.action()).isEqualTo(AgentDecision.Action.FINAL_ANSWER);
        assertThat(decision.finalAnswer()).isEqualTo("ok");
    }

    private InternalAgentRunRequest request() {
        return new InternalAgentRunRequest(
                10001L,
                1L,
                2L,
                3L,
                4L,
                "OWNER",
                "请搜索 Spring AI",
                null,
                "SUMMARY_PLUS_WINDOW",
                List.of()
        );
    }

    private ToolSpec spec(String id) {
        return new ToolSpec(
                id,
                1L,
                "core-tools",
                "0.1.0",
                "search",
                Map.of("type", "object"),
                Map.of(),
                10_000,
                "none",
                "standard",
                false
        );
    }

    private ToolCallback toolCallback(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{}";
            }
        };
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    private <T> ObjectProvider<T> emptyProvider() {
        return provider(null);
    }
}
