package com.superagent.infra.config;

import com.superagent.chat.service.ConversationRunLockManager;
import com.superagent.chat.service.LocalConversationRunLockManager;
import com.superagent.chat.service.RedisConversationRunLockManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ConversationLockConfiguration {

    @Bean
    public ConversationRunLockManager conversationRunLockManager(
            SuperAgentProperties properties,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (Boolean.TRUE.equals(properties.getRedis().getEnabled()) && redisTemplate != null) {
            return new RedisConversationRunLockManager(redisTemplate, properties);
        }
        return new LocalConversationRunLockManager();
    }
}
