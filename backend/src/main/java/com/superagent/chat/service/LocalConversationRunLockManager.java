package com.superagent.chat.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalConversationRunLockManager implements ConversationRunLockManager {

    private final Map<String, ActiveLock> activeLocks = new ConcurrentHashMap<>();

    @Override
    public ConversationRunLock acquire(long tenantId, long sessionId) {
        String key = buildKey(tenantId, sessionId);
        ActiveLock lock = new ActiveLock(UUID.randomUUID().toString(), Instant.now());
        ActiveLock existing = activeLocks.putIfAbsent(key, lock);
        if (existing != null) {
            return null;
        }
        return new ConversationRunLock(tenantId, sessionId, lock.ownerToken());
    }

    @Override
    public boolean requestStop(long tenantId, long sessionId) {
        ActiveLock lock = activeLocks.get(buildKey(tenantId, sessionId));
        return lock != null && lock.stopRequested().compareAndSet(false, true);
    }

    @Override
    public boolean isStopRequested(long tenantId, long sessionId) {
        ActiveLock lock = activeLocks.get(buildKey(tenantId, sessionId));
        return lock != null && lock.stopRequested().get();
    }

    @Override
    public void release(long tenantId, long sessionId, String ownerToken) {
        activeLocks.computeIfPresent(buildKey(tenantId, sessionId), (key, activeLock) ->
                activeLock.ownerToken().equals(ownerToken) ? null : activeLock);
    }

    private String buildKey(long tenantId, long sessionId) {
        return tenantId + ":" + sessionId;
    }

    private record ActiveLock(String ownerToken, Instant createdAt, AtomicBoolean stopRequested) {
        private ActiveLock(String ownerToken, Instant createdAt) {
            this(ownerToken, createdAt, new AtomicBoolean(false));
        }
    }
}
