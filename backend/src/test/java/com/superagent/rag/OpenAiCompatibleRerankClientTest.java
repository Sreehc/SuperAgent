package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.service.OpenAiCompatibleRerankClient;
import com.superagent.rag.service.RerankClient;
import com.superagent.settings.domain.RerankSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpenAiCompatibleRerankClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnRerankedEvidenceWhenProviderSucceeds() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rerank", exchange -> {
            byte[] body = """
                    {
                      "model": "bge-reranker-large",
                      "data": [
                        { "index": 1, "rank": 0, "score": 0.95 },
                        { "index": 0, "rank": 1, "score": 0.72 }
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

        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveRerankSettings(10001L)).thenReturn(new RerankSettings(
                true,
                "openai-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "bge-reranker-large",
                "rk-test"
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        OpenAiCompatibleRerankClient client = new OpenAiCompatibleRerankClient(runtimeSettingsService);
        RerankClient.RerankResult result = client.rerank("退款规则", List.of(
                evidence(10L, 100L, "文档A", "原始结果A"),
                evidence(11L, 101L, "文档B", "更相关结果B")
        ));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.evidences()).hasSize(2);
        assertThat(result.evidences().getFirst().chunkId()).isEqualTo(101L);
        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(result.model()).isEqualTo("bge-reranker-large");
    }

    @Test
    void shouldSkipWhenRerankDisabledOrConfigIncomplete() {
        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveRerankSettings(10001L)).thenReturn(new RerankSettings(
                false,
                "openai-compatible",
                null,
                null,
                null
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        OpenAiCompatibleRerankClient client = new OpenAiCompatibleRerankClient(runtimeSettingsService);
        RerankClient.RerankResult result = client.rerank("退款规则", List.of(evidence(10L, 100L, "文档A", "原始结果A")));

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.skippedReason()).isEqualTo("disabled_by_config");
    }

    @Test
    void shouldFallbackToOriginalOrderWhenProviderFails() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rerank", exchange -> {
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        RuntimeSettingsService runtimeSettingsService = Mockito.mock(RuntimeSettingsService.class);
        Mockito.when(runtimeSettingsService.resolveRerankSettings(10001L)).thenReturn(new RerankSettings(
                true,
                "openai-compatible",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "bge-reranker-large",
                "rk-test"
        ));
        TenantContextHolder.set(new TenantContext(10001L, TenantRole.OWNER));

        List<RagEvidence> original = List.of(
                evidence(10L, 100L, "文档A", "原始结果A"),
                evidence(11L, 101L, "文档B", "原始结果B")
        );
        OpenAiCompatibleRerankClient client = new OpenAiCompatibleRerankClient(runtimeSettingsService);
        RerankClient.RerankResult result = client.rerank("退款规则", original);

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).isEqualTo("provider_error");
        assertThat(result.evidences()).containsExactlyElementsOf(original);
    }

    private RagEvidence evidence(long documentId, long chunkId, String title, String content) {
        return new RagEvidence(
                "vector",
                1L,
                documentId,
                chunkId,
                title,
                1,
                title,
                content,
                0.8d,
                Map.of()
        );
    }
}
