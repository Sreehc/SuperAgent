package com.superagent.chat.service;

import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "super-agent.ai.chat-provider", havingValue = "openai-compatible")
public class OpenAiCompatibleChatModelClient implements ChatModelClient {

    private final RuntimeSettingsService runtimeSettingsService;

    public OpenAiCompatibleChatModelClient(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Override
    public ModelResponse generateReply(ModelRequest request) {
        ModelSettings settings = resolveSettings();
        validateSettings(settings);

        RestClient client = RestClient.builder()
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        try {
            ChatCompletionResponse response = client.post()
                    .uri("/chat/completions")
                    .body(new ChatCompletionRequest(
                            settings.chatModel(),
                            List.of(new ChatCompletionMessage("user", request.userMessage()))
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Chat provider returned empty response");
            }
            ChatCompletionChoice choice = response.choices().getFirst();
            String content = choice.message() == null || choice.message().content() == null
                    ? ""
                    : choice.message().content().trim();
            if (content.isBlank()) {
                throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Chat provider returned empty content");
            }
            Usage usage = response.usage();
            return new ModelResponse(
                    content,
                    slice(content, 24),
                    List.of(),
                    "openai-compatible",
                    response.model() == null || response.model().isBlank() ? settings.chatModel() : response.model(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    choice.finishReason()
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

    public record ChatCompletionRequest(String model, List<ChatCompletionMessage> messages) {
    }

    public record ChatCompletionMessage(String role, String content) {
    }

    public record ChatCompletionResponse(
            String id,
            String model,
            List<ChatCompletionChoice> choices,
            Usage usage
    ) {
    }

    public record ChatCompletionChoice(
            int index,
            ChatCompletionMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
        public Usage {
        }
    }
}
