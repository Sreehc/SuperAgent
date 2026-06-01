package com.superagent.settings.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.domain.RagSettings;
import com.superagent.settings.domain.RerankSettings;
import com.superagent.settings.repository.AuditLogRepository;
import com.superagent.settings.repository.RuntimeSettingsRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeSettingsService {

    private static final String MODEL_SECTION = "model";
    private static final String RAG_SECTION = "rag";
    private static final String RERANK_SECTION = "rerank";

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final RuntimeSettingsRepository runtimeSettingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final SuperAgentProperties properties;

    public RuntimeSettingsService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            RuntimeSettingsRepository runtimeSettingsRepository,
            AuditLogRepository auditLogRepository,
            SuperAgentProperties properties
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.runtimeSettingsRepository = runtimeSettingsRepository;
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    public ModelSettings getModelSettings() {
        requireOwnerOrAdmin();
        return resolveModelSettings(requireTenantContext().tenantId());
    }

    @Transactional
    public SecretUpdateResult updateModelSettings(ModelSettingsPatch patch) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        ModelSettings existing = resolveModelSettings(tenantContext.tenantId());
        ModelSettings merged = new ModelSettings(
                existing.provider(),
                firstNonBlank(patch.baseUrl(), existing.baseUrl()),
                firstNonBlank(patch.chatModel(), existing.chatModel()),
                firstNonBlank(patch.embeddingModel(), existing.embeddingModel()),
                firstNonBlank(patch.apiKey(), existing.apiKey())
        );
        runtimeSettingsRepository.upsertSection(tenantContext.tenantId(), MODEL_SECTION, toStorageMap(merged));
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "settings.model.updated",
                "runtime_setting",
                null,
                Map.of(
                        "section", MODEL_SECTION,
                        "provider", merged.provider(),
                        "baseUrl", merged.baseUrl(),
                        "chatModel", merged.chatModel(),
                        "embeddingModel", merged.embeddingModel(),
                        "apiKeySet", merged.apiKeySet()
                )
        );
        return new SecretUpdateResult(true, merged.apiKeySet());
    }

    public RagSettings getRagSettings() {
        requireOwnerOrAdmin();
        return resolveRagSettings(requireTenantContext().tenantId());
    }

    @Transactional
    public UpdateResult updateRagSettings(RagSettingsPatch patch) {
        AuthenticatedUserPrincipal principal = requireOwnerOrAdmin();
        TenantContext tenantContext = requireTenantContext();
        RagSettings existing = resolveRagSettings(tenantContext.tenantId());
        RagSettings merged = new RagSettings(
                patch.rewriteEnabled() == null ? existing.rewriteEnabled() : patch.rewriteEnabled(),
                patch.subQuestionEnabled() == null ? existing.subQuestionEnabled() : patch.subQuestionEnabled(),
                patch.maxSubQuestions() == null ? existing.maxSubQuestions() : patch.maxSubQuestions(),
                patch.vectorTopK() == null ? existing.vectorTopK() : patch.vectorTopK(),
                patch.keywordTopK() == null ? existing.keywordTopK() : patch.keywordTopK(),
                patch.rrfK() == null ? existing.rrfK() : patch.rrfK(),
                patch.rerankEnabled() == null ? existing.rerankEnabled() : patch.rerankEnabled(),
                patch.evidenceLimit() == null ? existing.evidenceLimit() : patch.evidenceLimit(),
                patch.perQuestionEvidenceCharLimit() == null ? existing.perQuestionEvidenceCharLimit() : patch.perQuestionEvidenceCharLimit(),
                patch.totalEvidenceCharLimit() == null ? existing.totalEvidenceCharLimit() : patch.totalEvidenceCharLimit(),
                patch.minRelevanceScore() == null ? existing.minRelevanceScore() : patch.minRelevanceScore()
        );
        runtimeSettingsRepository.upsertSection(tenantContext.tenantId(), RAG_SECTION, toStorageMap(merged));
        Map<String, Object> ragDetail = new LinkedHashMap<>();
        ragDetail.put("section", RAG_SECTION);
        ragDetail.put("rewriteEnabled", merged.rewriteEnabled());
        ragDetail.put("subQuestionEnabled", merged.subQuestionEnabled());
        ragDetail.put("maxSubQuestions", merged.maxSubQuestions());
        ragDetail.put("vectorTopK", merged.vectorTopK());
        ragDetail.put("keywordTopK", merged.keywordTopK());
        ragDetail.put("rrfK", merged.rrfK());
        ragDetail.put("rerankEnabled", merged.rerankEnabled());
        ragDetail.put("evidenceLimit", merged.evidenceLimit());
        ragDetail.put("perQuestionEvidenceCharLimit", merged.perQuestionEvidenceCharLimit());
        ragDetail.put("totalEvidenceCharLimit", merged.totalEvidenceCharLimit());
        ragDetail.put("minRelevanceScore", merged.minRelevanceScore());
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "settings.rag.updated",
                "runtime_setting",
                null,
                ragDetail
        );
        return new UpdateResult(true);
    }

    public RerankSettings getRerankSettings() {
        requireOwnerOrAdmin();
        return resolveRerankSettings(requireTenantContext().tenantId());
    }

    @Transactional
    public SecretUpdateResult updateRerankSettings(RerankSettingsPatch patch) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        RerankSettings existing = resolveRerankSettings(tenantContext.tenantId());
        RerankSettings merged = new RerankSettings(
                patch.enabled() == null ? existing.enabled() : patch.enabled(),
                firstNonBlank(patch.provider(), existing.provider()),
                normalizeNullable(patch.baseUrl()) == null ? existing.baseUrl() : patch.baseUrl().trim(),
                normalizeNullable(patch.model()) == null ? existing.model() : patch.model().trim(),
                firstNonBlank(patch.apiKey(), existing.apiKey())
        );
        runtimeSettingsRepository.upsertSection(tenantContext.tenantId(), RERANK_SECTION, toStorageMap(merged));
        Map<String, Object> rerankDetail = new LinkedHashMap<>();
        rerankDetail.put("section", RERANK_SECTION);
        rerankDetail.put("enabled", merged.enabled());
        rerankDetail.put("provider", merged.provider());
        rerankDetail.put("baseUrl", merged.baseUrl());
        rerankDetail.put("model", merged.model());
        rerankDetail.put("apiKeySet", merged.apiKeySet());
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "settings.rerank.updated",
                "runtime_setting",
                null,
                rerankDetail
        );
        return new SecretUpdateResult(true, merged.apiKeySet());
    }

    public RagSettings resolveRagSettingsForCurrentTenant() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            return defaultRagSettings();
        }
        return resolveRagSettings(tenantContext.tenantId());
    }

    public ModelSettings resolveModelSettings(long tenantId) {
        Map<String, Object> overrides = runtimeSettingsRepository.findSection(tenantId, MODEL_SECTION).orElse(Map.of());
        return new ModelSettings(
                "openai-compatible",
                getString(overrides, "baseUrl", properties.getAi().getOpenaiCompatibleBaseUrl()),
                getString(overrides, "chatModel", properties.getAi().getChatModel()),
                getString(overrides, "embeddingModel", properties.getAi().getEmbeddingModel()),
                getString(overrides, "apiKey", properties.getAi().getApiKey())
        );
    }

    public RagSettings resolveRagSettings(long tenantId) {
        Map<String, Object> overrides = runtimeSettingsRepository.findSection(tenantId, RAG_SECTION).orElse(Map.of());
        return new RagSettings(
                getBoolean(overrides, "rewriteEnabled", properties.getRag().getRewriteEnabled()),
                getBoolean(overrides, "subQuestionEnabled", properties.getRag().getSubQuestionEnabled()),
                getInt(overrides, "maxSubQuestions", properties.getRag().getMaxSubQuestions()),
                getInt(overrides, "vectorTopK", properties.getRag().getVectorTopK()),
                getInt(overrides, "keywordTopK", properties.getRag().getKeywordTopK()),
                getInt(overrides, "rrfK", properties.getRag().getRrfK()),
                getBoolean(overrides, "rerankEnabled", properties.getAi().getRerankEnabled()),
                getInt(overrides, "evidenceLimit", properties.getRag().getEvidenceLimit()),
                getInt(overrides, "perQuestionEvidenceCharLimit", properties.getRag().getPerQuestionEvidenceCharLimit()),
                getInt(overrides, "totalEvidenceCharLimit", properties.getRag().getTotalEvidenceCharLimit()),
                getDouble(overrides, "minRelevanceScore", properties.getRag().getMinRelevanceScore())
        );
    }

    public RerankSettings resolveRerankSettings(long tenantId) {
        Map<String, Object> overrides = runtimeSettingsRepository.findSection(tenantId, RERANK_SECTION).orElse(Map.of());
        return new RerankSettings(
                getBoolean(overrides, "enabled", properties.getAi().getRerankEnabled()),
                getString(overrides, "provider", "openai-compatible"),
                getNullableString(overrides, "baseUrl"),
                getNullableString(overrides, "model"),
                getNullableString(overrides, "apiKey")
        );
    }

    private RagSettings defaultRagSettings() {
        return new RagSettings(
                properties.getRag().getRewriteEnabled(),
                properties.getRag().getSubQuestionEnabled(),
                properties.getRag().getMaxSubQuestions(),
                properties.getRag().getVectorTopK(),
                properties.getRag().getKeywordTopK(),
                properties.getRag().getRrfK(),
                properties.getAi().getRerankEnabled(),
                properties.getRag().getEvidenceLimit(),
                properties.getRag().getPerQuestionEvidenceCharLimit(),
                properties.getRag().getTotalEvidenceCharLimit(),
                properties.getRag().getMinRelevanceScore()
        );
    }

    private Map<String, Object> toStorageMap(ModelSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider", settings.provider());
        map.put("baseUrl", settings.baseUrl());
        map.put("chatModel", settings.chatModel());
        map.put("embeddingModel", settings.embeddingModel());
        map.put("apiKey", settings.apiKey());
        return map;
    }

    private Map<String, Object> toStorageMap(RagSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rewriteEnabled", settings.rewriteEnabled());
        map.put("subQuestionEnabled", settings.subQuestionEnabled());
        map.put("maxSubQuestions", settings.maxSubQuestions());
        map.put("vectorTopK", settings.vectorTopK());
        map.put("keywordTopK", settings.keywordTopK());
        map.put("rrfK", settings.rrfK());
        map.put("rerankEnabled", settings.rerankEnabled());
        map.put("evidenceLimit", settings.evidenceLimit());
        map.put("perQuestionEvidenceCharLimit", settings.perQuestionEvidenceCharLimit());
        map.put("totalEvidenceCharLimit", settings.totalEvidenceCharLimit());
        map.put("minRelevanceScore", settings.minRelevanceScore());
        return map;
    }

    private Map<String, Object> toStorageMap(RerankSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", settings.enabled());
        map.put("provider", settings.provider());
        map.put("baseUrl", settings.baseUrl());
        map.put("model", settings.model());
        map.put("apiKey", settings.apiKey());
        return map;
    }

    private AuthenticatedUserPrincipal requireOwnerOrAdmin() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Admin permission required");
        }
        return principal;
    }

    private AuthenticatedUserPrincipal requireOwner() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Owner permission required");
        }
        return principal;
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private String firstNonBlank(String candidate, String fallback) {
        return normalizeNullable(candidate) == null ? fallback : candidate.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String getString(Map<String, Object> source, String key, String fallback) {
        String value = getNullableString(source, key);
        return value == null ? fallback : value;
    }

    private String getNullableString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : value.toString();
    }

    private boolean getBoolean(Map<String, Object> source, String key, boolean fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(value.toString());
    }

    private int getInt(Map<String, Object> source, String key, int fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private double getDouble(Map<String, Object> source, String key, double fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
    }

    public record ModelSettingsPatch(
            String baseUrl,
            String chatModel,
            String embeddingModel,
            String apiKey
    ) {
    }

    public record RagSettingsPatch(
            Boolean rewriteEnabled,
            Boolean subQuestionEnabled,
            Integer maxSubQuestions,
            Integer vectorTopK,
            Integer keywordTopK,
            Integer rrfK,
            Boolean rerankEnabled,
            Integer evidenceLimit,
            Integer perQuestionEvidenceCharLimit,
            Integer totalEvidenceCharLimit,
            Double minRelevanceScore
    ) {
    }

    public record RerankSettingsPatch(
            Boolean enabled,
            String provider,
            String baseUrl,
            String model,
            String apiKey
    ) {
    }

    public record UpdateResult(boolean updated) {
    }

    public record SecretUpdateResult(boolean updated, boolean apiKeySet) {
    }
}
