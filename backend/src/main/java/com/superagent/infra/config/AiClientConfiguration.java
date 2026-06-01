package com.superagent.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfiguration {

    @Bean
    public RestClient aiRestClient(SuperAgentProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getAi().getOpenaiCompatibleBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getAi().getApiKey())
                .build();
    }
}
