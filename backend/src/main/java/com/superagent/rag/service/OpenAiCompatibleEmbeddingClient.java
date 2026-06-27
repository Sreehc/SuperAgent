package com.superagent.rag.service;

import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.ai.SpringAiOpenAiModelFactory;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import io.micrometer.observation.ObservationRegistry;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;
    private final EmbeddingModelFactory embeddingModelFactory;

    public OpenAiCompatibleEmbeddingClient(SuperAgentProperties properties, RuntimeSettingsService runtimeSettingsService) {
        this(properties, runtimeSettingsService, ObservationRegistry.NOOP);
    }

    @Autowired
    public OpenAiCompatibleEmbeddingClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObservationRegistry observationRegistry
    ) {
        this(properties, runtimeSettingsService, observationRegistry, settings -> createSpringAiEmbeddingModel(settings, observationRegistry));
    }

    public OpenAiCompatibleEmbeddingClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            EmbeddingModelFactory embeddingModelFactory
    ) {
        this(properties, runtimeSettingsService, ObservationRegistry.NOOP, embeddingModelFactory);
    }

    public OpenAiCompatibleEmbeddingClient(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObservationRegistry observationRegistry,
            EmbeddingModelFactory embeddingModelFactory
    ) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
        this.embeddingModelFactory = embeddingModelFactory;
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
        EmbeddingModelSettings embeddingSettings = new EmbeddingModelSettings(
                settings.baseUrl(),
                settings.apiKey(),
                resolveEmbeddingModel(settings),
                properties.getAi().getEmbeddingDimension(),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpConnectTimeoutMillis())),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpReadTimeoutMillis()))
        );
        EmbeddingModel embeddingModel = embeddingModelFactory.create(embeddingSettings);
        int maxAttempts = Math.max(1, properties.getAi().getEmbeddingMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(
                        inputs,
                        OpenAiEmbeddingOptions.builder()
                                .model(embeddingSettings.model())
                                .dimensions(embeddingSettings.dimension())
                                .build()
                ));
                if (response == null || response.getResults() == null || response.getResults().size() != inputs.size()) {
                    throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding provider returned invalid response");
                }
                List<List<Double>> vectors = response.getResults().stream()
                        .map(Embedding::getOutput)
                        .map(this::toDoubleList)
                        .toList();
                int dimension = vectors.getFirst().size();
                if (dimension != properties.getAi().getEmbeddingDimension()) {
                    throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Embedding dimension does not match configured dimension");
                }
                String model = response.getMetadata() == null || response.getMetadata().getModel() == null || response.getMetadata().getModel().isBlank()
                        ? embeddingSettings.model()
                        : response.getMetadata().getModel();
                return new EmbeddingResult(resolveEmbeddingProvider(), model, dimension, vectors);
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

    private static EmbeddingModel createSpringAiEmbeddingModel(EmbeddingModelSettings settings) {
        return createSpringAiEmbeddingModel(settings, ObservationRegistry.NOOP);
    }

    private static EmbeddingModel createSpringAiEmbeddingModel(EmbeddingModelSettings settings, ObservationRegistry observationRegistry) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .embeddingsPath("/embeddings")
                .restClientBuilder(SpringAiOpenAiModelFactory.restClientBuilder(settings.connectTimeoutMillis(), settings.readTimeoutMillis()))
                .webClientBuilder(WebClient.builder())
                .build();
        return new OpenAiEmbeddingModel(
                api,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(settings.model())
                        .dimensions(settings.dimension())
                        .build(),
                SpringAiOpenAiModelFactory.singleAttemptRetryTemplate(),
                observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry
        );
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

    private List<Double> toDoubleList(float[] values) {
        List<Double> vector = new ArrayList<>(values.length);
        for (float value : values) {
            vector.add((double) value);
        }
        return vector;
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

    public record EmbeddingModelSettings(
            String baseUrl,
            String apiKey,
            String model,
            int dimension,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
    }

    @FunctionalInterface
    public interface EmbeddingModelFactory {
        EmbeddingModel create(EmbeddingModelSettings settings);
    }
}
