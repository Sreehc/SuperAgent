package com.superagent.chat.service;

import java.util.List;

public interface ChatModelClient {

    ModelResponse generateReply(ModelRequest request);

    record ModelRequest(
            String userMessage,
            String sessionTitle,
            String memoryStrategy,
            Long knowledgeBaseId,
            List<String> recentMessages
    ) {
    }

    record ModelResponse(
            String fullText,
            List<String> deltas,
            List<String> recommendations,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            String finishReason
    ) {
    }
}
