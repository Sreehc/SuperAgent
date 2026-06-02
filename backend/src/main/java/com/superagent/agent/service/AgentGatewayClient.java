package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentGatewayClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final SuperAgentProperties properties;

    public AgentGatewayClient(ObjectMapper objectMapper, SuperAgentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public long createRun(CreateRunRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getAgent().getServiceBaseUrl() + "/internal/agent-runs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Failed to create agent run");
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), MAP_TYPE);
            Object runId = payload.get("runId");
            if (runId instanceof Number number) {
                return number.longValue();
            }
            throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Agent run id missing");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Agent service unavailable");
        }
    }

    public void streamRun(long runId, BooleanSupplier stopRequested, AgentEventConsumer consumer) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getAgent().getServiceBaseUrl() + "/internal/agent-runs/" + runId + "/stream"))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Failed to stream agent run");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String eventName = null;
                StringBuilder dataBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stopRequested.getAsBoolean()) {
                        cancelRun(runId);
                        break;
                    }
                    if (line.isBlank()) {
                        if (eventName != null && dataBuilder.length() > 0) {
                            consumer.accept(eventName, dataBuilder.toString());
                        }
                        eventName = null;
                        dataBuilder.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (dataBuilder.length() > 0) {
                            dataBuilder.append('\n');
                        }
                        dataBuilder.append(line.substring("data:".length()).trim());
                    }
                }
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Failed to consume agent stream");
        }
    }

    public void cancelRun(long runId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getAgent().getServiceBaseUrl() + "/internal/agent-runs/" + runId + "/cancel"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // best effort cancellation
        }
    }

    public boolean resumeRun(long runId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getAgent().getServiceBaseUrl() + "/internal/agent-runs/" + runId + "/resume"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Failed to resume agent run");
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), MAP_TYPE);
            return Boolean.TRUE.equals(payload.get("accepted")) || Boolean.TRUE.equals(((Map<?, ?>) payload.getOrDefault("data", Map.of())).get("accepted"));
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.AGENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, "Agent service unavailable");
        }
    }

    public record CreateRunRequest(
            long tenantId,
            long sessionId,
            long exchangeId,
            long triggerMessageId,
            long actorUserId,
            String actorRole,
            String question,
            Long knowledgeBaseId,
            MemoryStrategy memoryStrategy,
            List<String> recentMessages
    ) {
    }

    @FunctionalInterface
    public interface AgentEventConsumer {
        void accept(String eventName, String dataJson) throws Exception;
    }
}
