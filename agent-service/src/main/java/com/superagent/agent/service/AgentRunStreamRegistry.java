package com.superagent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentRunStreamRegistry {

    private final ObjectMapper objectMapper;
    private final Map<Long, RunStream> streams = new ConcurrentHashMap<>();

    public AgentRunStreamRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void ensureRun(long runId) {
        streams.computeIfAbsent(runId, ignored -> new RunStream());
    }

    public SseEmitter register(long runId) {
        RunStream runStream = streams.computeIfAbsent(runId, ignored -> new RunStream());
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> runStream.emitters.remove(emitter));
        emitter.onTimeout(() -> runStream.emitters.remove(emitter));
        emitter.onError(error -> runStream.emitters.remove(emitter));

        synchronized (runStream) {
            for (StoredEvent event : runStream.history) {
                send(emitter, event.name(), event.dataJson());
            }
            if (runStream.completed) {
                emitter.complete();
                return emitter;
            }
            runStream.emitters.add(emitter);
        }
        return emitter;
    }

    public void emit(long runId, String eventName, Object payload) {
        RunStream runStream = streams.computeIfAbsent(runId, ignored -> new RunStream());
        try {
            String dataJson = objectMapper.writeValueAsString(payload);
            synchronized (runStream) {
                runStream.history.add(new StoredEvent(eventName, dataJson));
                for (SseEmitter emitter : List.copyOf(runStream.emitters)) {
                    send(emitter, eventName, dataJson);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to emit agent SSE event", exception);
        }
    }

    public void complete(long runId) {
        RunStream runStream = streams.computeIfAbsent(runId, ignored -> new RunStream());
        synchronized (runStream) {
            runStream.completed = true;
            for (SseEmitter emitter : List.copyOf(runStream.emitters)) {
                emitter.complete();
            }
            runStream.emitters.clear();
        }
    }

    public void cancel(long runId) {
        RunStream runStream = streams.computeIfAbsent(runId, ignored -> new RunStream());
        runStream.cancelled = true;
    }

    public boolean isCancelled(long runId) {
        RunStream runStream = streams.get(runId);
        return runStream != null && runStream.cancelled;
    }

    private void send(SseEmitter emitter, String eventName, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(dataJson));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private static final class RunStream {
        private final List<StoredEvent> history = new ArrayList<>();
        private final List<SseEmitter> emitters = new ArrayList<>();
        private boolean completed;
        private boolean cancelled;
    }

    private record StoredEvent(String name, String dataJson) {
    }
}
