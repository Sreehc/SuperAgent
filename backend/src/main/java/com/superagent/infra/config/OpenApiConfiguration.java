package com.superagent.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI superAgentOpenApi(SuperAgentProperties properties) {
        return new OpenAPI().info(new Info()
                .title("SuperAgent Backend API")
                .version("v0")
                .description("Backend baseline for SuperAgent")
                .summary(properties.getApp().getName()));
    }
}
