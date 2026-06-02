package com.superagent.rag.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final RestClient aiRestClient;
    private final SuperAgentProperties properties;

    public OpenAiCompatibleEmbeddingClient(RestClient aiRestClient, SuperAgentProperties properties) {
        this.aiRestClient = aiRestClient;
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Embedding input is required");
        }
        if (isLocalDeterministicProvider()) {
            return new EmbeddingResult(
                    properties.getAi().getEmbeddingProvider(),
                    properties.getAi().getEmbeddingModel(),
                    properties.getAi().getEmbeddingDimension(),
                    inputs.stream().map(this::toDeterministicVector).toList()
            );
        }
        try {
            EmbeddingApiResponse response = aiRestClient.post()
                    .uri("/embeddings")
                    .body(new EmbeddingApiRequest(properties.getAi().getEmbeddingModel(), inputs))
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
                    properties.getAi().getEmbeddingProvider(),
                    response.model() == null || response.model().isBlank() ? properties.getAi().getEmbeddingModel() : response.model(),
                    dimension,
                    vectors
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Failed to call embedding provider");
        }
    }

    private boolean isLocalDeterministicProvider() {
        return "local-deterministic".equalsIgnoreCase(properties.getAi().getEmbeddingProvider());
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

    public record EmbeddingApiRequest(String model, List<String> input) {
    }

    public record EmbeddingApiResponse(List<EmbeddingVector> data, String model) {
    }

    public record EmbeddingVector(List<Double> embedding, int index) {
    }
}
