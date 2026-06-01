package com.superagent.rag.service;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.util.List;
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

    public record EmbeddingApiRequest(String model, List<String> input) {
    }

    public record EmbeddingApiResponse(List<EmbeddingVector> data, String model) {
    }

    public record EmbeddingVector(List<Double> embedding, int index) {
    }
}
