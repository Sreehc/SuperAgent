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
import org.springframework.stereotype.Service;

@Service
public class SandboxRunnerClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final AgentServiceProperties properties;

    public SandboxRunnerClient(ObjectMapper objectMapper, AgentServiceProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Map<String, Object> executePython(String code, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getSandboxRunnerBaseUrl() + "/internal/sandbox/execute"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                            "code", code,
                            "timeout_seconds", timeoutSeconds
                    ))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return Map.of("status", "failed", "stderr", response.body(), "exit_code", response.statusCode());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (Exception exception) {
            return Map.of("status", "failed", "stderr", exception.getMessage(), "exit_code", -1);
        }
    }
}
