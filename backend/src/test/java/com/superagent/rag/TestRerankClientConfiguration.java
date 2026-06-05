package com.superagent.rag;

import com.superagent.rag.service.RerankClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestRerankClientConfiguration {

    @Bean
    @Primary
    public RerankClient testRerankClient() {
        return (query, evidences) -> {
            if (query != null && query.contains("rerank异常")) {
                throw new IllegalStateException("simulated rerank failure");
            }
            return new RerankClient.RerankResult(
                    evidences,
                    "test-rerank-provider",
                    "test-rerank-model",
                    "success",
                    null,
                    null,
                    5
            );
        };
    }
}
