package com.superagent.rag.service;

import com.superagent.chat.service.ChatModelClient;
import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RagChatComposer {

    private final ChatModelClient chatModelClient;

    public RagChatComposer(ChatModelClient chatModelClient) {
        this.chatModelClient = chatModelClient;
    }

    public RagAnswer answer(
            String userQuestion,
            String rewrittenQuestion,
            Long knowledgeBaseId,
            String memoryContext,
            List<RagEvidence> evidences,
            boolean forceCitationEnabled
    ) {
        String prompt = buildPrompt(rewrittenQuestion, memoryContext, evidences);
        ChatModelClient.ModelResponse response = chatModelClient.generateReply(new ChatModelClient.ModelRequest(
                prompt,
                "RAG",
                "SLIDING_WINDOW",
                knowledgeBaseId,
                List.of(memoryContext, userQuestion)
        ));
        RagAnswer answer = new RagAnswer(
                response.fullText(),
                response.deltas(),
                response.recommendations(),
                response.provider(),
                response.model(),
                response.inputTokens(),
                response.outputTokens(),
                response.finishReason()
        );
        return ensureCitationSuffix(answer, evidences, forceCitationEnabled);
    }

    private String buildPrompt(String rewrittenQuestion, String memoryContext, List<RagEvidence> evidences) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业知识问答助手，只能基于提供证据作答。\n");
        builder.append("会话记忆:\n").append(memoryContext).append("\n");
        builder.append("改写后问题:\n").append(rewrittenQuestion).append("\n");
        builder.append("证据:\n");
        for (int index = 0; index < evidences.size(); index++) {
            RagEvidence evidence = evidences.get(index);
            builder.append("[").append(index + 1).append("] ")
                    .append(evidence.documentTitle())
                    .append(" #").append(evidence.chunkNo())
                    .append(": ")
                    .append(evidence.content())
                    .append("\n");
        }
        builder.append("回答时引用证据编号，不要编造未出现的事实。");
        return builder.toString();
    }

    public RagAnswer noEvidence(String question) {
        String fullText = "未检索到足够证据，暂时无法基于知识库可靠回答“" + question + "”。请补充更具体的问题或上传相关文档。";
        return new RagAnswer(
                fullText,
                slice(fullText, 18),
                List.of("换一种更具体的问法", "缩小知识范围后再试"),
                "system",
                "no-evidence-fallback",
                null,
                null,
                "stop"
        );
    }

    private RagAnswer ensureCitationSuffix(RagAnswer answer, List<RagEvidence> evidences, boolean forceCitationEnabled) {
        if (!forceCitationEnabled || evidences == null || evidences.isEmpty() || containsCitation(answer.fullText())) {
            return answer;
        }
        StringBuilder suffix = new StringBuilder("\n\n参考依据：");
        int citationCount = Math.min(3, evidences.size());
        for (int index = 0; index < citationCount; index++) {
            suffix.append("[").append(index + 1).append("]");
        }
        String fullText = answer.fullText() + suffix;
        return new RagAnswer(
                fullText,
                slice(fullText, 18),
                answer.recommendations(),
                answer.provider(),
                answer.model(),
                answer.inputTokens(),
                answer.outputTokens() == null ? null : fullText.length(),
                answer.finishReason()
        );
    }

    private boolean containsCitation(String text) {
        return text != null && text.matches("(?s).*\\[\\d+].*");
    }

    private List<String> slice(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }
}
