package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ChatModelClient;
import com.superagent.chat.service.OpenAiCompatibleChatModelClient;
import com.superagent.common.exception.AppException;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAiCompatibleChatModelClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCallConfiguredChatProviderAndReturnMetadata() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = """
                    {
                      "id": "chatcmpl_test",
                      "model": "gpt-4.1-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": { "role": "assistant", "content": "根据文档，退款规则包括在 7 日内提交申请。[1]" },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 128,
                        "completion_tokens": 32,
                        "total_tokens": 160
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveModelSettings(10001L)).thenReturn(new ModelSettings(
                "openai-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "gpt-4.1-mini",
                "text-embedding-3-small",
                "sk-test"
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(runtimeSettingsService);
        ChatModelClient.ModelResponse response = client.generateReply(new ChatModelClient.ModelRequest(
                "退款规则是什么？",
                "RAG",
                "SLIDING_WINDOW",
                10L,
                List.of("记忆")
        ));

        assertThat(response.fullText()).contains("[1]");
        assertThat(response.provider()).isEqualTo("openai-compatible");
        assertThat(response.model()).isEqualTo("gpt-4.1-mini");
        assertThat(response.inputTokens()).isEqualTo(128);
        assertThat(response.outputTokens()).isEqualTo(32);
        assertThat(response.finishReason()).isEqualTo("stop");
    }

    @Test
    void shouldConvertProviderFailureToAppException() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveModelSettings(10001L)).thenReturn(new ModelSettings(
                "openai-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "gpt-4.1-mini",
                "text-embedding-3-small",
                "sk-test"
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        OpenAiCompatibleChatModelClient client = new OpenAiCompatibleChatModelClient(runtimeSettingsService);

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
}
