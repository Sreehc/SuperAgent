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
        return (query, evidences) -> new RerankClient.RerankResult(
                evidences,
                "test-rerank-provider",
                "test-rerank-model",
                "success",
                null,
                null,
                5
        );
    }
}
