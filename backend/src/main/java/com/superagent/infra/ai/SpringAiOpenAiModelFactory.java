package com.superagent.infra.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public final class SpringAiOpenAiModelFactory {

    private SpringAiOpenAiModelFactory() {
    }

    public static ChatModel createChatModel(ChatModelSettings settings) {
        return createChatModel(settings, ObservationRegistry.NOOP);
    }

    public static ChatModel createChatModel(ChatModelSettings settings, ObservationRegistry observationRegistry) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .completionsPath("/chat/completions")
                .restClientBuilder(restClientBuilder(settings.connectTimeoutMillis(), settings.readTimeoutMillis()))
                .webClientBuilder(WebClient.builder())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.model()).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(singleAttemptRetryTemplate())
                .observationRegistry(observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry)
                .build();
    }

    public static RetryTemplate singleAttemptRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(1)
                .fixedBackoff(1)
                .build();
    }

    public static RestClient.Builder restClientBuilder(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        return RestClient.builder().requestFactory(requestFactory);
    }

    public record ChatModelSettings(
            String baseUrl,
            String apiKey,
            String model,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
    }
}
