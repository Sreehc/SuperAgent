package com.superagent.rag.service;

import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;

    public OpenAiCompatibleEmbeddingClient(SuperAgentProperties properties, RuntimeSettingsService runtimeSettingsService) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Override
    public EmbeddingResult embed(List<String> inputs) {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required for embedding");
        }
        return embed(tenantContext.tenantId(), inputs);
    }

    @Override
    public EmbeddingResult embed(long tenantId, List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Embedding input is required");
        }
        ModelSettings settings = runtimeSettingsService.resolveModelSettings(tenantId);
        if (isLocalDeterministicProvider(settings)) {
            return new EmbeddingResult(
                    resolveEmbeddingProvider(),
                    resolveEmbeddingModel(settings),
                    properties.getAi().getEmbeddingDimension(),
                    inputs.stream().map(this::toDeterministicVector).toList()
            );
        }
        validateSettings(settings);
        RestClient client = buildRestClient(settings);
        int maxAttempts = Math.max(1, properties.getAi().getEmbeddingMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EmbeddingApiResponse response = client.post()
                        .uri("/embeddings")
                        .body(new EmbeddingApiRequest(resolveEmbeddingModel(settings), inputs))
                        .retrieve()
                        .body(EmbeddingApiResponse.class);
                if (response == null || response.data() == null || response.data().size() != inputs.size()) {
                    throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding provider returned invalid response");
                }
                List<List<Double>> vectors = response.data().stream()
                        .map(EmbeddingVector::embedding)
                        .toList();
                int dimension = vectors.getFirst().size();
                if (dimension != properties.getAi().getEmbeddingDimension()) {
                    throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding dimension does not match configured dimension");
                }
                return new EmbeddingResult(
                        resolveEmbeddingProvider(),
                        response.model() == null || response.model().isBlank() ? resolveEmbeddingModel(settings) : response.model(),
                        dimension,
                        vectors
                );
            } catch (AppException exception) {
                throw exception;
            } catch (Exception exception) {
                if (attempt == maxAttempts) {
                    throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Failed to call embedding provider after retries");
                }
                sleepBeforeRetry();
            }
        }
        throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Failed to call embedding provider after retries");
    }

    private RestClient buildRestClient(ModelSettings settings) {
        return RestClient.builder()
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private void validateSettings(ModelSettings settings) {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()
                || resolveEmbeddingModel(settings).isBlank()
                || settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding provider configuration is incomplete");
        }
    }

    private String resolveEmbeddingProvider() {
        return properties.getAi().getEmbeddingProvider();
    }

    private String resolveEmbeddingModel(ModelSettings settings) {
        if (settings.embeddingModel() != null && !settings.embeddingModel().isBlank()) {
            return settings.embeddingModel();
        }
        return properties.getAi().getEmbeddingModel();
    }

    private boolean isLocalDeterministicProvider(ModelSettings settings) {
        return "local-deterministic".equalsIgnoreCase(resolveEmbeddingProvider());
    }

    private List<Double> toDeterministicVector(String input) {
        int dimension = properties.getAi().getEmbeddingDimension();
        double[] accumulator = new double[dimension];
        String normalized = normalizeInput(input);
        if (normalized.isBlank()) {
            return toList(accumulator);
        }

        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            int unigramSlot = Math.floorMod(current, dimension);
            accumulator[unigramSlot] += 1.0d;
            if (index + 1 < normalized.length()) {
                char next = normalized.charAt(index + 1);
                int bigramSlot = Math.floorMod((current * 31) + next, dimension);
                accumulator[bigramSlot] += 2.0d;
            }
        }

        double norm = 0.0d;
        for (double value : accumulator) {
            norm += value * value;
        }
        if (norm == 0.0d) {
            return toList(accumulator);
        }

        double scale = Math.sqrt(norm);
        List<Double> vector = new ArrayList<>(dimension);
        for (double value : accumulator) {
            vector.add(value / scale);
        }
        return vector;
    }

    private String normalizeInput(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<Double> toList(double[] values) {
        List<Double> vector = new ArrayList<>(values.length);
        for (double value : values) {
            vector.add(value);
        }
        return vector;
    }

    private void sleepBeforeRetry() {
        long backoffMillis = Math.max(0L, properties.getAi().getEmbeddingRetryBackoffMillis());
        if (backoffMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding retry interrupted");
        }
    }

    public record EmbeddingApiRequest(String model, List<String> input) {
    }

    public record EmbeddingApiResponse(List<EmbeddingVector> data, String model) {
    }

    public record EmbeddingVector(List<Double> embedding, int index) {
    }
}
