package com.superagent.rag;

import com.superagent.rag.service.EmbeddingClient;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEmbeddingClientConfiguration {

    @Bean
    @Primary
    public EmbeddingClient testEmbeddingClient() {
        return inputs -> new EmbeddingClient.EmbeddingResult(
                "test-provider",
                "test-embedding-model",
                1536,
                inputs.stream().map(this::toVector).toList()
        );
    }

    private List<Double> toVector(String input) {
        double seed = Math.abs(input.hashCode() % 1000) / 1000.0;
        java.util.ArrayList<Double> vector = new java.util.ArrayList<>(1536);
        for (int index = 0; index < 1536; index++) {
            vector.add(seed + (index * 0.0001d));
        }
        return vector;
    }
}
