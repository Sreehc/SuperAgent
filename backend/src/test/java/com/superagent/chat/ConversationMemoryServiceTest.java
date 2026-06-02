package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.superagent.chat.domain.ConversationMemorySummary;
import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.domain.MessageRole;
import com.superagent.chat.repository.ConversationRepository;
import com.superagent.chat.service.ConversationMemoryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Test
    void shouldBuildContextForAllMemoryStrategies() {
        ConversationMemoryService service = new ConversationMemoryService(conversationRepository);
        ConversationMemorySummary summary = summary(9L, 2, "对话摘要：已讨论退款、配置和日志排查。");
        List<ConversationMessage> window = List.of(
                message(11L, MessageRole.user, "退款规则是什么？"),
                message(12L, MessageRole.assistant, "需要在 7 日内提交。")
        );

        when(conversationRepository.findLatestMemorySummary(10001L, 301L)).thenReturn(Optional.of(summary));
        when(conversationRepository.findMessages(301L, 10001L, 1, 6)).thenReturn(window);

        assertThat(service.buildContext(10001L, 301L, MemoryStrategy.NONE)).isEmpty();
        assertThat(service.buildContext(10001L, 301L, MemoryStrategy.SLIDING_WINDOW))
                .containsExactly("user: 退款规则是什么？", "assistant: 需要在 7 日内提交。");
        assertThat(service.buildContext(10001L, 301L, MemoryStrategy.SUMMARY_WINDOW))
                .containsExactly("summary: 对话摘要：已讨论退款、配置和日志排查。");
        assertThat(service.buildContext(10001L, 301L, MemoryStrategy.SUMMARY_PLUS_WINDOW))
                .containsExactly(
                        "summary: 对话摘要：已讨论退款、配置和日志排查。",
                        "user: 退款规则是什么？",
                        "assistant: 需要在 7 日内提交。"
                );
    }

    @Test
    void shouldFallbackToWindowWhenSummaryWindowHasNoSummaryYet() {
        ConversationMemoryService service = new ConversationMemoryService(conversationRepository);
        List<ConversationMessage> window = List.of(
                message(21L, MessageRole.user, "请帮我总结部署日志。"),
                message(22L, MessageRole.assistant, "最近一次错误发生在启动阶段。")
        );

        when(conversationRepository.findLatestMemorySummary(10001L, 302L)).thenReturn(Optional.empty());
        when(conversationRepository.findMessages(302L, 10001L, 1, 6)).thenReturn(window);

        assertThat(service.buildContext(10001L, 302L, MemoryStrategy.SUMMARY_WINDOW))
                .containsExactly(
                        "user: 请帮我总结部署日志。",
                        "assistant: 最近一次错误发生在启动阶段。"
                );
    }

    @Test
    void shouldRefreshSummaryWhenThresholdReachedAndKeepVersionedCoverage() {
        ConversationMemoryService service = new ConversationMemoryService(conversationRepository);
        ConversationMemorySummary latest = summary(29L, 3, "对话摘要：旧摘要。");
        List<ConversationMessage> messages = List.of(
                message(31L, MessageRole.user, "第一条新消息"),
                message(32L, MessageRole.assistant, "第二条新消息"),
                message(33L, MessageRole.user, "第三条新消息"),
                message(34L, MessageRole.assistant, "第四条新消息"),
                message(35L, MessageRole.user, "第五条新消息"),
                message(36L, MessageRole.assistant, "第六条新消息")
        );

        when(conversationRepository.findLatestMemorySummary(10001L, 303L)).thenReturn(Optional.of(latest));
        when(conversationRepository.findMessagesAfter(303L, 10001L, 29L, 20)).thenReturn(messages);

        service.refreshSummaryIfNeeded(10001L, 303L);

        verify(conversationRepository).createMemorySummary(
                eq(10001L),
                eq(303L),
                org.mockito.ArgumentMatchers.contains("对话摘要："),
                eq(36L),
                eq(4)
        );
    }

    @Test
    void shouldSkipSummaryRefreshWhenConversationIsTooShort() {
        ConversationMemoryService service = new ConversationMemoryService(conversationRepository);
        when(conversationRepository.findLatestMemorySummary(10001L, 304L)).thenReturn(Optional.empty());
        when(conversationRepository.findMessagesAfter(304L, 10001L, null, 20)).thenReturn(List.of(
                message(41L, MessageRole.user, "第一条"),
                message(42L, MessageRole.assistant, "第二条"),
                message(43L, MessageRole.user, "第三条")
        ));

        service.refreshSummaryIfNeeded(10001L, 304L);

        verify(conversationRepository, never()).createMemorySummary(eq(10001L), eq(304L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private ConversationMessage message(long id, MessageRole role, String content) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConversationMessage(id, 10001L, 301L, role, content, "success", null, now, now);
    }

    private ConversationMemorySummary summary(long coveredMessageId, int version, String text) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConversationMemorySummary(1L, 10001L, 301L, text, coveredMessageId, version, now, now);
    }
}
