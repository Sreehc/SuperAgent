package com.superagent;

import com.superagent.infra.config.SuperAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(SuperAgentProperties.class)
public class SuperAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuperAgentApplication.class, args);
    }
}
