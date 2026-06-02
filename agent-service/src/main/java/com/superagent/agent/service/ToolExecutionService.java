package com.superagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.config.AgentServiceProperties;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutionService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title>(.*?)</title>");
    private static final Pattern META_PUBLISHED_PATTERN = Pattern.compile(
            "(?is)<meta[^>]+(?:property|name)=[\"'](?:article:published_time|og:published_time|pubdate)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile("(?is)<time[^>]+datetime=[\"']([^\"']+)[\"'][^>]*>");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?is)<(script|style|noscript)[^>]*>.*?</\\1>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");

    private final SandboxRunnerClient sandboxRunnerClient;
    private final GraphQueryService graphQueryService;
    private final ToolAccessPolicyService toolAccessPolicyService;
    private final AgentServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ToolExecutionService(
            SandboxRunnerClient sandboxRunnerClient,
            GraphQueryService graphQueryService,
            ToolAccessPolicyService toolAccessPolicyService,
            AgentServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this(
                sandboxRunnerClient,
                graphQueryService,
                toolAccessPolicyService,
                properties,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        );
    }

    ToolExecutionService(
            SandboxRunnerClient sandboxRunnerClient,
            GraphQueryService graphQueryService,
            ToolAccessPolicyService toolAccessPolicyService,
            AgentServiceProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.sandboxRunnerClient = sandboxRunnerClient;
        this.graphQueryService = graphQueryService;
        this.toolAccessPolicyService = toolAccessPolicyService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ToolResult execute(ToolSpec spec, ToolInvocation invocation) {
        ToolAccessPolicyService.ToolAccessPolicy policy = toolAccessPolicyService.resolve(invocation.tenantId(), invocation.actorRole(), spec);
        Map<String, Object> effectiveInput = mergeInput(policy.parameterTemplate(), invocation.input());
        if (!policy.executable()) {
            return blockedResult(spec, policy);
        }
        ToolInvocation effectiveInvocation = new ToolInvocation(
                invocation.tenantId(),
                invocation.runId(),
                invocation.stepId(),
                invocation.actorRole(),
                invocation.toolId(),
                effectiveInput
        );
        return switch (spec.id()) {
            case "knowledge.search" -> executeKnowledgeSearch(effectiveInvocation);
            case "web.search" -> executeWebSearch(effectiveInvocation, policy);
            case "web.fetch" -> executeWebFetch(effectiveInvocation, policy);
            case "http.request" -> executeHttpRequest(effectiveInvocation, policy);
            case "graph.query" -> executeGraphQuery(effectiveInvocation);
            case "python.sandbox" -> executePythonSandbox(effectiveInvocation, policy);
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

    private ToolResult executeWebSearch(ToolInvocation invocation, ToolAccessPolicyService.ToolAccessPolicy policy) {
        String question = stringValue(invocation.input().get("question"));
        if (question.isBlank()) {
            return new ToolResult(invocation.toolId(), "failed", "缺少搜索问题", Map.of(), "missing_question");
        }
        String provider = firstNonBlank(policy.searchProvider(), properties.getSearch().getProvider()).toLowerCase(Locale.ROOT);
        if (!"tavily".equals(provider)) {
            return new ToolResult(invocation.toolId(), "failed", "搜索 provider 暂不支持", Map.of("provider", provider), "unsupported_search_provider");
        }
        String apiKey = firstNonBlank(
                policy.secrets().get("apiKey"),
                firstNonBlank(policy.secrets().get("tavilyApiKey"), properties.getSearch().getApiKey())
        );
        if (apiKey == null || apiKey.isBlank()) {
            return new ToolResult(invocation.toolId(), "failed", "Tavily API Key 未配置", Map.of("provider", provider), "search_api_key_missing");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "api_key", apiKey,
                    "query", question,
                    "search_depth", "advanced",
                    "max_results", Math.max(1, policy.maxResults()),
                    "include_answer", false,
                    "include_raw_content", false
            );
            HttpResponse<String> response = sendWithRetry(
                    () -> jsonPost(properties.getSearch().getTavilyBaseUrl() + "/search", payload, policy.timeoutMs()),
                    policy.maxRetries()
            );
            if (response.statusCode() >= 400) {
                return new ToolResult(
                        invocation.toolId(),
                        "failed",
                        "联网搜索失败",
                        Map.of("provider", provider, "statusCode", response.statusCode()),
                        "web_search_status_" + response.statusCode()
                );
            }
            JsonNode body = objectMapper.readTree(response.body());
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode item : body.path("results")) {
                results.add(webResult(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("published_date").asText(""),
                        truncate(item.path("content").asText(""), properties.getFetch().getMaxSummaryChars())
                ));
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("question", question);
            output.put("provider", provider);
            output.put("searchedAt", OffsetDateTime.now().toString());
            output.put("results", results);
            return new ToolResult(
                    invocation.toolId(),
                    "success",
                    "返回 " + results.size() + " 条网页搜索结果",
                    output,
                    null
            );
        } catch (Exception exception) {
            return new ToolResult(
                    invocation.toolId(),
                    "failed",
                    "联网搜索异常",
                    Map.of("provider", provider),
                    exception.getMessage()
            );
        }
    }

    private ToolResult executeWebFetch(ToolInvocation invocation, ToolAccessPolicyService.ToolAccessPolicy policy) {
        String url = stringValue(invocation.input().get("url"));
        if (!isHttpUrl(url)) {
            return new ToolResult(invocation.toolId(), "failed", "抓取地址无效", Map.of("url", url), "invalid_fetch_url");
        }
        try {
            HttpResponse<String> response = sendWithRetry(
                    () -> baseRequest(url, policy.timeoutMs())
                            .header("User-Agent", "SuperAgentBot/0.1")
                            .GET()
                            .build(),
                    policy.maxRetries()
            );
            if (response.statusCode() >= 400) {
                return new ToolResult(
                        invocation.toolId(),
                        "failed",
                        "网页抓取失败",
                        Map.of("url", url, "statusCode", response.statusCode()),
                        "web_fetch_status_" + response.statusCode()
                );
            }
            String html = response.body();
            String title = extractFirst(TITLE_PATTERN, html);
            String publishedAt = firstNonBlank(extractFirst(META_PUBLISHED_PATTERN, html), extractFirst(TIME_PATTERN, html));
            String text = extractReadableText(html);
            String summary = summarizeText(text, properties.getFetch().getMaxSummaryChars());
            Map<String, Object> article = new LinkedHashMap<>();
            article.put("title", title == null || title.isBlank() ? url : cleanText(title));
            article.put("url", url);
            article.put("publishedAt", publishedAt);
            article.put("fetchedAt", OffsetDateTime.now().toString());
            article.put("summary", summary);
            article.put("excerpt", truncate(text, Math.min(properties.getFetch().getMaxBodyChars(), 600)));
            article.put("bodyCharCount", text.length());
            return new ToolResult(
                    invocation.toolId(),
                    "success",
                    "完成网页抓取摘要",
                    article,
                    null
            );
        } catch (Exception exception) {
            return new ToolResult(invocation.toolId(), "failed", "网页抓取异常", Map.of("url", url), exception.getMessage());
        }
    }

    private ToolResult executeHttpRequest(ToolInvocation invocation, ToolAccessPolicyService.ToolAccessPolicy policy) {
        String url = stringValue(invocation.input().get("url"));
        String method = stringValue(invocation.input().getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        if (!isHttpUrl(url)) {
            return new ToolResult(invocation.toolId(), "failed", "请求地址无效", Map.of("url", url), "invalid_http_url");
        }
        if (!policy.allowedMethods().contains(method)) {
            return new ToolResult(invocation.toolId(), "failed", "该租户未授权此 HTTP 方法", Map.of("method", method), "http_method_not_allowed");
        }
        boolean writeMethod = !"GET".equals(method);
        List<String> allowedDomains = writeMethod ? policy.allowedWriteDomains() : policy.allowedDomains();
        if (!isAllowedDomain(url, allowedDomains)) {
            return new ToolResult(invocation.toolId(), "failed", "目标域名不在 allowlist 中", Map.of("url", url, "method", method), "http_domain_not_allowed");
        }
        try {
            HttpRequest.Builder builder = baseRequest(url, policy.timeoutMs());
            Map<String, String> headers = stringifyMap(invocation.input().get("headers"));
            headers.forEach(builder::header);
            String body = stringValue(invocation.input().get("body"));
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = sendWithRetry(builder::build, policy.maxRetries());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("method", method);
            output.put("url", url);
            output.put("statusCode", response.statusCode());
            output.put("contentType", response.headers().firstValue("Content-Type").orElse(""));
            output.put("body", truncate(response.body(), 1200));
            output.put("allowlistMatched", true);
            return new ToolResult(
                    invocation.toolId(),
                    response.statusCode() < 400 ? "success" : "failed",
                    "HTTP 请求完成",
                    output,
                    response.statusCode() < 400 ? null : "http_status_" + response.statusCode()
            );
        } catch (Exception exception) {
            return new ToolResult(invocation.toolId(), "failed", "HTTP 请求失败", Map.of("url", url, "method", method), exception.getMessage());
        }
    }

    private ToolResult executePythonSandbox(ToolInvocation invocation, ToolAccessPolicyService.ToolAccessPolicy policy) {
        String code = stringValue(invocation.input().getOrDefault("code", "print('hello from sandbox')"));
        Map<String, Object> result = new LinkedHashMap<>(sandboxRunnerClient.executePython(
                code,
                Math.max(1, policy.timeoutMs() / 1000),
                32_768
        ));
        result.putIfAbsent("executor", "sandbox-runner");
        result.putIfAbsent("limits", Map.of(
                "network", "disabled",
                "timeoutSeconds", Math.max(1, policy.timeoutMs() / 1000),
                "maxOutputBytes", 32_768
        ));
        String status = stringValue(result.getOrDefault("status", "failed"));
        return new ToolResult(
                invocation.toolId(),
                status,
                "Python Sandbox 执行完成",
                result,
                "success".equals(status) ? null : stringValue(result.getOrDefault("stderr", "sandbox_failed"))
        );
    }

    private ToolResult executeGraphQuery(ToolInvocation invocation) {
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

    private ToolResult blockedResult(ToolSpec spec, ToolAccessPolicyService.ToolAccessPolicy policy) {
        if (!policy.featureEnabled()) {
            return new ToolResult(spec.id(), "failed", "工具未启用", Map.of("toolId", spec.id()), disabledCode(spec.id()));
        }
        if (!policy.bindingEnabled()) {
            return new ToolResult(spec.id(), "failed", "工具未授权到当前租户", Map.of("toolId", spec.id()), "tool_binding_disabled");
        }
        if (policy.highRisk() && !policy.ownerOrAdmin()) {
            return new ToolResult(spec.id(), "failed", "高风险工具仅允许管理员使用", Map.of("toolId", spec.id()), "tool_role_forbidden");
        }
        return new ToolResult(spec.id(), "failed", "工具当前不可执行", Map.of("toolId", spec.id()), "tool_not_executable");
    }

    private String disabledCode(String toolId) {
        return switch (toolId) {
            case "web.search", "web.fetch" -> "web_search_disabled";
            case "http.request" -> "http_tool_disabled";
            case "graph.query" -> "graph_tool_disabled";
            case "python.sandbox" -> "code_execution_disabled";
            default -> "tool_disabled";
        };
    }

    private HttpRequest.Builder baseRequest(String url, int timeoutMs) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(timeoutMs, 100)));
    }

    private HttpRequest jsonPost(String url, Map<String, Object> payload, int timeoutMs) {
        try {
            return baseRequest(url, timeoutMs)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("failed_to_build_request", exception);
        }
    }

    private HttpResponse<String> sendWithRetry(Supplier<HttpRequest> requestSupplier, int maxRetries) throws Exception {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                HttpResponse<String> response = httpClient.send(requestSupplier.get(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!shouldRetry(response.statusCode()) || attempts > maxRetries + 1) {
                    return response;
                }
            } catch (Exception exception) {
                if (attempts > maxRetries + 1) {
                    throw exception;
                }
            }
        }
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private Map<String, Object> mergeInput(Map<String, Object> parameterTemplate, Map<String, Object> input) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (parameterTemplate != null) {
            merged.putAll(parameterTemplate);
        }
        if (input != null) {
            merged.putAll(input);
        }
        return merged;
    }

    private Map<String, String> stringifyMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            if (key != null && entryValue != null) {
                result.put(String.valueOf(key), String.valueOf(entryValue));
            }
        });
        return result;
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

    private boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception exception) {
            return false;
        }
    }

    private String extractFirst(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source == null ? "" : source);
        return matcher.find() ? cleanText(matcher.group(1)) : null;
    }

    private String extractReadableText(String html) {
        String withoutScripts = SCRIPT_PATTERN.matcher(html == null ? "" : html).replaceAll(" ");
        String withoutTags = TAG_PATTERN.matcher(withoutScripts).replaceAll(" ");
        return cleanText(withoutTags);
    }

    private String summarizeText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = cleanText(text);
        int boundary = cleaned.indexOf("。");
        if (boundary > 0 && boundary < maxLength) {
            return truncate(cleaned.substring(0, boundary + 1), maxLength);
        }
        return truncate(cleaned, maxLength);
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
