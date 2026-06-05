package com.superagent.rag.service;

import com.superagent.chat.service.ChatModelClient;
import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RagChatComposer {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

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
                response.finishReason(),
                false
        );
        return normalizeCitations(answer, evidences, forceCitationEnabled);
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
                "stop",
                false
        );
    }

    private RagAnswer normalizeCitations(RagAnswer answer, List<RagEvidence> evidences, boolean forceCitationEnabled) {
        if (answer == null || evidences == null || evidences.isEmpty()) {
            return answer;
        }
        CitationState citationState = inspectCitations(answer.fullText(), evidences.size());
        if (citationState.hasAnyCitation() && citationState.allValid()) {
            return answer;
        }
        if (!citationState.hasAnyCitation() && !forceCitationEnabled) {
            return answer;
        }

        List<Integer> citationOrdinals = citationState.validOrdinals();
        if (citationOrdinals.isEmpty() && forceCitationEnabled) {
            citationOrdinals = defaultCitationOrdinals(evidences.size());
        }

        String normalized = stripCitationMarkers(answer.fullText());
        if (!citationOrdinals.isEmpty()) {
            normalized = appendCitationSuffix(normalized, citationOrdinals);
        }
        boolean citationAppended = !normalized.equals(answer.fullText());
        return rebuildAnswer(answer, normalized, citationAppended);
    }

    private CitationState inspectCitations(String text, int evidenceCount) {
        Matcher matcher = CITATION_PATTERN.matcher(text == null ? "" : text);
        LinkedHashSet<Integer> validOrdinals = new LinkedHashSet<>();
        boolean hasAnyCitation = false;
        boolean invalidCitation = false;
        while (matcher.find()) {
            hasAnyCitation = true;
            int ordinal = Integer.parseInt(matcher.group(1));
            if (ordinal >= 1 && ordinal <= evidenceCount) {
                validOrdinals.add(ordinal);
            } else {
                invalidCitation = true;
            }
        }
        return new CitationState(hasAnyCitation, !invalidCitation && !validOrdinals.isEmpty(), List.copyOf(validOrdinals));
    }

    private List<Integer> defaultCitationOrdinals(int evidenceCount) {
        List<Integer> ordinals = new ArrayList<>();
        for (int index = 1; index <= Math.min(3, evidenceCount); index++) {
            ordinals.add(index);
        }
        return ordinals;
    }

    private String stripCitationMarkers(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = CITATION_PATTERN.matcher(text).replaceAll("");
        stripped = stripped.replaceAll("[ \\t]+\\n", "\n");
        stripped = stripped.replaceAll("\\n{3,}", "\n\n");
        return stripped.trim();
    }

    private String appendCitationSuffix(String text, List<Integer> ordinals) {
        StringBuilder builder = new StringBuilder(text == null ? "" : text);
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("参考依据：");
        for (Integer ordinal : ordinals) {
            builder.append("[").append(ordinal).append("]");
        }
        return builder.toString();
    }

    private RagAnswer rebuildAnswer(RagAnswer answer, String fullText, boolean citationAppended) {
        return new RagAnswer(
                fullText,
                slice(fullText, 18),
                answer.recommendations(),
                answer.provider(),
                answer.model(),
                answer.inputTokens(),
                answer.outputTokens() == null ? null : fullText.length(),
                answer.finishReason(),
                citationAppended
        );
    }

    private List<String> slice(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }

    private record CitationState(
            boolean hasAnyCitation,
            boolean allValid,
            List<Integer> validOrdinals
    ) {
    }
}
