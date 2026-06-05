package com.superagent.settings.web;

import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.common.api.ApiResponse;
import com.superagent.settings.domain.AgentSettings;
import com.superagent.settings.domain.ModelSettings;
import com.superagent.settings.domain.RagSettings;
import com.superagent.settings.domain.RerankSettings;
import com.superagent.settings.domain.ToolSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
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
                settings.queryUnderstandingEnabled(),
                settings.decompositionEnabled(),
                settings.rewriteEnabled(),
                settings.subQuestionEnabled(),
                settings.versionConsistencyEnabled(),
                settings.neighborExpansionEnabled(),
                settings.maxSubQuestions(),
                settings.vectorTopK(),
                settings.keywordTopK(),
                settings.candidateTopK(),
                settings.rrfK(),
                settings.rerankEnabled(),
                settings.neighborWindow(),
                settings.maxChunksPerDocument(),
                settings.evidenceLimit(),
                settings.perQuestionEvidenceCharLimit(),
                settings.totalEvidenceCharLimit(),
                settings.minRelevanceScore(),
                settings.answerConfidenceThreshold(),
                settings.queryResultCacheEnabled(),
                settings.queryResultCacheTtlSeconds(),
                settings.noEvidenceMinResults(),
                settings.forceCitationEnabled()
        ));
    }

    @PatchMapping("/rag")
    public ApiResponse<UpdateResponse> updateRagSettings(@Valid @RequestBody UpdateRagSettingsRequest request) {
        RuntimeSettingsService.UpdateResult result = runtimeSettingsService.updateRagSettings(new RuntimeSettingsService.RagSettingsPatch(
                request.queryUnderstandingEnabled(),
                request.decompositionEnabled(),
                request.rewriteEnabled(),
                request.subQuestionEnabled(),
                request.versionConsistencyEnabled(),
                request.neighborExpansionEnabled(),
                request.maxSubQuestions(),
                request.vectorTopK(),
                request.keywordTopK(),
                request.candidateTopK(),
                request.rrfK(),
                request.rerankEnabled(),
                request.neighborWindow(),
                request.maxChunksPerDocument(),
                request.evidenceLimit(),
                request.perQuestionEvidenceCharLimit(),
                request.totalEvidenceCharLimit(),
                request.minRelevanceScore(),
                request.answerConfidenceThreshold(),
                request.queryResultCacheEnabled(),
                request.queryResultCacheTtlSeconds(),
                request.noEvidenceMinResults(),
                request.forceCitationEnabled()
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

    @GetMapping("/agent")
    public ApiResponse<AgentSettingsItem> getAgentSettings() {
        AgentSettings settings = runtimeSettingsService.getAgentSettings();
        return ApiResponse.success(new AgentSettingsItem(
                settings.enabled(),
                settings.maxModelSteps(),
                settings.maxToolCalls(),
                settings.checkpointEnabled(),
                settings.defaultMemoryStrategy().name(),
                settings.webSearchEnabled(),
                settings.httpToolEnabled(),
                settings.graphToolEnabled(),
                settings.codeExecutionEnabled(),
                settings.toolTimeoutMs(),
                settings.allowedHttpDomains()
        ));
    }

    @PatchMapping("/agent")
    public ApiResponse<UpdateResponse> updateAgentSettings(@Valid @RequestBody UpdateAgentSettingsRequest request) {
        RuntimeSettingsService.UpdateResult result = runtimeSettingsService.updateAgentSettings(new RuntimeSettingsService.AgentSettingsPatch(
                request.enabled(),
                request.maxModelSteps(),
                request.maxToolCalls(),
                request.checkpointEnabled(),
                request.defaultMemoryStrategy(),
                request.webSearchEnabled(),
                request.httpToolEnabled(),
                request.graphToolEnabled(),
                request.codeExecutionEnabled(),
                request.toolTimeoutMs(),
                request.allowedHttpDomains()
        ));
        return ApiResponse.success(new UpdateResponse(result.updated()));
    }

    @GetMapping("/tools")
    public ApiResponse<ToolSettingsItem> getToolSettings() {
        ToolSettings settings = runtimeSettingsService.getToolSettings();
        return ApiResponse.success(new ToolSettingsItem(
                settings.webSearchEnabled(),
                settings.httpToolEnabled(),
                settings.graphToolEnabled(),
                settings.codeExecutionEnabled(),
                settings.toolTimeoutMs(),
                settings.searchProvider(),
                settings.allowedHttpDomains()
        ));
    }

    @PatchMapping("/tools")
    public ApiResponse<UpdateResponse> updateToolSettings(@Valid @RequestBody UpdateToolSettingsRequest request) {
        RuntimeSettingsService.UpdateResult result = runtimeSettingsService.updateToolSettings(new RuntimeSettingsService.ToolSettingsPatch(
                request.webSearchEnabled(),
                request.httpToolEnabled(),
                request.graphToolEnabled(),
                request.codeExecutionEnabled(),
                request.toolTimeoutMs(),
                request.searchProvider(),
                request.allowedHttpDomains()
        ));
        return ApiResponse.success(new UpdateResponse(result.updated()));
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
            boolean queryUnderstandingEnabled,
            boolean decompositionEnabled,
            boolean rewriteEnabled,
            boolean subQuestionEnabled,
            boolean versionConsistencyEnabled,
            boolean neighborExpansionEnabled,
            int maxSubQuestions,
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
            int noEvidenceMinResults,
            boolean forceCitationEnabled
    ) {
    }

    public record UpdateRagSettingsRequest(
            Boolean queryUnderstandingEnabled,
            Boolean decompositionEnabled,
            Boolean rewriteEnabled,
            Boolean subQuestionEnabled,
            Boolean versionConsistencyEnabled,
            Boolean neighborExpansionEnabled,
            @Min(1) Integer maxSubQuestions,
            @Min(1) Integer vectorTopK,
            @Min(1) Integer keywordTopK,
            @Min(1) Integer candidateTopK,
            @Min(1) Integer rrfK,
            Boolean rerankEnabled,
            @Min(0) Integer neighborWindow,
            @Min(1) Integer maxChunksPerDocument,
            @Min(1) Integer evidenceLimit,
            @Min(1) Integer perQuestionEvidenceCharLimit,
            @Min(1) Integer totalEvidenceCharLimit,
            @DecimalMin("0.0") Double minRelevanceScore,
            @DecimalMin("0.0") Double answerConfidenceThreshold,
            Boolean queryResultCacheEnabled,
            @Min(1) Long queryResultCacheTtlSeconds,
            @Min(1) Integer noEvidenceMinResults,
            Boolean forceCitationEnabled
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

    public record AgentSettingsItem(
            boolean enabled,
            int maxModelSteps,
            int maxToolCalls,
            boolean checkpointEnabled,
            String defaultMemoryStrategy,
            boolean webSearchEnabled,
            boolean httpToolEnabled,
            boolean graphToolEnabled,
            boolean codeExecutionEnabled,
            int toolTimeoutMs,
            List<String> allowedHttpDomains
    ) {
    }

    public record UpdateAgentSettingsRequest(
            Boolean enabled,
            @Min(1) Integer maxModelSteps,
            @Min(1) Integer maxToolCalls,
            Boolean checkpointEnabled,
            MemoryStrategy defaultMemoryStrategy,
            Boolean webSearchEnabled,
            Boolean httpToolEnabled,
            Boolean graphToolEnabled,
            Boolean codeExecutionEnabled,
            @Min(100) Integer toolTimeoutMs,
            List<String> allowedHttpDomains
    ) {
    }

    public record ToolSettingsItem(
            boolean webSearchEnabled,
            boolean httpToolEnabled,
            boolean graphToolEnabled,
            boolean codeExecutionEnabled,
            int toolTimeoutMs,
            String searchProvider,
            List<String> allowedHttpDomains
    ) {
    }

    public record UpdateToolSettingsRequest(
            Boolean webSearchEnabled,
            Boolean httpToolEnabled,
            Boolean graphToolEnabled,
            Boolean codeExecutionEnabled,
            @Min(100) Integer toolTimeoutMs,
            @Size(max = 64) String searchProvider,
            List<String> allowedHttpDomains
    ) {
    }

    public record UpdateResponse(boolean updated) {
    }

    public record SecretUpdateResponse(boolean updated, boolean apiKeySet) {
    }
}
