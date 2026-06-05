package com.superagent.rag.service;

import com.superagent.chat.service.ConversationService;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.settings.domain.RagSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RagSupportService {

    private final SuperAgentProperties properties;
    private final RuntimeSettingsService runtimeSettingsService;

    public RagSupportService(SuperAgentProperties properties, RuntimeSettingsService runtimeSettingsService) {
        this.properties = properties;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public String assembleMemory(List<String> recentMessages, String question) {
        List<String> sanitizedMessages = sanitizeRecentMessages(recentMessages, question);
        if (sanitizedMessages.isEmpty()) {
            return question.trim();
        }
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, sanitizedMessages.size() - 6);
        for (int index = start; index < sanitizedMessages.size(); index++) {
            builder.append(sanitizedMessages.get(index).trim()).append("\n");
        }
        builder.append("当前问题: ").append(question.trim());
        return builder.toString().trim();
    }

    public EffectiveRagSettings resolveEffectiveSettings(ConversationService.RagOptions ragOptions) {
        RagSettings base = runtimeSettingsService == null
                ? defaultRagSettings()
                : runtimeSettingsService.resolveRagSettingsForCurrentTenant();
        return new EffectiveRagSettings(
                base.queryUnderstandingEnabled(),
                base.decompositionEnabled(),
                ragOptions == null || ragOptions.rewriteEnabled() == null ? base.rewriteEnabled() : ragOptions.rewriteEnabled(),
                ragOptions == null || ragOptions.subQuestionEnabled() == null ? base.subQuestionEnabled() : ragOptions.subQuestionEnabled(),
                base.versionConsistencyEnabled(),
                base.neighborExpansionEnabled(),
                ragOptions == null || ragOptions.vectorTopK() == null ? base.vectorTopK() : ragOptions.vectorTopK(),
                ragOptions == null || ragOptions.keywordTopK() == null ? base.keywordTopK() : ragOptions.keywordTopK(),
                base.candidateTopK(),
                ragOptions == null || ragOptions.rrfK() == null ? base.rrfK() : ragOptions.rrfK(),
                ragOptions == null || ragOptions.rerankEnabled() == null ? base.rerankEnabled() : ragOptions.rerankEnabled(),
                base.neighborWindow(),
                base.maxChunksPerDocument(),
                ragOptions == null || ragOptions.evidenceLimit() == null ? base.evidenceLimit() : ragOptions.evidenceLimit(),
                base.perQuestionEvidenceCharLimit(),
                base.totalEvidenceCharLimit(),
                ragOptions == null || ragOptions.minRelevanceScore() == null ? base.minRelevanceScore() : ragOptions.minRelevanceScore(),
                base.answerConfidenceThreshold(),
                base.queryResultCacheEnabled(),
                base.queryResultCacheTtlSeconds(),
                base.maxSubQuestions(),
                base.noEvidenceMinResults(),
                base.forceCitationEnabled()
        );
    }

    public String rewriteQuestion(String question, List<String> recentMessages, EffectiveRagSettings settings) {
        boolean enabled = settings.rewriteEnabled();
        List<String> sanitizedMessages = sanitizeRecentMessages(recentMessages, question);
        if (!enabled || sanitizedMessages.isEmpty()) {
            return question.trim();
        }
        String context = sanitizedMessages.stream()
                .skip(Math.max(0, sanitizedMessages.size() - 3))
                .map(String::trim)
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
        return "结合上下文[" + context + "]的问题：" + question.trim();
    }

    public List<String> splitSubQuestions(String rewrittenQuestion, EffectiveRagSettings settings) {
        boolean enabled = settings.subQuestionEnabled();
        int maxSubQuestions = settings.maxSubQuestions();
        if (!enabled) {
            return List.of(rewrittenQuestion);
        }
        String[] candidates = rewrittenQuestion.split("[？?；;。]");
        List<String> subQuestions = new ArrayList<>();
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if (!trimmed.isBlank()) {
                subQuestions.add(trimmed);
            }
            if (subQuestions.size() >= maxSubQuestions) {
                break;
            }
        }
        if (subQuestions.isEmpty()) {
            return List.of(rewrittenQuestion);
        }
        return subQuestions;
    }

    public RagSearchQuery resolveSearchQuery(
            String originalQuestion,
            String rewrittenQuestion,
            String subQuestion,
            int subQuestionNo,
            Long knowledgeBaseId,
            String answerMode,
            String queryUnderstandingSource,
            double queryUnderstandingConfidence,
            EffectiveRagSettings settings
    ) {
        return new RagSearchQuery(
                originalQuestion,
                rewrittenQuestion,
                subQuestion,
                subQuestionNo,
                knowledgeBaseId,
                answerMode,
                queryUnderstandingSource,
                queryUnderstandingConfidence,
                settings.versionConsistencyEnabled(),
                settings.neighborExpansionEnabled(),
                settings.vectorTopK(),
                settings.keywordTopK(),
                settings.candidateTopK(),
                settings.rrfK(),
                settings.neighborWindow(),
                settings.maxChunksPerDocument(),
                settings.evidenceLimit(),
                settings.perQuestionEvidenceCharLimit(),
                settings.totalEvidenceCharLimit(),
                settings.minRelevanceScore(),
                settings.answerConfidenceThreshold(),
                settings.queryResultCacheEnabled(),
                settings.queryResultCacheTtlSeconds(),
                settings.rerankEnabled(),
                settings.noEvidenceMinResults(),
                settings.forceCitationEnabled()
        );
    }

    public List<RagEvidence> fuseWithRrf(List<RetrievalResult> vectorResults, List<RetrievalResult> keywordResults, int rrfK) {
        Map<Long, RagEvidenceAccumulator> accumulator = new LinkedHashMap<>();
        mergeRanked(accumulator, vectorResults, rrfK);
        mergeRanked(accumulator, keywordResults, rrfK);
        double maxPossibleScore = 2.0d / (rrfK + 1.0d);
        return accumulator.values().stream()
                .map(candidate -> candidate.toEvidence(maxPossibleScore))
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
    }

    public BudgetedEvidenceResult applyThresholdAndBudget(
            String query,
            List<RagEvidence> evidences,
            double minScore,
            int evidenceLimit,
            int maxChunksPerDocument,
            int evidenceCharLimit
    ) {
        List<RagEvidence> adjusted = evidences.stream()
                .map(evidence -> adjustRelevance(query, evidence))
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();
        List<RagEvidence> thresholdFiltered = adjusted.stream()
                .filter(evidence -> evidence.score() >= minScore)
                .toList();
        int belowThresholdFilteredCount = Math.max(0, adjusted.size() - thresholdFiltered.size());
        LimitedEvidenceResult perDocumentLimited = limitPerDocument(thresholdFiltered, maxChunksPerDocument);
        LimitedEvidenceResult charLimited = limitByTotalChars(perDocumentLimited.evidences(), evidenceCharLimit);
        List<RagEvidence> finalEvidences = charLimited.evidences().stream()
                .limit(evidenceLimit)
                .toList();
        int evidenceLimitTrimmedCount = Math.max(0, charLimited.evidences().size() - finalEvidences.size());
        boolean diversityLimited = perDocumentLimited.trimmedCount() > 0 || evidenceLimitTrimmedCount > 0;
        return new BudgetedEvidenceResult(
                finalEvidences,
                diversityLimited,
                belowThresholdFilteredCount,
                perDocumentLimited.trimmedCount(),
                charLimited.trimmedCount(),
                evidenceLimitTrimmedCount
        );
    }

    public BudgetedEvidenceResult applyTotalBudget(
            List<RagEvidence> evidences,
            int evidenceLimit,
            int maxChunksPerDocument,
            int totalEvidenceCharLimit
    ) {
        LimitedEvidenceResult perDocumentLimited = limitPerDocument(
                evidences.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList(),
                maxChunksPerDocument
        );
        LimitedEvidenceResult charLimited = limitByTotalChars(perDocumentLimited.evidences(), totalEvidenceCharLimit);
        List<RagEvidence> finalEvidences = charLimited.evidences().stream()
                .limit(evidenceLimit)
                .toList();
        int evidenceLimitTrimmedCount = Math.max(0, charLimited.evidences().size() - finalEvidences.size());
        boolean diversityLimited = perDocumentLimited.trimmedCount() > 0 || evidenceLimitTrimmedCount > 0;
        return new BudgetedEvidenceResult(
                finalEvidences,
                diversityLimited,
                0,
                perDocumentLimited.trimmedCount(),
                charLimited.trimmedCount(),
                evidenceLimitTrimmedCount
        );
    }

    public double calculateAnswerConfidence(List<RagEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return 0.0d;
        }
        return evidences.stream()
                .mapToDouble(RagEvidence::score)
                .limit(3)
                .average()
                .orElse(0.0d);
    }

    private void mergeRanked(Map<Long, RagEvidenceAccumulator> accumulator, List<RetrievalResult> results, int rrfK) {
        for (int index = 0; index < results.size(); index++) {
            RetrievalResult result = results.get(index);
            double contribution = 1.0d / (rrfK + index + 1);
            accumulator.compute(result.chunkId(), (ignored, existing) -> {
                if (existing == null) {
                    return new RagEvidenceAccumulator(result, contribution);
                }
                existing.addContribution(result.channel(), contribution);
                return existing;
            });
        }
    }

    private RagSettings defaultRagSettings() {
        return new RagSettings(
                properties.getRag().getQueryUnderstandingEnabled(),
                properties.getRag().getDecompositionEnabled(),
                properties.getRag().getRewriteEnabled(),
                properties.getRag().getSubQuestionEnabled(),
                properties.getRag().getVersionConsistencyEnabled(),
                properties.getRag().getNeighborExpansionEnabled(),
                properties.getRag().getMaxSubQuestions(),
                properties.getRag().getVectorTopK(),
                properties.getRag().getKeywordTopK(),
                properties.getRag().getCandidateTopK(),
                properties.getRag().getRrfK(),
                properties.getAi().getRerankEnabled(),
                properties.getRag().getNeighborWindow(),
                properties.getRag().getMaxChunksPerDocument(),
                properties.getRag().getEvidenceLimit(),
                properties.getRag().getPerQuestionEvidenceCharLimit(),
                properties.getRag().getTotalEvidenceCharLimit(),
                properties.getRag().getMinRelevanceScore(),
                properties.getRag().getAnswerConfidenceThreshold(),
                properties.getRag().getQueryResultCacheEnabled(),
                properties.getRag().getQueryResultCacheTtlSeconds(),
                properties.getRag().getNoEvidenceMinResults(),
                properties.getRag().getForceCitationEnabled()
        );
    }

    private LimitedEvidenceResult limitByTotalChars(List<RagEvidence> evidences, int maxChars) {
        if (maxChars <= 0) {
            return new LimitedEvidenceResult(evidences, 0);
        }
        int totalChars = 0;
        List<RagEvidence> limited = new ArrayList<>();
        int trimmedCount = 0;
        for (RagEvidence evidence : evidences) {
            int candidateChars = evidence.content() == null ? 0 : evidence.content().length();
            if (!limited.isEmpty() && totalChars + candidateChars > maxChars) {
                trimmedCount++;
                continue;
            }
            limited.add(evidence);
            totalChars += candidateChars;
            if (totalChars >= maxChars) {
                break;
            }
        }
        trimmedCount += Math.max(0, evidences.size() - limited.size() - trimmedCount);
        return new LimitedEvidenceResult(limited, trimmedCount);
    }

    private LimitedEvidenceResult limitPerDocument(List<RagEvidence> evidences, int maxChunksPerDocument) {
        if (maxChunksPerDocument <= 0) {
            return new LimitedEvidenceResult(evidences, 0);
        }
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<RagEvidence> limited = new ArrayList<>();
        int trimmedCount = 0;
        for (RagEvidence evidence : evidences) {
            int currentCount = counts.getOrDefault(evidence.documentId(), 0);
            if (currentCount >= maxChunksPerDocument) {
                trimmedCount++;
                continue;
            }
            counts.put(evidence.documentId(), currentCount + 1);
            limited.add(evidence);
        }
        return new LimitedEvidenceResult(limited, trimmedCount);
    }

    private RagEvidence adjustRelevance(String query, RagEvidence evidence) {
        boolean lexicalMatched = hasMeaningfulLexicalMatch(query, evidence.content());
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) evidence.metadata().getOrDefault("channels", List.of());
        boolean keywordMatched = channels.stream().anyMatch("keyword"::equalsIgnoreCase);
        boolean neighborExpanded = Boolean.TRUE.equals(evidence.metadata().get("neighborExpanded"));
        double adjustedScore = evidence.score() * 0.2d;
        if (lexicalMatched) {
            adjustedScore += 0.45d;
        }
        if (keywordMatched) {
            adjustedScore += 0.25d;
        }
        if (neighborExpanded) {
            adjustedScore += 0.28d;
        }
        adjustedScore = Math.min(1.0d, adjustedScore);
        Map<String, Object> metadata = new LinkedHashMap<>(evidence.metadata());
        metadata.put("lexicalMatched", lexicalMatched);
        metadata.put("neighborExpanded", neighborExpanded);
        metadata.put("adjustedScore", adjustedScore);
        return new RagEvidence(
                evidence.channel(),
                evidence.knowledgeBaseId(),
                evidence.documentId(),
                evidence.chunkId(),
                evidence.documentTitle(),
                evidence.chunkNo(),
                evidence.content(),
                evidence.sectionTitle(),
                adjustedScore,
                metadata
        );
    }

    private boolean hasMeaningfulLexicalMatch(String query, String content) {
        if (query == null || query.isBlank() || content == null || content.isBlank()) {
            return false;
        }
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        long matchedCount = extractKeywordTerms(query).stream()
                .filter(token -> token.length() >= 2)
                .filter(normalizedContent::contains)
                .count();
        return matchedCount >= 1;
    }

    public List<String> extractKeywordTerms(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        collectAsciiTokens(text, tokens);
        collectCjkNgrams(text, tokens);
        return tokens.stream()
                .filter(token -> token.length() >= 2)
                .toList();
    }

    private List<String> sanitizeRecentMessages(List<String> recentMessages, String question) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        String normalizedQuestion = question == null ? "" : question.trim();
        return recentMessages.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .filter(item -> !item.equals(normalizedQuestion))
                .toList();
    }

    private void collectAsciiTokens(String text, Set<String> tokens) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{2,}").matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
    }

    private void collectCjkNgrams(String text, Set<String> tokens) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]{2,}").matcher(text);
        while (matcher.find()) {
            String segment = matcher.group();
            for (int index = 0; index < segment.length() - 1; index++) {
                tokens.add(segment.substring(index, index + 2));
            }
            if (segment.length() <= 4) {
                tokens.add(segment);
            }
        }
    }

    private static final class RagEvidenceAccumulator {
        private final long knowledgeBaseId;
        private final long documentId;
        private final long chunkId;
        private final String documentTitle;
        private final int chunkNo;
        private final String content;
        private final String sectionTitle;
        private final Map<String, Object> metadata;
        private final Set<String> channels = new LinkedHashSet<>();
        private double score;

        private RagEvidenceAccumulator(RetrievalResult result, double score) {
            this.knowledgeBaseId = result.knowledgeBaseId();
            this.documentId = result.documentId();
            this.chunkId = result.chunkId();
            this.documentTitle = result.documentTitle();
            this.chunkNo = result.chunkNo();
            this.content = result.content();
            this.sectionTitle = result.sectionTitle();
            this.metadata = new LinkedHashMap<>(result.metadata());
            this.channels.add(result.channel());
            this.score = score;
        }

        private void addContribution(String channel, double contribution) {
            this.channels.add(channel);
            this.score += contribution;
        }

        private RagEvidence toEvidence(double maxPossibleScore) {
            Map<String, Object> resolvedMetadata = new LinkedHashMap<>(metadata);
            resolvedMetadata.put("channels", channels.stream().map(item -> item.toLowerCase(Locale.ROOT)).toList());
            resolvedMetadata.put("rawRrfScore", score);
            double normalizedScore = maxPossibleScore <= 0 ? score : Math.min(1.0d, score / maxPossibleScore);
            return new RagEvidence(
                    channels.size() == 1 ? channels.iterator().next() : "hybrid",
                    knowledgeBaseId,
                    documentId,
                    chunkId,
                    documentTitle,
                    chunkNo,
                    content,
                    sectionTitle,
                    normalizedScore,
                    resolvedMetadata
            );
        }
    }

    public record EffectiveRagSettings(
            boolean queryUnderstandingEnabled,
            boolean decompositionEnabled,
            boolean rewriteEnabled,
            boolean subQuestionEnabled,
            boolean versionConsistencyEnabled,
            boolean neighborExpansionEnabled,
            int vectorTopK,
            int keywordTopK,
            int candidateTopK,
            int rrfK,
            boolean rerankEnabled,
            int neighborWindow,
            int maxChunksPerDocument,
            int evidenceLimit,
            int perQuestionEvidenceCharLimit,
            int totalEvidenceCharLimit,
            double minRelevanceScore,
            double answerConfidenceThreshold,
            boolean queryResultCacheEnabled,
            long queryResultCacheTtlSeconds,
            int maxSubQuestions,
            int noEvidenceMinResults,
            boolean forceCitationEnabled
    ) {
    }

    public record BudgetedEvidenceResult(
            List<RagEvidence> evidences,
            boolean diversityLimited,
            int belowThresholdFilteredCount,
            int perDocumentTrimmedCount,
            int charBudgetTrimmedCount,
            int evidenceLimitTrimmedCount
    ) {
    }

    private record LimitedEvidenceResult(
            List<RagEvidence> evidences,
            int trimmedCount
    ) {
    }
}
