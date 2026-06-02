package com.superagent.chat.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "super-agent.ai.chat-provider", havingValue = "local-fake", matchIfMissing = true)
public class FakeChatModelClient implements ChatModelClient {

    @Override
    public ModelResponse generateReply(ModelRequest request) {
        String prefix = request.knowledgeBaseId() == null
                ? "这是最小可用对话链路返回的演示回答。"
                : "已按当前知识范围返回最小可用回答。";
        String fullText = prefix + " 你刚刚问的是：“" + request.userMessage() + "”。"
                + " 当前会话记忆策略为 " + request.memoryStrategy() + "，后续阶段会接入真实模型、检索和引用增强。";

        List<String> deltas = slice(fullText, 18);
        List<String> recommendations = List.of(
                "请总结一下这次回答",
                "继续展开下一步建议"
        );
        return new ModelResponse(
                fullText,
                deltas,
                recommendations,
                "local-fake",
                "fake-chat-model",
                request.userMessage() == null ? 0 : request.userMessage().length(),
                fullText.length(),
                "stop"
        );
    }

    private List<String> slice(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }
}
