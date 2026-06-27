package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.agent.config.AgentServiceProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class SpringAiAgentChatModelClientTest {

    @Test
    void shouldSendPromptThroughSpringAiChatModel() throws Exception {
        AtomicReference<SpringAiAgentChatModelClient.ChatModelSettings> capturedSettings = new AtomicReference<>();
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        SpringAiAgentChatModelClient client = new SpringAiAgentChatModelClient(
                properties(),
                settings -> {
                    capturedSettings.set(settings);
                    return prompt -> {
                        capturedPrompt.set(prompt);
                        return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"action\":\"FINAL_ANSWER\"}"))));
                    };
                }
        );

        String response = client.generate("choose next action");

        assertThat(response).isEqualTo("{\"action\":\"FINAL_ANSWER\"}");
        assertThat(capturedSettings.get().baseUrl()).isEqualTo("https://tenant.example/v1");
        assertThat(capturedSettings.get().apiKey()).isEqualTo("sk-agent");
        assertThat(capturedSettings.get().model()).isEqualTo("gpt-4.1-mini");
        assertThat(capturedPrompt.get().getContents()).contains("choose next action");
    }

    @Test
    void shouldFailWhenSpringAiChatReturnsBlankContent() {
        SpringAiAgentChatModelClient client = new SpringAiAgentChatModelClient(
                properties(),
                settings -> prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("  "))))
        );

        assertThatThrownBy(() -> client.generate("choose next action"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void shouldPassToolCallbacksToSpringAiAndReturnModelToolCall() throws Exception {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("web.search")
                        .description("search")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "{}";
            }
        };
        SpringAiAgentChatModelClient client = new SpringAiAgentChatModelClient(
                properties(),
                settings -> prompt -> {
                    capturedPrompt.set(prompt);
                    AssistantMessage message = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "call_1",
                                    "function",
                                    "web.search",
                                    "{\"question\":\"Spring AI\"}"
                            )))
                            .build();
                    return new ChatResponse(List.of(new Generation(message)));
                }
        );

        AgentPlanner.ModelDecisionResponse response = client.generateDecision("choose next action", List.of(callback));

        assertThat(response.toolName()).isEqualTo("web.search");
        assertThat(response.toolArguments()).isEqualTo("{\"question\":\"Spring AI\"}");
        assertThat(capturedPrompt.get().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions options = (ToolCallingChatOptions) capturedPrompt.get().getOptions();
        assertThat(options.getToolCallbacks()).containsExactly(callback);
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
    }

    private AgentServiceProperties properties() {
        AgentServiceProperties properties = new AgentServiceProperties();
        properties.getAi().setBaseUrl("https://tenant.example/v1");
        properties.getAi().setApiKey("sk-agent");
        properties.getAi().setChatModel("gpt-4.1-mini");
        properties.getAi().setConnectTimeoutMillis(3_000);
        properties.getAi().setReadTimeoutMillis(10_000);
        return properties;
    }

    @FunctionalInterface
    private interface TestChatModel extends ChatModel {
    }
}
