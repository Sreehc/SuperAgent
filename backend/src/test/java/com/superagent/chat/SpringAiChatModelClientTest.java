package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ChatModelClient;
import com.superagent.chat.service.OpenAiCompatibleChatModelClient;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiChatModelClientTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldMapSpringAiChatResponseToExistingModelResponse() {
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));
        AtomicReference<OpenAiCompatibleChatModelClient.ChatModelSettings> capturedSettings = new AtomicReference<>();
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
                properties(),
                runtimeSettings("https://tenant.example/v1", "sk-tenant"),
                settings -> {
                    capturedSettings.set(settings);
                    return prompt -> {
                        capturedPrompt.set(prompt);
                        return chatResponse("根据文档，退款规则包括 7 日内提交申请。[1]", "gpt-4.1-mini", 128, 32, "stop");
                    };
                }
        );

        ChatModelClient.ModelResponse response = client.generateReply(new ChatModelClient.ModelRequest(
                "退款规则是什么？",
                "RAG",
                "SLIDING_WINDOW",
                10L,
                List.of("记忆")
        ));

        assertThat(capturedSettings.get().baseUrl()).isEqualTo("https://tenant.example/v1");
        assertThat(capturedSettings.get().apiKey()).isEqualTo("sk-tenant");
        assertThat(capturedSettings.get().model()).isEqualTo("gpt-4.1-mini");
        assertThat(capturedPrompt.get().getContents()).contains("退款规则是什么？");
        assertThat(response.fullText()).contains("[1]");
        assertThat(response.provider()).isEqualTo("openai-compatible");
        assertThat(response.model()).isEqualTo("gpt-4.1-mini");
        assertThat(response.inputTokens()).isEqualTo(128);
        assertThat(response.outputTokens()).isEqualTo(32);
        assertThat(response.finishReason()).isEqualTo("stop");
    }

    @Test
    void shouldConvertSpringAiChatFailureToAppException() {
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));
        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(
                properties(),
                runtimeSettings("https://tenant.example/v1", "sk-tenant"),
                settings -> prompt -> {
                    throw new IllegalStateException("provider down");
                }
        );

        assertThatThrownBy(() -> client.generateReply(new ChatModelClient.ModelRequest(
                "退款规则是什么？",
                "RAG",
                "SLIDING_WINDOW",
                10L,
                List.of("记忆")
        )))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Failed to call chat provider");
    }

    private ChatResponse chatResponse(String content, String model, int promptTokens, int completionTokens, String finishReason) {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage(content),
                        ChatGenerationMetadata.builder().finishReason(finishReason).build()
                )),
                ChatResponseMetadata.builder()
                        .model(model)
                        .usage(new DefaultUsage(promptTokens, completionTokens, promptTokens + completionTokens))
                        .build()
        );
    }

    private SuperAgentProperties properties() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAi().setOpenaiCompatibleBaseUrl("https://api.example.com/v1");
        properties.getAi().setApiKey("sk-test");
        properties.getAi().setChatModel("gpt-4.1-mini");
        properties.getAi().setEmbeddingModel("text-embedding-3-small");
        properties.getAi().setRerankEnabled(false);
        properties.getAi().setHttpConnectTimeoutMillis(3_000L);
        properties.getAi().setHttpReadTimeoutMillis(10_000L);
        return properties;
    }

    private RuntimeSettingsService runtimeSettings(String baseUrl, String apiKey) {
        return new RuntimeSettingsService(null, null, null, properties()) {
            @Override
            public ModelSettings resolveModelSettings(long tenantId) {
                return new ModelSettings(
                        "openai-compatible",
                        baseUrl,
                        "gpt-4.1-mini",
                        "text-embedding-3-small",
                        apiKey
                );
            }
        };
    }

    @FunctionalInterface
    private interface CapturingChatModel extends ChatModel {
    }
}
