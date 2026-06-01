package com.superagent.settings.web;

import com.superagent.common.api.ApiResponse;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.domain.RagSettings;
import com.superagent.settings.domain.RerankSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/settings")
public class AdminSettingsController {

    private final RuntimeSettingsService runtimeSettingsService;

    public AdminSettingsController(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @GetMapping("/model")
    public ApiResponse<ModelSettingsItem> getModelSettings() {
        ModelSettings settings = runtimeSettingsService.getModelSettings();
        return ApiResponse.success(new ModelSettingsItem(
                settings.provider(),
                settings.baseUrl(),
                settings.chatModel(),
                settings.embeddingModel(),
                settings.apiKeySet()
        ));
    }

    @PatchMapping("/model")
    public ApiResponse<SecretUpdateResponse> updateModelSettings(@Valid @RequestBody UpdateModelSettingsRequest request) {
        RuntimeSettingsService.SecretUpdateResult result = runtimeSettingsService.updateModelSettings(new RuntimeSettingsService.ModelSettingsPatch(
                request.baseUrl(),
                request.chatModel(),
                request.embeddingModel(),
                request.apiKey()
        ));
        return ApiResponse.success(new SecretUpdateResponse(result.updated(), result.apiKeySet()));
    }

    @GetMapping("/rag")
    public ApiResponse<RagSettingsItem> getRagSettings() {
        RagSettings settings = runtimeSettingsService.getRagSettings();
        return ApiResponse.success(new RagSettingsItem(
                settings.rewriteEnabled(),
                settings.subQuestionEnabled(),
                settings.maxSubQuestions(),
                settings.vectorTopK(),
                settings.keywordTopK(),
                settings.rrfK(),
                settings.rerankEnabled(),
                settings.evidenceLimit(),
                settings.perQuestionEvidenceCharLimit(),
                settings.totalEvidenceCharLimit(),
                settings.minRelevanceScore()
        ));
    }

    @PatchMapping("/rag")
    public ApiResponse<UpdateResponse> updateRagSettings(@Valid @RequestBody UpdateRagSettingsRequest request) {
        RuntimeSettingsService.UpdateResult result = runtimeSettingsService.updateRagSettings(new RuntimeSettingsService.RagSettingsPatch(
                request.rewriteEnabled(),
                request.subQuestionEnabled(),
                request.maxSubQuestions(),
                request.vectorTopK(),
                request.keywordTopK(),
                request.rrfK(),
                request.rerankEnabled(),
                request.evidenceLimit(),
                request.perQuestionEvidenceCharLimit(),
                request.totalEvidenceCharLimit(),
                request.minRelevanceScore()
        ));
        return ApiResponse.success(new UpdateResponse(result.updated()));
    }

    @GetMapping("/rerank")
    public ApiResponse<RerankSettingsItem> getRerankSettings() {
        RerankSettings settings = runtimeSettingsService.getRerankSettings();
        return ApiResponse.success(new RerankSettingsItem(
                settings.enabled(),
                settings.provider(),
                settings.baseUrl(),
                settings.model(),
                settings.apiKeySet()
        ));
    }

    @PatchMapping("/rerank")
    public ApiResponse<SecretUpdateResponse> updateRerankSettings(@Valid @RequestBody UpdateRerankSettingsRequest request) {
        RuntimeSettingsService.SecretUpdateResult result = runtimeSettingsService.updateRerankSettings(new RuntimeSettingsService.RerankSettingsPatch(
                request.enabled(),
                request.provider(),
                request.baseUrl(),
                request.model(),
                request.apiKey()
        ));
        return ApiResponse.success(new SecretUpdateResponse(result.updated(), result.apiKeySet()));
    }

    public record ModelSettingsItem(
            String provider,
            String baseUrl,
            String chatModel,
            String embeddingModel,
            boolean apiKeySet
    ) {
    }

    public record UpdateModelSettingsRequest(
            @Size(max = 500) String baseUrl,
            @Size(max = 128) String chatModel,
            @Size(max = 128) String embeddingModel,
            @Size(max = 512) String apiKey
    ) {
    }

    public record RagSettingsItem(
            boolean rewriteEnabled,
            boolean subQuestionEnabled,
            int maxSubQuestions,
            int vectorTopK,
            int keywordTopK,
            int rrfK,
            boolean rerankEnabled,
            int evidenceLimit,
            int perQuestionEvidenceCharLimit,
            int totalEvidenceCharLimit,
            double minRelevanceScore
    ) {
    }

    public record UpdateRagSettingsRequest(
            Boolean rewriteEnabled,
            Boolean subQuestionEnabled,
            @Min(1) Integer maxSubQuestions,
            @Min(1) Integer vectorTopK,
            @Min(1) Integer keywordTopK,
            @Min(1) Integer rrfK,
            Boolean rerankEnabled,
            @Min(1) Integer evidenceLimit,
            @Min(1) Integer perQuestionEvidenceCharLimit,
            @Min(1) Integer totalEvidenceCharLimit,
            @DecimalMin("0.0") Double minRelevanceScore
    ) {
    }

    public record RerankSettingsItem(
            boolean enabled,
            String provider,
            String baseUrl,
            String model,
            boolean apiKeySet
    ) {
    }

    public record UpdateRerankSettingsRequest(
            Boolean enabled,
            @Size(max = 64) String provider,
            @Size(max = 500) String baseUrl,
            @Size(max = 128) String model,
            @Size(max = 512) String apiKey
    ) {
    }

    public record UpdateResponse(boolean updated) {
    }

    public record SecretUpdateResponse(boolean updated, boolean apiKeySet) {
    }
}
