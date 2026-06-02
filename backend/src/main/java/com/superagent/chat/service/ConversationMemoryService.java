package com.superagent.chat.service;

import com.superagent.chat.domain.ConversationMemorySummary;
import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.repository.ConversationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private static final int SUMMARY_TRIGGER_MESSAGE_COUNT = 6;
    private static final int WINDOW_MESSAGE_COUNT = 6;

    private final ConversationRepository conversationRepository;

    public ConversationMemoryService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public List<String> buildContext(long tenantId, long sessionId, MemoryStrategy strategy) {
        return switch (strategy) {
            case NONE -> List.of();
            case SLIDING_WINDOW -> loadWindow(sessionId, tenantId);
            case SUMMARY_WINDOW -> combineSummaryAndWindow(tenantId, sessionId, false);
            case SUMMARY_PLUS_WINDOW -> combineSummaryAndWindow(tenantId, sessionId, true);
        };
    }

    public void refreshSummaryIfNeeded(long tenantId, long sessionId) {
        ConversationMemorySummary latest = conversationRepository.findLatestMemorySummary(tenantId, sessionId).orElse(null);
        Long afterMessageId = latest == null ? null : latest.coveredMessageId();
        List<ConversationMessage> candidates = conversationRepository.findMessagesAfter(sessionId, tenantId, afterMessageId, 20);
        if (candidates.size() < SUMMARY_TRIGGER_MESSAGE_COUNT) {
            return;
        }
        ConversationMessage last = candidates.getLast();
        String summaryText = summarize(candidates);
        int version = latest == null ? 1 : latest.version() + 1;
        conversationRepository.createMemorySummary(tenantId, sessionId, summaryText, last.id(), version);
    }

    private List<String> loadWindow(long sessionId, long tenantId) {
        return conversationRepository.findMessages(sessionId, tenantId, 1, WINDOW_MESSAGE_COUNT).stream()
                .map(message -> message.role().name() + ": " + message.content())
                .toList();
    }

    private List<String> combineSummaryAndWindow(long tenantId, long sessionId, boolean includeWindow) {
        List<String> context = new ArrayList<>();
        conversationRepository.findLatestMemorySummary(tenantId, sessionId)
                .ifPresent(summary -> context.add("summary: " + summary.summaryText()));
        if (includeWindow || context.isEmpty()) {
            context.addAll(loadWindow(sessionId, tenantId));
        }
        return context;
    }

    private String summarize(List<ConversationMessage> messages) {
        StringBuilder builder = new StringBuilder("对话摘要：");
        for (ConversationMessage message : messages) {
            if (builder.length() > 600) {
                break;
            }
            builder.append('[')
                    .append(message.role().name())
                    .append("] ")
                    .append(excerpt(message.content()))
                    .append(" ; ");
        }
        return builder.toString();
    }

    private String excerpt(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
