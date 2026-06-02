package com.superagent.chat.service;

public interface ConversationRunLockManager {

    ConversationRunLock acquire(long tenantId, long sessionId);

    boolean requestStop(long tenantId, long sessionId);

    boolean isStopRequested(long tenantId, long sessionId);

    void release(long tenantId, long sessionId, String ownerToken);

    record ConversationRunLock(long tenantId, long sessionId, String ownerToken) {
    }
}
