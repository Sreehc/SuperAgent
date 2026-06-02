package com.superagent.agent.domain;

import java.util.List;

public record InternalAgentRunRequest(
        long tenantId,
        long sessionId,
        long exchangeId,
        long triggerMessageId,
        long actorUserId,
        String question,
        Long knowledgeBaseId,
        String memoryStrategy,
        List<String> recentMessages
) {
}
