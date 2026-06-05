package com.superagent.rag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class QueryUnderstandingService {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;
    private final ObjectMapper objectMapper;

    public QueryUnderstandingService(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
        this.objectMapper = objectMapper;
    }

    public QueryUnderstandingResult understand(
            String question,
            List<String> recentMessages,
            RagSupportService.EffectiveRagSettings settings,
            RagSupportService supportService
    ) {
        QueryUnderstandingResult fallback = fallback(question, recentMessages, settings, supportService, "rule_fallback", 0.55d);
        if (!settings.queryUnderstandingEnabled()) {
            return fallback.withSource("config_disabled", 0.5d);
        }
        if (!"openai-compatible".equalsIgnoreCase(properties.getAi().getChatProvider())) {
            return fallback.withSource("provider_unavailable", 0.5d);
        }
        try {
            ModelPayload payload = callProvider(question, recentMessages);
            return normalize(payload, question, recentMessages, settings, supportService);
        } catch (Exception ignored) {
            return fallback.withSource("rule_fallback", 0.55d);
        }
    }

    private QueryUnderstandingResult normalize(
            ModelPayload payload,
            String question,
            List<String> recentMessages,
            RagSupportService.EffectiveRagSettings settings,
            RagSupportService supportService
    ) {
        String normalizedQuestion = question == null ? "" : question.trim();
        String rewrittenQuestion = settings.rewriteEnabled()
                ? firstNonBlank(payload.rewrittenQuestion(), normalizedQuestion)
                : normalizedQuestion;
        List<String> subQuestions = resolveSubQuestions(payload.subQuestions(), rewrittenQuestion, settings, supportService);
        String answerMode = firstNonBlank(payload.answerMode(), subQuestions.size() > 1 ? "decomposed_multi_question" : "single_question")
                .toLowerCase(Locale.ROOT);
        double confidence = payload.confidence() == null ? 0.82d : clampConfidence(payload.confidence());
        String source = "model";
        return new QueryUnderstandingResult(
                rewrittenQuestion,
                subQuestions,
                answerMode,
                source,
                confidence
        );
    }

    private List<String> resolveSubQuestions(
            List<String> modelSubQuestions,
            String rewrittenQuestion,
            RagSupportService.EffectiveRagSettings settings,
            RagSupportService supportService
    ) {
        if (!(settings.decompositionEnabled() && settings.subQuestionEnabled())) {
            return List.of(rewrittenQuestion);
        }
        List<String> normalized = sanitizeSubQuestions(modelSubQuestions, settings.maxSubQuestions());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return supportService.splitSubQuestions(rewrittenQuestion, settings);
    }

    private QueryUnderstandingResult fallback(
            String question,
            List<String> recentMessages,
            RagSupportService.EffectiveRagSettings settings,
            RagSupportService supportService,
            String source,
            double confidence
    ) {
        String rewrittenQuestion = supportService.rewriteQuestion(question, recentMessages, settings);
        List<String> subQuestions = (settings.decompositionEnabled() && settings.subQuestionEnabled())
                ? supportService.splitSubQuestions(rewrittenQuestion, settings)
                : List.of(rewrittenQuestion);
        String answerMode = subQuestions.size() > 1 ? "decomposed_multi_question" : "single_question";
        return new QueryUnderstandingResult(rewrittenQuestion, subQuestions, answerMode, source, confidence);
    }

    private ModelPayload callProvider(String question, List<String> recentMessages) throws Exception {
        ModelSettings settings = resolveSettings();
        validateSettings(settings);
        RestClient client = RestClient.builder()
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        ChatCompletionResponse response = client.post()
                .uri("/chat/completions")
                .body(new ChatCompletionRequest(
                        settings.chatModel(),
                        List.of(new ChatCompletionMessage("user", buildPrompt(question, recentMessages)))
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Query understanding provider returned empty response");
        }
        ChatCompletionChoice choice = response.choices().getFirst();
        if (choice.message() == null || choice.message().content() == null || choice.message().content().isBlank()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Query understanding provider returned empty content");
        }
        return objectMapper.readValue(extractJson(choice.message().content()), ModelPayload.class);
    }

    private String buildPrompt(String question, List<String> recentMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业知识问答系统的 query understanding 模块。")
                .append("输出严格 JSON，不要输出解释。")
                .append("字段必须包含 rewrittenQuestion、subQuestions、answerMode、confidence。")
                .append("answerMode 只能是 single_question 或 decomposed_multi_question。")
                .append("如果无需拆分，subQuestions 只返回一个元素。")
                .append("\n最近对话:\n");
        if (recentMessages != null) {
            for (String item : recentMessages.stream().skip(Math.max(0, recentMessages.size() - 4)).toList()) {
                if (item != null && !item.isBlank()) {
                    builder.append("- ").append(item.trim()).append("\n");
                }
            }
        }
        builder.append("当前问题:\n").append(question == null ? "" : question.trim()).append("\n");
        builder.append("输出示例:\n")
                .append("{\"rewrittenQuestion\":\"...\",\"subQuestions\":[\"...\"],\"answerMode\":\"single_question\",\"confidence\":0.86}");
        return builder.toString();
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private List<String> sanitizeSubQuestions(List<String> questions, int maxSubQuestions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String item : questions) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                deduplicated.add(trimmed);
            }
            if (deduplicated.size() >= maxSubQuestions) {
                break;
            }
        }
        return new ArrayList<>(deduplicated);
    }

    private ModelSettings resolveSettings() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return runtimeSettingsService.resolveModelSettings(tenantContext.tenantId());
    }

    private void validateSettings(ModelSettings settings) {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()
                || settings.chatModel() == null || settings.chatModel().isBlank()
                || settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Query understanding provider configuration is incomplete");
        }
    }

    private String firstNonBlank(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        return candidate.trim();
    }

    private double clampConfidence(double confidence) {
        return Math.max(0.0d, Math.min(1.0d, confidence));
    }

    public record QueryUnderstandingResult(
            String rewrittenQuestion,
            List<String> subQuestions,
            String answerMode,
            String source,
            double confidence
    ) {
        public QueryUnderstandingResult withSource(String newSource, double newConfidence) {
            return new QueryUnderstandingResult(
                    rewrittenQuestion,
                    subQuestions,
                    answerMode,
                    newSource,
                    newConfidence
            );
        }
    }

    public record ChatCompletionRequest(String model, List<ChatCompletionMessage> messages) {
    }

    public record ChatCompletionMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(
            String id,
            String model,
            List<ChatCompletionChoice> choices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionChoice(
            int index,
            ChatCompletionMessage message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelPayload(
            String rewrittenQuestion,
            List<String> subQuestions,
            String answerMode,
            Double confidence
    ) {
    }
}
