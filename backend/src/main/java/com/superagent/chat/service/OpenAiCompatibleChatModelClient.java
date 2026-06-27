package com.superagent.chat.service;

import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.ai.SpringAiOpenAiModelFactory;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "super-agent.ai.chat-provider", havingValue = "openai-compatible")
public class OpenAiCompatibleChatModelClient implements ChatModelClient {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;
    private final ChatModelFactory chatModelFactory;

    @Autowired
    public OpenAiCompatibleChatModelClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObservationRegistry observationRegistry
    ) {
        this(properties, runtimeSettingsService, observationRegistry, settings -> SpringAiOpenAiModelFactory.createChatModel(
                new SpringAiOpenAiModelFactory.ChatModelSettings(
                        settings.baseUrl(),
                        settings.apiKey(),
                        settings.model(),
                        settings.connectTimeoutMillis(),
                        settings.readTimeoutMillis()
                ),
                observationRegistry
        ));
    }

    public OpenAiCompatibleChatModelClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService
    ) {
        this(properties, runtimeSettingsService, ObservationRegistry.NOOP);
    }

    public OpenAiCompatibleChatModelClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ChatModelFactory chatModelFactory
    ) {
        this(properties, runtimeSettingsService, ObservationRegistry.NOOP, chatModelFactory);
    }

    public OpenAiCompatibleChatModelClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObservationRegistry observationRegistry,
            ChatModelFactory chatModelFactory
    ) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public ModelResponse generateReply(ModelRequest request) {
        ModelSettings settings = resolveSettings();
        validateSettings(settings);
        ChatModelSettings chatSettings = new ChatModelSettings(
                settings.baseUrl(),
                settings.apiKey(),
                settings.chatModel(),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpConnectTimeoutMillis())),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpReadTimeoutMillis()))
        );
        try {
            ChatResponse response = chatModelFactory.create(chatSettings)
                    .call(new Prompt(request.userMessage(), OpenAiChatOptions.builder().model(chatSettings.model()).build()));
            Generation generation = response == null ? null : response.getResult();
            String content = generation == null || generation.getOutput() == null
                    ? ""
                    : generation.getOutput().getText().trim();
            if (content.isBlank()) {
                throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Chat provider returned empty content");
            }
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            String model = response.getMetadata() == null || response.getMetadata().getModel() == null || response.getMetadata().getModel().isBlank()
                    ? chatSettings.model()
                    : response.getMetadata().getModel();
            String finishReason = generation.getMetadata() == null ? null : normalizeFinishReason(generation.getMetadata().getFinishReason());
            return new ModelResponse(
                    content,
                    slice(content, 24),
                    List.of(),
                    "openai-compatible",
                    model,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    finishReason
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Failed to call chat provider");
        }
    }

    private ModelSettings resolveSettings() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return runtimeSettingsService.resolveModelSettings(tenantContext.tenantId());
    }

    private void validateSettings(ModelSettings settings) {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()
                || settings.chatModel() == null || settings.chatModel().isBlank()
                || settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Chat provider configuration is incomplete");
        }
    }

    private List<String> slice(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }

    private String normalizeFinishReason(String finishReason) {
        return finishReason == null || finishReason.isBlank() ? finishReason : finishReason.toLowerCase(Locale.ROOT);
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
