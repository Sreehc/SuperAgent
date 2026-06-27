package com.superagent.agent.service;

import com.superagent.agent.config.AgentServiceProperties;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SpringAiAgentChatModelClient implements AgentPlanner.ChatModelClient {

    private final AgentServiceProperties properties;
    private final ChatModelFactory chatModelFactory;

    @Autowired
    public SpringAiAgentChatModelClient(AgentServiceProperties properties, ObservationRegistry observationRegistry) {
        this(properties, observationRegistry, settings -> createChatModel(settings, observationRegistry));
    }

    public SpringAiAgentChatModelClient(AgentServiceProperties properties) {
        this(properties, ObservationRegistry.NOOP);
    }

    SpringAiAgentChatModelClient(AgentServiceProperties properties, ChatModelFactory chatModelFactory) {
        this(properties, ObservationRegistry.NOOP, chatModelFactory);
    }

    SpringAiAgentChatModelClient(
            AgentServiceProperties properties,
            ObservationRegistry observationRegistry,
            ChatModelFactory chatModelFactory
    ) {
        this.properties = properties;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public String generate(String prompt) throws Exception {
        return generateDecision(prompt, List.of()).content();
    }

    @Override
    public AgentPlanner.ModelDecisionResponse generateDecision(String prompt, List<ToolCallback> toolCallbacks) throws Exception {
        ChatModelSettings settings = new ChatModelSettings(
                properties.getAi().getBaseUrl(),
                properties.getAi().getApiKey(),
                properties.getAi().getChatModel(),
                Math.max(1, properties.getAi().getConnectTimeoutMillis()),
                Math.max(1, properties.getAi().getReadTimeoutMillis())
        );
        validate(settings);
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(settings.model());
        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            optionsBuilder
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false);
        }
        ChatResponse response = chatModelFactory.create(settings)
                .call(new Prompt(prompt, optionsBuilder.build()));
        Generation generation = response == null ? null : response.getResult();
        if (generation != null && generation.getOutput() != null && generation.getOutput().hasToolCalls()) {
            var toolCall = generation.getOutput().getToolCalls().getFirst();
            return AgentPlanner.ModelDecisionResponse.toolCall(toolCall.name(), toolCall.arguments());
        }
        String content = generation == null || generation.getOutput() == null ? "" : generation.getOutput().getText().trim();
        if (content.isBlank()) {
            throw new IllegalStateException("Agent chat model returned empty content");
        }
        return AgentPlanner.ModelDecisionResponse.content(content);
    }

    private static ChatModel createChatModel(ChatModelSettings settings) {
        return createChatModel(settings, ObservationRegistry.NOOP);
    }

    private static ChatModel createChatModel(ChatModelSettings settings, ObservationRegistry observationRegistry) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.connectTimeoutMillis());
        requestFactory.setReadTimeout(settings.readTimeoutMillis());
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .completionsPath("/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .webClientBuilder(WebClient.builder())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.model()).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build())
                .observationRegistry(observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry)
                .build();
    }

    private void validate(ChatModelSettings settings) {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()
                || settings.apiKey() == null || settings.apiKey().isBlank()
                || settings.model() == null || settings.model().isBlank()) {
            throw new IllegalStateException("Agent chat model configuration is incomplete");
        }
    }

    public record ChatModelSettings(
            String baseUrl,
            String apiKey,
            String model,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
    }

    @FunctionalInterface
    public interface ChatModelFactory {
        ChatModel create(ChatModelSettings settings);
    }
}
