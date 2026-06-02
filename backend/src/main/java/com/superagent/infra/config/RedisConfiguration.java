package com.superagent.infra.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "super-agent.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfiguration {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(SuperAgentProperties properties) {
        URI uri = URI.create(properties.getRedis().getUrl());
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(uri.getHost());
        configuration.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String password = uri.getUserInfo().substring(uri.getUserInfo().indexOf(':') + 1);
            if (!password.isBlank()) {
                configuration.setPassword(RedisPassword.of(password));
            }
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
