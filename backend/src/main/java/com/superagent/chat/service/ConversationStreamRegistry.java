package com.superagent.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ConversationStreamRegistry {

    private final Map<String, ActiveConversation> activeConversations = new ConcurrentHashMap<>();

    public ActiveConversation register(long tenantId, long sessionId, SseEmitter emitter) {
        String key = buildKey(tenantId, sessionId);
        ActiveConversation conversation = new ActiveConversation(tenantId, sessionId, emitter);
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

    public static final class ActiveConversation {
        private final long tenantId;
        private final long sessionId;
        private final SseEmitter emitter;
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        private ActiveConversation(long tenantId, long sessionId, SseEmitter emitter) {
            this.tenantId = tenantId;
            this.sessionId = sessionId;
            this.emitter = emitter;
        }

        public long tenantId() {
            return tenantId;
        }

        public long sessionId() {
            return sessionId;
        }

        public SseEmitter emitter() {
            return emitter;
        }

        public boolean requestStop() {
            return stopRequested.compareAndSet(false, true);
        }

        public boolean stopRequested() {
            return stopRequested.get();
        }
    }
}
