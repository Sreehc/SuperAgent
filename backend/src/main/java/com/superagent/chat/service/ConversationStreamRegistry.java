package com.superagent.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ConversationStreamRegistry {

    private final Map<String, ActiveConversation> activeConversations = new ConcurrentHashMap<>();

    public ActiveConversation register(
            long tenantId,
            long sessionId,
            SseEmitter emitter,
            ConversationRunLockManager.ConversationRunLock runLock
    ) {
        String key = buildKey(tenantId, sessionId);
        ActiveConversation conversation = new ActiveConversation(tenantId, sessionId, emitter, runLock);
        ActiveConversation existing = activeConversations.putIfAbsent(key, conversation);
        return existing == null ? conversation : null;
    }

    public ActiveConversation get(long tenantId, long sessionId) {
        return activeConversations.get(buildKey(tenantId, sessionId));
    }

    public ActiveConversation remove(long tenantId, long sessionId) {
        return activeConversations.remove(buildKey(tenantId, sessionId));
    }

    private String buildKey(long tenantId, long sessionId) {
        return tenantId + ":" + sessionId;
    }

    public record ActiveConversation(
            long tenantId,
            long sessionId,
            SseEmitter emitter,
            ConversationRunLockManager.ConversationRunLock runLock
    ) {
    }
}
