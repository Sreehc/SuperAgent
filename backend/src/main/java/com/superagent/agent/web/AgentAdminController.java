package com.superagent.agent.web;

import com.superagent.agent.domain.AdminAgentCheckpoint;
import com.superagent.agent.domain.AdminAgentRunDetail;
import com.superagent.agent.domain.AdminAgentRunSummary;
import com.superagent.agent.domain.AdminAgentRunStep;
import com.superagent.agent.domain.AdminPluginItem;
import com.superagent.agent.domain.AdminToolCallDetail;
import com.superagent.agent.service.AgentAdminService;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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

    public record PagedResponse<T>(List<T> items, int page, int pageSize, long total) {
    }

    public record UpdatePluginRequest(boolean enabled) {
    }

    public record PluginPatchResponse(long pluginId, boolean updated) {
    }
}
