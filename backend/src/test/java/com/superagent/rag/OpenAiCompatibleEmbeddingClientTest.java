package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.service.EmbeddingClient;
import com.superagent.rag.service.OpenAiCompatibleEmbeddingClient;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryEmbeddingProviderAndReturnVectors() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            int current = requests.incrementAndGet();
            byte[] body = current == 1
                    ? "{\"error\":\"temporary\"}".getBytes(StandardCharsets.UTF_8)
                    : """
                    {
                      "model": "text-embedding-3-small",
                      "data": [
                        { "index": 0, "embedding": [0.1, 0.2, 0.3] }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(current == 1 ? 500 : 200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                properties(3, 0L, 3),
                runtimeSettings("http://127.0.0.1:" + server.getAddress().getPort(), "text-embedding-3-small", "sk-test")
        );

        EmbeddingClient.EmbeddingResult result = client.embed(10001L, List.of("refund rule"));

        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
        assertThat(result.dimension()).isEqualTo(3);
        assertThat(result.vectors()).hasSize(1);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void shouldFailAfterRetryLimitExhausted() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"error\":\"down\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                properties(3, 0L, 3),
                runtimeSettings("http://127.0.0.1:" + server.getAddress().getPort(), "text-embedding-3-small", "sk-test")
        );

        assertThatThrownBy(() -> client.embed(10001L, List.of("refund rule")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("after retries");
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void shouldUseRuntimeEmbeddingSettingsPerTenant() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            requests.incrementAndGet();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(requestBody).contains("tenant-specific-embedding-model");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer sk-tenant");
            byte[] body = """
                    {
                      "model": "tenant-specific-embedding-model",
                      "data": [
                        { "index": 0, "embedding": [0.1, 0.2, 0.3] }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                properties(1, 0L, 3),
                runtimeSettings("http://127.0.0.1:" + server.getAddress().getPort(), "tenant-specific-embedding-model", "sk-tenant")
        );

        EmbeddingClient.EmbeddingResult result = client.embed(10002L, List.of("refund rule"));

        assertThat(result.model()).isEqualTo("tenant-specific-embedding-model");
        assertThat(requests.get()).isEqualTo(1);
    }

    private SuperAgentProperties properties(int attempts, long backoffMillis, int dimension) {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAi().setEmbeddingProvider("openai-compatible");
        properties.getAi().setEmbeddingModel("text-embedding-3-small");
        properties.getAi().setEmbeddingDimension(dimension);
        properties.getAi().setEmbeddingMaxAttempts(attempts);
        properties.getAi().setEmbeddingRetryBackoffMillis(backoffMillis);
        properties.getAi().setOpenaiCompatibleBaseUrl("https://api.example.com/v1");
        properties.getAi().setApiKey("sk-test");
        properties.getAi().setChatModel("gpt-4.1-mini");
        properties.getAi().setRerankEnabled(false);
        return properties;
    }

    private RuntimeSettingsService runtimeSettings(String baseUrl, String embeddingModel, String apiKey) {
        return new RuntimeSettingsService(
                null,
                null,
                null,
                properties(1, 0L, 3)
        ) {
            @Override
            public ModelSettings resolveModelSettings(long tenantId) {
                return new ModelSettings("openai-compatible", baseUrl, "gpt-4.1-mini", embeddingModel, apiKey);
            }
        };
    }
}
