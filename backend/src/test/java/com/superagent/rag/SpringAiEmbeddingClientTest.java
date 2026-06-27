package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.service.EmbeddingClient;
import com.superagent.rag.service.OpenAiCompatibleEmbeddingClient;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

class SpringAiEmbeddingClientTest {

    @Test
    void shouldUseSpringAiEmbeddingModelAndValidateDimension() {
        AtomicReference<OpenAiCompatibleEmbeddingClient.EmbeddingModelSettings> capturedSettings = new AtomicReference<>();
        AtomicReference<EmbeddingRequest> capturedRequest = new AtomicReference<>();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                properties(3, 1),
                runtimeSettings("https://tenant.example/v1", "tenant-specific-embedding-model", "sk-tenant"),
                settings -> {
                    capturedSettings.set(settings);
                    return new StubEmbeddingModel(capturedRequest, new EmbeddingResponse(
                            List.of(new Embedding(new float[]{0.1f, 0.2f, 0.3f}, 0)),
                            new EmbeddingResponseMetadata("tenant-specific-embedding-model", null)
                    ));
                }
        );

        EmbeddingClient.EmbeddingResult result = client.embed(10002L, List.of("refund rule"));

        assertThat(capturedSettings.get().baseUrl()).isEqualTo("https://tenant.example/v1");
        assertThat(capturedSettings.get().apiKey()).isEqualTo("sk-tenant");
        assertThat(capturedSettings.get().model()).isEqualTo("tenant-specific-embedding-model");
        assertThat(capturedRequest.get().getInstructions()).containsExactly("refund rule");
        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(result.model()).isEqualTo("tenant-specific-embedding-model");
        assertThat(result.dimension()).isEqualTo(3);
        assertThat(result.vectors()).singleElement().satisfies(vector -> {
            assertThat(vector.get(0)).isCloseTo(0.1d, within(0.000001d));
            assertThat(vector.get(1)).isCloseTo(0.2d, within(0.000001d));
            assertThat(vector.get(2)).isCloseTo(0.3d, within(0.000001d));
        });
    }

    @Test
    void shouldRetrySpringAiEmbeddingProviderAndFailAfterLimit() {
        AtomicInteger attempts = new AtomicInteger();
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                properties(3, 3),
                runtimeSettings("https://tenant.example/v1", "text-embedding-3-small", "sk-tenant"),
                settings -> new FailingEmbeddingModel(attempts)
        );

        assertThatThrownBy(() -> client.embed(10001L, List.of("refund rule")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("after retries");
        assertThat(attempts.get()).isEqualTo(3);
    }

    private SuperAgentProperties properties(int dimension, int attempts) {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAi().setEmbeddingProvider("openai-compatible");
        properties.getAi().setEmbeddingModel("text-embedding-3-small");
        properties.getAi().setEmbeddingDimension(dimension);
        properties.getAi().setEmbeddingMaxAttempts(attempts);
        properties.getAi().setEmbeddingRetryBackoffMillis(0L);
        properties.getAi().setOpenaiCompatibleBaseUrl("https://api.example.com/v1");
        properties.getAi().setApiKey("sk-test");
        properties.getAi().setChatModel("gpt-4.1-mini");
        properties.getAi().setRerankEnabled(false);
        return properties;
    }

    private RuntimeSettingsService runtimeSettings(String baseUrl, String embeddingModel, String apiKey) {
        return new RuntimeSettingsService(null, null, null, properties(3, 1)) {
            @Override
            public ModelSettings resolveModelSettings(long tenantId) {
                return new ModelSettings("openai-compatible", baseUrl, "gpt-4.1-mini", embeddingModel, apiKey);
            }
        };
    }

    private static class StubEmbeddingModel implements EmbeddingModel {

        private final AtomicReference<EmbeddingRequest> capturedRequest;
        private final EmbeddingResponse response;

        private StubEmbeddingModel(AtomicReference<EmbeddingRequest> capturedRequest, EmbeddingResponse response) {
            this.capturedRequest = capturedRequest;
            this.response = response;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            capturedRequest.set(request);
            return response;
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class FailingEmbeddingModel implements EmbeddingModel {

        private final AtomicInteger attempts;

        private FailingEmbeddingModel(AtomicInteger attempts) {
            this.attempts = attempts;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            attempts.incrementAndGet();
            throw new IllegalStateException("provider down");
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
