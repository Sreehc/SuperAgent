package com.superagent.agent.web;

import com.superagent.agent.domain.AdminAgentCheckpoint;
import com.superagent.agent.domain.AdminAgentRunDetail;
import com.superagent.agent.domain.AdminAgentRunSummary;
import com.superagent.agent.domain.AdminAgentRunStep;
import com.superagent.agent.domain.AdminPluginItem;
import com.superagent.agent.domain.AdminToolCallDetail;
import com.superagent.agent.repository.AgentAdminRepository;
import com.superagent.agent.service.AgentAdminService;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin")
public class AgentAdminController {

    private final AgentAdminService agentAdminService;

    public AgentAdminController(AgentAdminService agentAdminService) {
        this.agentAdminService = agentAdminService;
    }

    @GetMapping("/agent-runs")
    public ApiResponse<PagedResponse<AdminAgentRunSummary>> listRuns(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status
    ) {
        ConversationService.PagedResult<AdminAgentRunSummary> result = agentAdminService.listRuns(page, pageSize, status);
        return ApiResponse.success(new PagedResponse<>(result.items(), result.page(), result.pageSize(), result.total()));
    }

    @GetMapping("/agent-runs/{runId}")
    public ApiResponse<AdminAgentRunSummary> getRun(@PathVariable long runId) {
        return ApiResponse.success(agentAdminService.getRun(runId));
    }

    @GetMapping("/agent-runs/{runId}/detail")
    public ApiResponse<AdminAgentRunDetail> getRunDetail(@PathVariable long runId) {
        return ApiResponse.success(agentAdminService.getRunDetail(runId));
    }

    @GetMapping("/agent-runs/{runId}/steps")
    public ApiResponse<List<AdminAgentRunStep>> listSteps(@PathVariable long runId) {
        return ApiResponse.success(agentAdminService.listSteps(runId));
    }

    @GetMapping("/agent-runs/by-exchange/{exchangeId}")
    public ApiResponse<AdminAgentRunSummary> getRunByExchangeId(@PathVariable long exchangeId) {
        return ApiResponse.success(agentAdminService.getRunByExchangeId(exchangeId));
    }

    @GetMapping("/agent-runs/{runId}/checkpoints")
    public ApiResponse<List<AdminAgentCheckpoint>> listCheckpoints(@PathVariable long runId) {
        return ApiResponse.success(agentAdminService.listCheckpoints(runId));
    }

    @GetMapping("/tool-calls")
    public ApiResponse<List<AdminToolCallDetail>> listToolCalls(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) String toolId
    ) {
        return ApiResponse.success(agentAdminService.listToolCalls(runId, toolId));
    }

    @GetMapping("/plugins")
    public ApiResponse<List<AdminPluginItem>> listPlugins() {
        return ApiResponse.success(agentAdminService.listPlugins());
    }

    @PatchMapping("/plugins/{pluginId}")
    public ApiResponse<PluginPatchResponse> updatePlugin(
            @PathVariable long pluginId,
            @Valid @RequestBody UpdatePluginRequest request
    ) {
        return ApiResponse.success(new PluginPatchResponse(pluginId, agentAdminService.updatePlugin(pluginId, request.enabled())));
    }

    @PutMapping("/tools/{toolId}/secrets/{secretKey}")
    public ApiResponse<AgentAdminService.SecretUpdateResult> updateToolSecret(
            @PathVariable String toolId,
            @PathVariable String secretKey,
            @Valid @RequestBody UpdateToolSecretRequest request
    ) {
        return ApiResponse.success(agentAdminService.updateToolSecret(toolId, secretKey, request.value()));
    }

    @DeleteMapping("/tools/{toolId}/secrets/{secretKey}")
    public ApiResponse<AgentAdminService.SecretUpdateResult> deleteToolSecret(
            @PathVariable String toolId,
            @PathVariable String secretKey
    ) {
        return ApiResponse.success(agentAdminService.deleteToolSecret(toolId, secretKey));
    }

    @GetMapping("/tool-bindings")
    public ApiResponse<List<ToolBindingResponse>> listToolBindings() {
        return ApiResponse.success(agentAdminService.listToolBindings().stream()
                .map(record -> new ToolBindingResponse(
                        record.id(),
                        record.toolId(),
                        record.pluginId(),
                        record.pluginKey(),
                        record.pluginDisplayName(),
                        record.enabled(),
                        record.riskLevel(),
                        record.config(),
                        record.createdAt(),
                        record.updatedAt()
                ))
                .toList());
    }

    @PatchMapping("/tool-bindings/{bindingId}")
    public ApiResponse<ToolBindingResponse> updateToolBinding(
            @PathVariable long bindingId,
            @Valid @RequestBody UpdateToolBindingRequest request
    ) {
        AgentAdminRepository.ToolBindingRecord record = agentAdminService.updateToolBinding(
                bindingId,
                request.enabled(),
                request.riskLevel(),
                request.config()
        );
        return ApiResponse.success(new ToolBindingResponse(
                record.id(),
                record.toolId(),
                record.pluginId(),
                record.pluginKey(),
                record.pluginDisplayName(),
                record.enabled(),
                record.riskLevel(),
                record.config(),
                record.createdAt(),
                record.updatedAt()
        ));
    }

    @PostMapping("/plugins/install")
    public ApiResponse<AdminPluginItem> installPlugin(@Valid @RequestBody InstallPluginRequest request) {
        return ApiResponse.success(agentAdminService.installPlugin(
                request.pluginKey(),
                request.version(),
                request.displayName(),
                request.manifest()
        ));
    }

    @DeleteMapping("/plugins/{pluginId}")
    public ApiResponse<UninstallPluginResponse> uninstallPlugin(@PathVariable long pluginId) {
        return ApiResponse.success(new UninstallPluginResponse(pluginId, agentAdminService.uninstallPlugin(pluginId)));
    }

    public record PagedResponse<T>(List<T> items, int page, int pageSize, long total) {
    }

    public record UpdatePluginRequest(boolean enabled) {
    }

    public record PluginPatchResponse(long pluginId, boolean updated) {
    }

    public record UpdateToolSecretRequest(String value) {
    }

    public record UpdateToolBindingRequest(
            Boolean enabled,
            String riskLevel,
            Map<String, Object> config
    ) {
    }

    public record ToolBindingResponse(
            long id,
            String toolId,
            Long pluginId,
            String pluginKey,
            String pluginDisplayName,
            boolean enabled,
            String riskLevel,
            Map<String, Object> config,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record InstallPluginRequest(
            @NotBlank String pluginKey,
            @NotBlank String version,
            String displayName,
            Map<String, Object> manifest
    ) {
    }

    public record UninstallPluginResponse(long pluginId, boolean uninstalled) {
    }
}
