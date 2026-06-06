package com.superagent.chat;

import com.superagent.chat.service.ChatModelClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestChatModelClientConfiguration {

    @Bean
    @Primary
    public ChatModelClient testChatModelClient() {
        return request -> {
            String fullText = buildAnswer(request.userMessage());
            return new ChatModelClient.ModelResponse(
                    fullText,
                    slice(fullText, 20),
                    List.of("请总结一下这次回答", "继续展开下一步建议"),
                    "test-provider",
                    "test-chat-model",
                    request.userMessage() == null ? 0 : request.userMessage().length(),
                    fullText.length(),
                    "stop"
            );
        };
    }

    private String buildAnswer(String input) {
        if (input != null && (input.contains("那申请材料呢") || input.contains("申请材料呢"))) {
            return "根据知识库证据，申请材料包括订单截图和退款原因说明。[1]";
        }
        if (input != null && input.contains("申请材料需要什么")) {
            return "根据知识库证据，退款规则需要在 7 日内提交申请，申请材料包括订单截图和退款原因说明。[1][2]";
        }
        if (input != null && input.contains("越界引用")) {
            return "根据知识库证据，退款规则包括在 7 日内提交申请并提供订单截图。[9]";
        }
        if (input != null && input.contains("退款")) {
            return "根据知识库证据，退款规则包括在 7 日内提交申请并提供订单截图。[1]";
        }
        return "这是测试环境返回的回答。[1]";
    }

    private List<String> slice(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }
}
