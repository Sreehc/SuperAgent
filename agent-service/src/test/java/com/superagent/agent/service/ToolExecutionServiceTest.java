package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.config.AgentServiceProperties;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolSpec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolExecutionServiceTest {

    @Mock
    private SandboxRunnerClient sandboxRunnerClient;

    @Mock
    private KnowledgeSearchService knowledgeSearchService;

    @Mock
    private GraphQueryService graphQueryService;

    @Mock
    private ToolAccessPolicyService toolAccessPolicyService;

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldUseTavilyProviderForWebSearch() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/search", exchange -> respond(exchange, 200, """
                {
                  "results": [
                    {
                      "title": "行业快讯",
                      "url": "https://example.com/news",
                      "content": "这是最新行业动态摘要",
                      "published_date": "2026-06-02"
                    }
                  ]
                }
                """));
        httpServer.start();

        AgentServiceProperties properties = new AgentServiceProperties();
        properties.getSearch().setTavilyBaseUrl("http://localhost:" + httpServer.getAddress().getPort());
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("web.search", 1L, "core-tools", "0.1.0", "web", Map.of(), Map.of(), 10_000, "none", "standard", false);
        when(toolAccessPolicyService.resolve(10001L, "ADMIN", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, false, true, 5_000, 0, "tavily", 3, Map.of(), Map.of("apiKey", "test-key"), List.of(), List.of(), List.of("GET")
                )
        );

        var result = service.execute(spec, new ToolInvocation(10001L, 1L, 1L, "ADMIN", "web.search", Map.of("question", "今天行业新闻")));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.output()).containsEntry("provider", "tavily");
        assertThat((List<?>) result.output().get("results")).hasSize(1);
    }

    @Test
    void shouldReturnRealKnowledgeSearchEvidence() {
        AgentServiceProperties properties = new AgentServiceProperties();
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                knowledgeSearchService,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("knowledge.search", 1L, "core-tools", "0.1.0", "knowledge", Map.of(), Map.of(), 10_000, "none", "standard", false);
        when(toolAccessPolicyService.resolve(10001L, "MEMBER", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, false, false, 5_000, 0, "tavily", 3, Map.of(), Map.of(), List.of(), List.of(), List.of("GET")
                )
        );
        when(knowledgeSearchService.search(10001L, "退款规则", 10L, 2)).thenReturn(List.of(
                Map.of("documentId", 7L, "chunkId", 70L, "title", "退款手册", "quote", "7 天内可申请退款", "score", 0.91)
        ));

        var result = service.execute(spec, new ToolInvocation(
                10001L,
                1L,
                1L,
                "MEMBER",
                "knowledge.search",
                Map.of("question", "退款规则", "knowledgeBaseId", 10L, "limit", 2)
        ));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.summary()).isEqualTo("返回 1 条知识库证据");
        assertThat((List<?>) result.output().get("evidence")).hasSize(1);
        assertThat(String.valueOf(((List<?>) result.output().get("evidence")).get(0))).contains("退款手册");
    }

    @Test
    void shouldExtractFetchSummaryFromHtml() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/article", exchange -> respond(exchange, 200, """
                <html>
                  <head>
                    <title>Agent 平台发布</title>
                    <meta property="article:published_time" content="2026-06-02T10:00:00Z" />
                  </head>
                  <body>
                    <article>
                      <p>SuperAgent 发布了新的企业级 Agent 闭环能力。</p>
                      <p>现在支持联网检索、图谱查询和受控代码执行。</p>
                    </article>
                  </body>
                </html>
                """));
        httpServer.start();

        AgentServiceProperties properties = new AgentServiceProperties();
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("web.fetch", 1L, "core-tools", "0.1.0", "web", Map.of(), Map.of(), 10_000, "none", "standard", false);
        when(toolAccessPolicyService.resolve(10001L, "ADMIN", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, false, true, 5_000, 0, "tavily", 3, Map.of(), Map.of(), List.of(), List.of(), List.of("GET")
                )
        );

        var result = service.execute(spec, new ToolInvocation(
                10001L,
                1L,
                1L,
                "ADMIN",
                "web.fetch",
                Map.of("url", "http://localhost:" + httpServer.getAddress().getPort() + "/article")
        ));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.output()).containsEntry("title", "Agent 平台发布");
        assertThat(String.valueOf(result.output().get("summary"))).contains("SuperAgent 发布了新的企业级 Agent 闭环能力");
        assertThat(result.output()).containsEntry("publishedAt", "2026-06-02T10:00:00Z");
    }

    @Test
    void shouldRejectHttpRequestOutsideAllowlist() {
        AgentServiceProperties properties = new AgentServiceProperties();
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("http.request", 1L, "core-tools", "0.1.0", "http", Map.of(), Map.of(), 10_000, "none", "high", false);
        when(toolAccessPolicyService.resolve(10001L, "ADMIN", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, true, true, 5_000, 0, "tavily", 3, Map.of(), Map.of(), List.of("allowed.example.com"), List.of("allowed.example.com"), List.of("GET")
                )
        );

        var result = service.execute(spec, new ToolInvocation(
                10001L,
                1L,
                1L,
                "ADMIN",
                "http.request",
                Map.of("url", "https://blocked.example.com/api", "method", "GET")
        ));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).isEqualTo("http_domain_not_allowed");
    }

    @Test
    void shouldRejectUnauthorizedWriteMethod() {
        AgentServiceProperties properties = new AgentServiceProperties();
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("http.request", 1L, "core-tools", "0.1.0", "http", Map.of(), Map.of(), 10_000, "none", "high", false);
        when(toolAccessPolicyService.resolve(10001L, "ADMIN", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, true, true, 5_000, 0, "tavily", 3, Map.of(), Map.of(), List.of("api.example.com"), List.of("api.example.com"), List.of("GET")
                )
        );

        var result = service.execute(spec, new ToolInvocation(
                10001L,
                1L,
                1L,
                "ADMIN",
                "http.request",
                Map.of("url", "https://api.example.com/write", "method", "POST", "body", "{}")
        ));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).isEqualTo("http_method_not_allowed");
    }

    @Test
    void shouldAttachSandboxLimits() {
        AgentServiceProperties properties = new AgentServiceProperties();
        ToolExecutionService service = new ToolExecutionService(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                new ObjectMapper(),
                HttpClient.newHttpClient()
        );
        ToolSpec spec = new ToolSpec("python.sandbox", 1L, "core-tools", "0.1.0", "python", Map.of(), Map.of(), 10_000, "none", "high", false);
        when(toolAccessPolicyService.resolve(10001L, "ADMIN", spec)).thenReturn(
                new ToolAccessPolicyService.ToolAccessPolicy(
                        true, true, true, true, true, 5_000, 0, "tavily", 3, Map.of(), Map.of(), List.of(), List.of(), List.of("GET")
                )
        );
        when(sandboxRunnerClient.executePython("print('ok')", 5, 32768)).thenReturn(new java.util.LinkedHashMap<>(Map.of(
                "status", "success",
                "stdout", "ok\n",
                "stderr", "",
                "exit_code", 0
        )));

        var result = service.execute(spec, new ToolInvocation(
                10001L,
                1L,
                1L,
                "ADMIN",
                "python.sandbox",
                Map.of("code", "print('ok')")
        ));

        assertThat(result.status()).isEqualTo("success");
        assertThat(((Map<?, ?>) result.output().get("limits")).get("network")).isEqualTo("disabled");
        assertThat(result.output()).containsEntry("executor", "sandbox-runner");
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
