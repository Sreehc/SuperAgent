package com.superagent.rag.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.ai.SpringAiOpenAiModelFactory;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QueryUnderstandingService {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;
    private final ObjectMapper objectMapper;
    private final ChatModelFactory chatModelFactory;
    private final StructuredOutputConverter<ModelPayload> outputConverter;

    @Autowired
    public QueryUnderstandingService(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                runtimeSettingsService,
                objectMapper,
                settings -> SpringAiOpenAiModelFactory.createChatModel(new SpringAiOpenAiModelFactory.ChatModelSettings(
                        settings.baseUrl(),
                        settings.apiKey(),
                        settings.model(),
                        settings.connectTimeoutMillis(),
                        settings.readTimeoutMillis()
                ))
        );
    }

    public QueryUnderstandingService(
            SuperAgentProperties properties,
            RuntimeSettingsService runtimeSettingsService,
            ObjectMapper objectMapper,
            ChatModelFactory chatModelFactory
    ) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
        this.objectMapper = objectMapper;
        this.chatModelFactory = chatModelFactory;
        this.outputConverter = new BeanOutputConverter<>(ModelPayload.class, objectMapper);
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
        ChatModelSettings chatSettings = new ChatModelSettings(
                settings.baseUrl(),
                settings.apiKey(),
                settings.chatModel(),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpConnectTimeoutMillis())),
                Math.toIntExact(Math.max(1L, properties.getAi().getHttpReadTimeoutMillis()))
        );
        ChatResponse response = chatModelFactory.create(chatSettings)
                .call(new Prompt(
                        buildPrompt(question, recentMessages),
                        OpenAiChatOptions.builder().model(chatSettings.model()).build()
                ));
        Generation generation = response == null ? null : response.getResult();
        String content = generation == null || generation.getOutput() == null ? "" : generation.getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new AppException(ErrorCode.MODEL_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Query understanding provider returned empty response");
        }
        return convertPayload(content);
    }

    private String buildPrompt(String question, List<String> recentMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业知识问答系统的 query understanding 模块。")
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
        builder.append("输出格式:\n").append(outputConverter.getFormat());
        return builder.toString();
    }

    private ModelPayload convertPayload(String content) throws Exception {
        try {
            return outputConverter.convert(content);
        } catch (Exception exception) {
            return objectMapper.readValue(extractJson(content), ModelPayload.class);
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelPayload(
            String rewrittenQuestion,
            List<String> subQuestions,
            String answerMode,
            Double confidence
    ) {
    }

    public record ChatModelSettings(
            String baseUrl,
            String apiKey,
            String model,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
    }

    @FunctionalInterface
    public interface ChatModelFactory {
        ChatModel create(ChatModelSettings settings);
    }
}
