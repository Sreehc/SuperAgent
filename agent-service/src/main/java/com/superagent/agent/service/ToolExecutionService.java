package com.superagent.agent.service;

import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutionService {

    private final TenantRuntimeSettingsService runtimeSettingsService;
    private final SandboxRunnerClient sandboxRunnerClient;
    private final GraphQueryService graphQueryService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ToolExecutionService(
            TenantRuntimeSettingsService runtimeSettingsService,
            SandboxRunnerClient sandboxRunnerClient,
            GraphQueryService graphQueryService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.sandboxRunnerClient = sandboxRunnerClient;
        this.graphQueryService = graphQueryService;
    }

    public ToolResult execute(ToolSpec spec, ToolInvocation invocation) {
        return switch (spec.id()) {
            case "knowledge.search" -> executeKnowledgeSearch(invocation);
            case "web.search" -> executeWebSearch(invocation);
            case "web.fetch" -> executeWebFetch(invocation);
            case "http.request" -> executeHttpRequest(invocation);
            case "graph.query" -> executeGraphQuery(invocation);
            case "python.sandbox" -> executePythonSandbox(invocation);
            default -> new ToolResult(spec.id(), "failed", "工具暂未实现", Map.of(), "unsupported_tool");
        };
    }

    private ToolResult executeKnowledgeSearch(ToolInvocation invocation) {
        String question = String.valueOf(invocation.input().getOrDefault("question", ""));
        List<Map<String, Object>> evidence = List.of(
                Map.of("title", "知识库命中 1", "quote", "这是针对问题的知识库证据摘要。", "score", 0.92),
                Map.of("title", "知识库命中 2", "quote", "这是第二条知识库证据摘要。", "score", 0.88)
        );
        return new ToolResult(
                invocation.toolId(),
                "success",
                "返回 2 条知识库证据",
                Map.of("question", question, "evidence", evidence),
                null
        );
    }

    private ToolResult executeWebSearch(ToolInvocation invocation) {
        String question = String.valueOf(invocation.input().getOrDefault("question", ""));
        List<Map<String, Object>> results = List.of(
                webResult("搜索结果 A", "https://example.com/a", "2026-06-02", "这是网页搜索结果摘要 A"),
                webResult("搜索结果 B", "https://example.com/b", "2026-06-02", "这是网页搜索结果摘要 B"),
                webResult("搜索结果 C", "https://example.com/c", "2026-06-01", "这是网页搜索结果摘要 C")
        );
        return new ToolResult(
                invocation.toolId(),
                "success",
                "返回 3 条网页搜索结果",
                Map.of("question", question, "results", results),
                null
        );
    }

    private ToolResult executeWebFetch(ToolInvocation invocation) {
        String url = String.valueOf(invocation.input().getOrDefault("url", "https://example.com/a"));
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "网页正文抓取示例");
        article.put("url", url);
        article.put("publishedAt", "2026-06-02");
        article.put("fetchedAt", "2026-06-02T12:00:00Z");
        article.put("summary", "这是网页正文抓取后的摘要内容，用于给 Agent 提供引用依据。");
        return new ToolResult(
                invocation.toolId(),
                "success",
                "完成网页抓取摘要",
                article,
                null
        );
    }

    private ToolResult executeHttpRequest(ToolInvocation invocation) {
        TenantRuntimeSettingsService.ToolPolicy policy = runtimeSettingsService.resolveToolPolicy(invocation.tenantId());
        if (!policy.httpToolEnabled()) {
            return new ToolResult(invocation.toolId(), "failed", "HTTP 工具未启用", Map.of(), "http_tool_disabled");
        }
        String url = String.valueOf(invocation.input().getOrDefault("url", ""));
        String method = String.valueOf(invocation.input().getOrDefault("method", "GET")).toUpperCase();
        if (!"GET".equals(method)) {
            return new ToolResult(invocation.toolId(), "failed", "仅允许 GET 方法", Map.of(), "http_method_not_allowed");
        }
        if (!isAllowedDomain(url, policy.allowedHttpDomains())) {
            return new ToolResult(invocation.toolId(), "failed", "目标域名不在 allowlist 中", Map.of(), "http_domain_not_allowed");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new ToolResult(
                    invocation.toolId(),
                    response.statusCode() < 400 ? "success" : "failed",
                    "HTTP 请求完成",
                    Map.of("statusCode", response.statusCode(), "body", truncate(response.body(), 1200), "url", url),
                    response.statusCode() < 400 ? null : "http_status_" + response.statusCode()
            );
        } catch (Exception exception) {
            return new ToolResult(invocation.toolId(), "failed", "HTTP 请求失败", Map.of("url", url), exception.getMessage());
        }
    }

    private ToolResult executePythonSandbox(ToolInvocation invocation) {
        TenantRuntimeSettingsService.ToolPolicy policy = runtimeSettingsService.resolveToolPolicy(invocation.tenantId());
        if (!policy.codeExecutionEnabled()) {
            return new ToolResult(invocation.toolId(), "failed", "代码执行工具未启用", Map.of(), "code_execution_disabled");
        }
        String code = String.valueOf(invocation.input().getOrDefault("code", "print('hello from sandbox')"));
        Map<String, Object> result = sandboxRunnerClient.executePython(code, Math.max(1, policy.toolTimeoutMs() / 1000));
        String status = String.valueOf(result.getOrDefault("status", "failed"));
        return new ToolResult(
                invocation.toolId(),
                status,
                "Python Sandbox 执行完成",
                result,
                "success".equals(status) ? null : String.valueOf(result.getOrDefault("stderr", "sandbox_failed"))
        );
    }

    private ToolResult executeGraphQuery(ToolInvocation invocation) {
        TenantRuntimeSettingsService.ToolPolicy policy = runtimeSettingsService.resolveToolPolicy(invocation.tenantId());
        if (!policy.graphToolEnabled()) {
            return new ToolResult(invocation.toolId(), "failed", "图谱工具未启用", Map.of(), "graph_tool_disabled");
        }
        String question = String.valueOf(invocation.input().getOrDefault("question", ""));
        Long knowledgeBaseId = toLong(invocation.input().get("knowledgeBaseId"));
        Long documentId = toLong(invocation.input().get("documentId"));
        int limit = Math.max(1, toInt(invocation.input().get("limit"), 5));
        Map<String, Object> result = graphQueryService.query(invocation.tenantId(), question, knowledgeBaseId, documentId, limit);
        int evidenceCount = ((List<?>) result.getOrDefault("evidence", List.of())).size();
        return new ToolResult(
                invocation.toolId(),
                "success",
                "返回 " + evidenceCount + " 条图谱证据",
                result,
                null
        );
    }

    private Map<String, Object> webResult(String title, String url, String publishedAt, String summary) {
        return Map.of(
                "title", title,
                "url", url,
                "publishedAt", publishedAt,
                "summary", summary
        );
    }

    private boolean isAllowedDomain(String url, List<String> allowedDomains) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            return allowedDomains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        } catch (Exception exception) {
            return false;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
