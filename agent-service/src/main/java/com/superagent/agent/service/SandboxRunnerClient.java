package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.config.AgentServiceProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SandboxRunnerClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentServiceProperties properties;

    @Autowired
    public SandboxRunnerClient(ObjectMapper objectMapper, AgentServiceProperties properties) {
        this(objectMapper, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    SandboxRunnerClient(ObjectMapper objectMapper, AgentServiceProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public Map<String, Object> executePython(String code, int timeoutSeconds, int maxOutputBytes) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getSandboxRunnerBaseUrl() + "/internal/sandbox/execute"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "code", code,
                            "timeout_seconds", timeoutSeconds,
                            "max_output_bytes", maxOutputBytes
                    ))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
                Object detail = body.get("detail");
                if (detail instanceof Map<?, ?> detailMap) {
                    Map<String, Object> merged = new java.util.LinkedHashMap<>();
                    detailMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
                    merged.putIfAbsent("status", "failed");
                    merged.putIfAbsent("exit_code", response.statusCode());
                    return merged;
                }
                return Map.of("status", "failed", "stderr", response.body(), "exit_code", response.statusCode());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (Exception exception) {
            return Map.of("status", "failed", "stderr", exception.getMessage(), "exit_code", -1);
        }
    }
}
