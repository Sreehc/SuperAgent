package com.superagent.chat.service;

import com.superagent.infra.config.SuperAgentProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisConversationRunLockManager implements ConversationRunLockManager {

    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final SuperAgentProperties properties;

    public RedisConversationRunLockManager(StringRedisTemplate redisTemplate, SuperAgentProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public ConversationRunLock acquire(long tenantId, long sessionId) {
        String ownerToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey(tenantId, sessionId),
                ownerToken,
                Duration.ofSeconds(properties.getRedis().getConversationLockTtlSeconds())
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        return new ConversationRunLock(tenantId, sessionId, ownerToken);
    }

    @Override
    public boolean requestStop(long tenantId, long sessionId) {
        String key = lockKey(tenantId, sessionId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return false;
        }
        redisTemplate.opsForValue().set(
                stopKey(tenantId, sessionId),
                "true",
                Duration.ofSeconds(properties.getRedis().getStopSignalTtlSeconds())
        );
        return true;
    }

    @Override
    public boolean isStopRequested(long tenantId, long sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(stopKey(tenantId, sessionId)));
    }

    @Override
    public void release(long tenantId, long sessionId, String ownerToken) {
        redisTemplate.execute(
                connection -> connection.scriptingCommands().eval(
                        RELEASE_SCRIPT.getBytes(),
                        ReturnType.INTEGER,
                        1,
                        lockKey(tenantId, sessionId).getBytes(),
                        ownerToken.getBytes()
                ),
                true
        );
        redisTemplate.delete(stopKey(tenantId, sessionId));
    }

    private String lockKey(long tenantId, long sessionId) {
        return "superagent:conversation:lock:" + tenantId + ":" + sessionId;
    }

    private String stopKey(long tenantId, long sessionId) {
        return "superagent:conversation:stop:" + tenantId + ":" + sessionId;
    }
}
