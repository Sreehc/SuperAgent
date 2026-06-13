package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AgentRunExecutionService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentRunRepository agentRunRepository;
    private final AgentRunStreamRegistry streamRegistry;
    private final ToolRegistryService toolRegistryService;
    private final ToolExecutionService toolExecutionService;
    private final TenantRuntimeSettingsService runtimeSettingsService;
    private final AgentAnswerComposer answerComposer;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    @Autowired
    public AgentRunExecutionService(
            AgentRunRepository agentRunRepository,
            AgentRunStreamRegistry streamRegistry,
            ToolRegistryService toolRegistryService,
            ToolExecutionService toolExecutionService,
            TenantRuntimeSettingsService runtimeSettingsService,
            AgentAnswerComposer answerComposer,
            ObjectMapper objectMapper
    ) {
        this(
                agentRunRepository,
                streamRegistry,
                toolRegistryService,
                toolExecutionService,
                runtimeSettingsService,
                answerComposer,
                objectMapper,
                new SimpleAsyncTaskExecutor("agent-run-")
        );
    }

    AgentRunExecutionService(
            AgentRunRepository agentRunRepository,
            AgentRunStreamRegistry streamRegistry,
            ToolRegistryService toolRegistryService,
            ToolExecutionService toolExecutionService,
            TenantRuntimeSettingsService runtimeSettingsService,
            AgentAnswerComposer answerComposer,
            ObjectMapper objectMapper,
            Executor executor
    ) {
        this.agentRunRepository = agentRunRepository;
        this.streamRegistry = streamRegistry;
        this.toolRegistryService = toolRegistryService;
        this.toolExecutionService = toolExecutionService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.answerComposer = answerComposer;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public long createAndStart(InternalAgentRunRequest request) {
        long runId = agentRunRepository.createRun(
                request.tenantId(),
                request.sessionId(),
                request.exchangeId(),
                request.triggerMessageId(),
                request.question(),
                request.memoryStrategy(),
                writeJson(buildRunMetadata(request))
        );
        streamRegistry.ensureRun(runId);
        executor.execute(() -> execute(runId, request, false));
        return runId;
    }

    public boolean resume(long runId) {
        return agentRunRepository.findRun(runId)
                .filter(run -> !isTerminalStatus(run.status()))
                .map(run -> {
                    InternalAgentRunRequest request = restoreRequest(run);
                    streamRegistry.ensureRun(runId);
                    executor.execute(() -> execute(runId, request, true));
                    return true;
                })
                .orElse(false);
    }

    public boolean cancel(long runId) {
        streamRegistry.cancel(runId);
        return agentRunRepository.findRun(runId)
                .map(run -> {
                    finishCancelled(new ExecutionContext(runId, run.tenantId(), restoreRequest(run)));
                    return true;
                })
                .orElse(false);
    }

    void execute(long runId, InternalAgentRunRequest request, boolean resumed) {
        TenantRuntimeSettingsService.ExecutionPolicy executionPolicy = runtimeSettingsService.resolveExecutionPolicy(request.tenantId());
        if (!executionPolicy.enabled()) {
            failRun(new ExecutionContext(runId, request.tenantId(), request), "Agent execution is disabled");
            return;
        }

        ExecutionContext context = resumed
                ? restoreContext(runId, request)
                : new ExecutionContext(runId, request.tenantId(), request);
        try {
            agentRunRepository.markRunRunning(request.tenantId(), runId);
            Map<String, ToolSpec> enabledTools = toolRegistryService.listEnabledTools(request.tenantId());

            if (resumed) {
                streamRegistry.emit(runId, "resume", Map.of(
                        "runId", runId,
                        "status", "resumed",
                        "checkpointNo", context.checkpointNo
                ));
            }

            while (true) {
                if (streamRegistry.isCancelled(runId)) {
                    finishCancelled(context);
                    return;
                }

                switch (context.phase) {
                    case PLAN -> handlePlan(context, executionPolicy);
                    case SELECT_TOOL -> handleSelectTool(context, enabledTools, executionPolicy);
                    case EXECUTE_TOOL -> handleExecuteTool(context, enabledTools, executionPolicy);
                    case OBSERVE -> handleObserve(context, executionPolicy);
                    case DECIDE_NEXT -> handleDecideNext(context, executionPolicy);
                    case COMPLETE -> {
                        completeRun(context);
                        return;
                    }
                    case FAILED -> {
                        failRun(context, context.errorMessage);
                        return;
                    }
                    case CANCELLED -> {
                        finishCancelled(context);
                        return;
                    }
                }
            }
        } catch (Exception exception) {
            failRun(context, exception.getMessage());
        } finally {
            streamRegistry.complete(runId);
        }
    }

    private void handlePlan(ExecutionContext context, TenantRuntimeSettingsService.ExecutionPolicy policy) {
        if (!incrementModelSteps(context, policy, AgentPhase.PLAN, "达到最大模型步数限制，已提前结束。")) {
            return;
        }
        long stepId = persistStep(
                context,
                1,
                AgentPhase.PLAN,
                "success",
                context.resumed ? "resume_from_checkpoint" : "route_to_agent_service",
                null,
                null,
                null,
                null,
                Map.of("question", context.request.question())
        );
        emitAgentStep(context, 1, AgentPhase.PLAN, "success", context.resumed ? "从最近 checkpoint 恢复执行" : "Agent 已完成初始规划");
        saveCheckpoint(context, stepId, "model_decision", Map.of("question", context.request.question()), AgentPhase.SELECT_TOOL);
        context.phase = AgentPhase.SELECT_TOOL;
    }

    private void handleSelectTool(
            ExecutionContext context,
            Map<String, ToolSpec> enabledTools,
            TenantRuntimeSettingsService.ExecutionPolicy policy
    ) {
        if (!incrementModelSteps(context, policy, AgentPhase.SELECT_TOOL, "达到最大模型步数限制，已提前结束。")) {
            return;
        }
        context.selectedToolId = selectTool(context.request);
        context.selectedToolReason = describeToolReason(context.request, context.selectedToolId, enabledTools.containsKey(context.selectedToolId));

        long stepId = persistStep(
                context,
                2,
                AgentPhase.SELECT_TOOL,
                "success",
                "select_" + context.selectedToolId,
                null,
                context.selectedToolId,
                context.selectedToolReason,
                null,
                Map.of("enabledToolCount", enabledTools.size())
        );
        emitAgentStep(context, 2, AgentPhase.SELECT_TOOL, "success", "选择工具 " + context.selectedToolId);
        saveCheckpoint(
                context,
                stepId,
                "tool_selected",
                Map.of("selectedToolId", context.selectedToolId, "selectedToolReason", context.selectedToolReason),
                AgentPhase.EXECUTE_TOOL
        );
        context.phase = AgentPhase.EXECUTE_TOOL;
    }

    private void handleExecuteTool(
            ExecutionContext context,
            Map<String, ToolSpec> enabledTools,
            TenantRuntimeSettingsService.ExecutionPolicy policy
    ) {
        if (context.toolCalls >= policy.maxToolCalls()) {
            completeWithGuardrail(context, AgentPhase.EXECUTE_TOOL, "达到最大工具调用次数限制，已返回部分结果。");
            return;
        }

        ToolSpec toolSpec = enabledTools.get(context.selectedToolId);
        context.toolInput = context.toolInput == null || context.toolInput.isEmpty()
                ? buildToolInput(context.request, context.selectedToolId)
                : context.toolInput;

        long stepId = persistStep(
                context,
                3,
                AgentPhase.EXECUTE_TOOL,
                "running",
                "execute_" + context.selectedToolId,
                null,
                context.selectedToolId,
                context.selectedToolReason,
                null,
                Map.of("input", context.toolInput)
        );
        context.currentStepId = stepId;
        context.currentToolCallId = context.currentToolCallId != null
                ? context.currentToolCallId
                : agentRunRepository.findLatestToolCallId(context.tenantId, context.runId, stepId)
                        .orElseGet(() -> agentRunRepository.createToolCall(
                                context.tenantId,
                                context.runId,
                                stepId,
                                context.selectedToolId,
                                toolSpec == null ? null : toolSpec.pluginId(),
                                context.request.question()
                        ));

        streamRegistry.emit(context.runId, "tool_start", Map.of(
                "runId", context.runId,
                "toolId", context.selectedToolId,
                "stepNo", 3,
                "summary", "开始执行 " + context.selectedToolId
        ));
        saveCheckpoint(
                context,
                stepId,
                "tool_call_started",
                Map.of("toolId", context.selectedToolId, "toolCallId", context.currentToolCallId, "input", context.toolInput),
                AgentPhase.EXECUTE_TOOL
        );

        ToolResult toolResult = executeToolSafely(toolSpec, context);
        context.toolCalls += 1;
        context.toolResult = toolResult;
        agentRunRepository.completeToolCall(
                context.tenantId,
                context.currentToolCallId,
                toolResult.summary(),
                120,
                toolResult.status(),
                toolResult.errorMessage(),
                writeJson(buildToolMetadata(toolSpec, context.toolInput, toolResult))
        );
        persistStep(
                context,
                3,
                AgentPhase.EXECUTE_TOOL,
                "success".equalsIgnoreCase(toolResult.status()) ? "success" : "failed",
                "execute_" + context.selectedToolId,
                null,
                context.selectedToolId,
                context.selectedToolReason,
                toolResult.errorMessage(),
                Map.of("input", context.toolInput, "output", toolResult.output())
        );
        streamRegistry.emit(context.runId, "tool_result", Map.of(
                "runId", context.runId,
                "toolId", context.selectedToolId,
                "status", toolResult.status(),
                "summary", toolResult.summary(),
                "output", toolResult.output()
        ));
        saveCheckpoint(
                context,
                stepId,
                "tool_call_finished",
                checkpointPayload(
                        "toolId", context.selectedToolId,
                        "toolCallId", context.currentToolCallId,
                        "toolStatus", toolResult.status(),
                        "toolSummary", toolResult.summary(),
                        "toolOutput", toolResult.output(),
                        "toolError", toolResult.errorMessage()
                ),
                AgentPhase.OBSERVE
        );
        context.phase = AgentPhase.OBSERVE;
    }

    private void handleObserve(ExecutionContext context, TenantRuntimeSettingsService.ExecutionPolicy policy) {
        String observationSummary = summarizeObservation(context.toolResult);
        context.observationSummary = observationSummary;
        String stepStatus = context.toolResult == null || "success".equalsIgnoreCase(context.toolResult.status()) ? "success" : "failed";
        long stepId = persistStep(
                context,
                4,
                AgentPhase.OBSERVE,
                stepStatus,
                "observe_" + context.selectedToolId,
                observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                context.toolResult == null ? null : context.toolResult.errorMessage(),
                Map.of("toolStatus", context.toolResult == null ? "missing" : context.toolResult.status())
        );
        emitAgentStep(context, 4, AgentPhase.OBSERVE, stepStatus, observationSummary);
        saveCheckpoint(
                context,
                stepId,
                "observation_ready",
                Map.of("observationSummary", observationSummary),
                AgentPhase.DECIDE_NEXT
        );
        context.phase = AgentPhase.DECIDE_NEXT;
    }

    private void handleDecideNext(ExecutionContext context, TenantRuntimeSettingsService.ExecutionPolicy policy) {
        if (!incrementModelSteps(context, policy, AgentPhase.DECIDE_NEXT, "达到最大模型步数限制，已返回部分结果。")) {
            return;
        }
        String answer = buildDecisionAnswer(context);
        context.answer = answer;
        long stepId = persistStep(
                context,
                5,
                AgentPhase.DECIDE_NEXT,
                "success",
                "finish_with_answer",
                context.observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                null,
                Map.of("toolStatus", context.toolResult == null ? "missing" : context.toolResult.status())
        );
        emitAgentStep(context, 5, AgentPhase.DECIDE_NEXT, "success", "已根据工具观察结果生成最终回答");
        saveCheckpoint(
                context,
                stepId,
                "answer_ready",
                Map.of("answer", answer, "observationSummary", context.observationSummary),
                AgentPhase.COMPLETE
        );
        context.phase = AgentPhase.COMPLETE;
    }

    private void completeRun(ExecutionContext context) {
        persistStep(
                context,
                6,
                AgentPhase.COMPLETE,
                "success",
                "stream_final_answer",
                context.observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                null,
                Map.of("answerLength", context.answer == null ? 0 : context.answer.length())
        );
        String answer = context.answer == null ? "我已完成当前 Agent 工具执行。" : context.answer;
        for (String chunk : slice(answer, 18)) {
            if (streamRegistry.isCancelled(context.runId)) {
                context.phase = AgentPhase.CANCELLED;
                finishCancelled(context);
                return;
            }
            streamRegistry.emit(context.runId, "delta", Map.of("text", chunk));
            sleep(30L);
        }
        streamRegistry.emit(context.runId, "recommendation", Map.of("questions", buildRecommendations(context)));
        agentRunRepository.updateRunProgress(context.tenantId, context.runId, context.modelSteps, context.toolCalls, "success", null);
        streamRegistry.emit(context.runId, "done", Map.of("runId", context.runId, "status", "success", "stopped", false));
    }

    private void finishCancelled(ExecutionContext context) {
        persistStep(
                context,
                6,
                AgentPhase.CANCELLED,
                "cancelled",
                "cancelled_by_request",
                context.observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                "Cancelled by request",
                Map.of()
        );
        agentRunRepository.updateRunProgress(context.tenantId, context.runId, context.modelSteps, context.toolCalls, "cancelled", "Cancelled by request");
        streamRegistry.emit(context.runId, "done", Map.of("runId", context.runId, "status", "cancelled", "stopped", true));
    }

    private void failRun(ExecutionContext context, String message) {
        context.errorMessage = message == null || message.isBlank() ? "Agent run failed" : message;
        persistStep(
                context,
                6,
                AgentPhase.FAILED,
                "failed",
                "failed_with_error",
                context.observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                context.errorMessage,
                Map.of()
        );
        agentRunRepository.updateRunProgress(context.tenantId, context.runId, context.modelSteps, context.toolCalls, "failed", context.errorMessage);
        streamRegistry.emit(context.runId, "error", Map.of("code", "AGENT_EXECUTION_FAILED", "message", context.errorMessage, "runId", context.runId));
    }

    private ExecutionContext restoreContext(long runId, InternalAgentRunRequest request) {
        ExecutionContext context = new ExecutionContext(runId, request.tenantId(), request);
        agentRunRepository.findLatestStableCheckpoint(request.tenantId(), runId).ifPresent(checkpoint -> {
            Map<String, Object> payload = parseJsonMap(checkpoint.payloadJson());
            context.phase = readPhase(payload, checkpoint.checkpointType());
            context.checkpointNo = checkpoint.checkpointNo();
            context.modelSteps = intValue(payload.get("modelStepCount"), context.modelSteps);
            context.toolCalls = intValue(payload.get("toolCallCount"), context.toolCalls);
            context.selectedToolId = stringValue(payload.get("selectedToolId"), context.selectedToolId);
            context.selectedToolReason = stringValue(payload.get("selectedToolReason"), context.selectedToolReason);
            context.observationSummary = stringValue(payload.get("observationSummary"), context.observationSummary);
            context.answer = stringValue(payload.get("answer"), context.answer);
            context.currentToolCallId = longValue(payload.get("toolCallId"), context.currentToolCallId);
            context.toolInput = mapValue(payload.get("toolInput"), mapValue(payload.get("input"), Map.of()));
            if (payload.containsKey("toolStatus")) {
                context.toolResult = new ToolResult(
                        context.selectedToolId == null ? "" : context.selectedToolId,
                        stringValue(payload.get("toolStatus"), "failed"),
                        stringValue(payload.get("toolSummary"), ""),
                        mapValue(payload.get("toolOutput"), Map.of()),
                        stringValue(payload.get("toolError"), null)
                );
            }
            context.resumed = true;
        });
        return context;
    }

    private InternalAgentRunRequest restoreRequest(AgentRunRepository.RunRecord run) {
        Map<String, Object> metadata = parseJsonMap(run.metadataJson());
        return new InternalAgentRunRequest(
                run.tenantId(),
                run.sessionId(),
                run.exchangeId() == null ? 0L : run.exchangeId(),
                run.triggerMessageId() == null ? 0L : run.triggerMessageId(),
                longValue(metadata.get("actorUserId"), 0L),
                stringValue(metadata.get("actorRole"), "MEMBER"),
                run.question(),
                longValue(metadata.get("knowledgeBaseId"), null),
                run.memoryStrategy(),
                stringListValue(metadata.get("recentMessages"))
        );
    }

    private long persistStep(
            ExecutionContext context,
            int stepNo,
            AgentPhase phase,
            String status,
            String decisionSummary,
            String observationSummary,
            String selectedToolId,
            String selectedToolReason,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        return agentRunRepository.createStep(
                context.tenantId,
                context.runId,
                stepNo,
                phase.name(),
                status,
                decisionSummary,
                observationSummary,
                selectedToolId,
                selectedToolReason,
                errorMessage,
                writeJson(metadata)
        );
    }

    private void saveCheckpoint(
            ExecutionContext context,
            long stepId,
            String checkpointType,
            Map<String, Object> payload,
            AgentPhase nextPhase
    ) {
        if (!runtimeSettingsService.resolveExecutionPolicy(context.tenantId).checkpointEnabled()) {
            return;
        }
        context.checkpointNo += 1;
        Map<String, Object> checkpointPayload = new LinkedHashMap<>();
        checkpointPayload.put("nextPhase", nextPhase.name());
        checkpointPayload.put("checkpointType", checkpointType);
        checkpointPayload.put("stepNo", stepId);
        checkpointPayload.put("modelStepCount", context.modelSteps);
        checkpointPayload.put("toolCallCount", context.toolCalls);
        checkpointPayload.put("selectedToolId", context.selectedToolId);
        checkpointPayload.put("selectedToolReason", context.selectedToolReason);
        checkpointPayload.put("toolCallId", context.currentToolCallId);
        checkpointPayload.put("toolInput", context.toolInput);
        checkpointPayload.put("observationSummary", context.observationSummary);
        checkpointPayload.put("answer", context.answer);
        checkpointPayload.putAll(payload);
        agentRunRepository.saveCheckpoint(
                context.tenantId,
                context.runId,
                context.checkpointNo,
                stepId,
                checkpointType,
                writeJson(checkpointPayload)
        );
        streamRegistry.emit(context.runId, "checkpoint", Map.of(
                "runId", context.runId,
                "checkpointNo", context.checkpointNo,
                "phase", nextPhase.name(),
                "stable", true
        ));
    }

    private boolean incrementModelSteps(
            ExecutionContext context,
            TenantRuntimeSettingsService.ExecutionPolicy policy,
            AgentPhase phase,
            String limitMessage
    ) {
        if (context.modelSteps >= policy.maxModelSteps()) {
            completeWithGuardrail(context, phase, limitMessage);
            return false;
        }
        context.modelSteps += 1;
        return true;
    }

    private void completeWithGuardrail(ExecutionContext context, AgentPhase phase, String message) {
        context.answer = message;
        context.errorMessage = message;
        persistStep(
                context,
                5,
                phase,
                "failed",
                "guardrail_triggered",
                context.observationSummary,
                context.selectedToolId,
                context.selectedToolReason,
                message,
                Map.of("guardrail", phase.name())
        );
        context.phase = AgentPhase.COMPLETE;
    }

    private ToolResult executeToolSafely(ToolSpec toolSpec, ExecutionContext context) {
        if (toolSpec == null) {
            return new ToolResult(
                    context.selectedToolId == null ? "unknown" : context.selectedToolId,
                    "failed",
                    "工具未对当前租户启用",
                    Map.of(),
                    "tool_not_enabled"
            );
        }
        try {
            return toolExecutionService.execute(toolSpec, new ToolInvocation(
                    context.tenantId,
                    context.runId,
                    context.currentStepId,
                    context.request.actorRole(),
                    context.selectedToolId,
                    context.toolInput
            ));
        } catch (Exception exception) {
            return new ToolResult(
                    context.selectedToolId,
                    "failed",
                    "工具执行异常",
                    checkpointPayload("error", stringValue(exception.getMessage(), "tool_execution_exception")),
                    exception.getMessage()
            );
        }
    }

    private void emitAgentStep(ExecutionContext context, int stepNo, AgentPhase phase, String status, String summary) {
        streamRegistry.emit(context.runId, "agent_step", Map.of(
                "runId", context.runId,
                "stepNo", stepNo,
                "phase", phase.name(),
                "status", status,
                "summary", summary
        ));
    }

    private Map<String, Object> buildRunMetadata(InternalAgentRunRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actorUserId", request.actorUserId());
        metadata.put("actorRole", stringValue(request.actorRole(), "MEMBER"));
        metadata.put("knowledgeBaseId", request.knowledgeBaseId());
        metadata.put("recentMessages", request.recentMessages());
        return metadata;
    }

    private AgentPhase readPhase(Map<String, Object> payload, String checkpointType) {
        String explicit = stringValue(payload.get("nextPhase"), null);
        if (explicit != null && !explicit.isBlank()) {
            return AgentPhase.valueOf(explicit);
        }
        return switch (checkpointType) {
            case "model_decision" -> AgentPhase.SELECT_TOOL;
            case "tool_selected", "tool_call_started" -> AgentPhase.EXECUTE_TOOL;
            case "tool_call_finished" -> AgentPhase.OBSERVE;
            case "observation_ready" -> AgentPhase.DECIDE_NEXT;
            case "answer_ready" -> AgentPhase.COMPLETE;
            default -> AgentPhase.PLAN;
        };
    }

    private String summarizeObservation(ToolResult toolResult) {
        if (toolResult == null) {
            return "工具观察缺失。";
        }
        if ("success".equalsIgnoreCase(toolResult.status())) {
            return "已完成工具观察：" + toolResult.summary();
        }
        return "工具执行失败：" + toolResult.summary();
    }

    private String buildDecisionAnswer(ExecutionContext context) {
        if (context.toolResult == null) {
            return "当前 Agent 缺少可用工具观察，已提前结束。";
        }
        if (!"success".equalsIgnoreCase(context.toolResult.status())) {
            return "工具执行失败，但已返回结构化错误信息：" + context.toolResult.summary();
        }
        return answerComposer.compose(context.request, context.selectedToolId, context.toolResult);
    }

    private Map<String, Object> buildToolMetadata(ToolSpec toolSpec, Map<String, Object> toolInput, ToolResult toolResult) {
        return Map.of(
                "pluginKey", toolSpec == null || toolSpec.pluginKey() == null ? "" : toolSpec.pluginKey(),
                "pluginVersion", toolSpec == null || toolSpec.pluginVersion() == null ? "" : toolSpec.pluginVersion(),
                "riskLevel", toolSpec == null ? "standard" : toolSpec.riskLevel(),
                "input", toolInput,
                "output", toolResult.output()
        );
    }

    private List<String> buildRecommendations(ExecutionContext context) {
        List<String> recommendations = new ArrayList<>();
        recommendations.add("继续追问这个主题");
        if (context.request.knowledgeBaseId() != null) {
            recommendations.add("切换到知识库验证细节");
        } else {
            recommendations.add("换一个实时问题继续搜索");
        }
        return recommendations;
    }

    private Map<String, Object> buildToolInput(InternalAgentRunRequest request, String toolId) {
        if ("python.sandbox".equals(toolId)) {
            return Map.of("code", "print('sandbox execution ok')", "question", request.question());
        }
        if ("http.request".equals(toolId)) {
            return Map.of("url", "https://example.com", "method", "GET", "question", request.question());
        }
        if ("graph.query".equals(toolId)) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("question", request.question());
            input.put("limit", 5);
            if (request.knowledgeBaseId() != null) {
                input.put("knowledgeBaseId", request.knowledgeBaseId());
            }
            return input;
        }
        if ("web.fetch".equals(toolId)) {
            return Map.of("url", "https://example.com/a", "question", request.question());
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question", request.question());
        if (request.knowledgeBaseId() != null) {
            input.put("knowledgeBaseId", request.knowledgeBaseId());
        }
        return input;
    }

    private String describeToolReason(InternalAgentRunRequest request, String toolId, boolean enabled) {
        if (!enabled) {
            return "tool_not_enabled_for_tenant";
        }
        if ("knowledge.search".equals(toolId)) {
            return "knowledge_base_selected";
        }
        if ("graph.query".equals(toolId)) {
            return "graph_or_entity_question";
        }
        if ("http.request".equals(toolId)) {
            return "http_or_api_request_detected";
        }
        if ("python.sandbox".equals(toolId)) {
            return "code_or_script_request_detected";
        }
        return request.knowledgeBaseId() == null ? "open_ended_or_realtime_request" : "fallback_tool_selection";
    }

    private String selectTool(InternalAgentRunRequest request) {
        String question = request.question() == null ? "" : request.question().toLowerCase();
        if (question.contains("python") || question.contains("代码") || question.contains("脚本")) {
            return "python.sandbox";
        }
        if (question.contains("http") || question.contains("接口") || question.contains("请求")) {
            return "http.request";
        }
        if (question.contains("图谱") || question.contains("实体") || question.contains("关系") || question.contains("关联")) {
            return "graph.query";
        }
        if (request.knowledgeBaseId() != null) {
            return "knowledge.search";
        }
        return "web.search";
    }

    private List<String> slice(String text, int chunkSize) {
        ArrayList<String> chunks = new ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }

    private Map<String, Object> checkpointPayload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length - 1; index += 2) {
            Object key = keyValues[index];
            Object value = keyValues[index + 1];
            if (key != null && value != null) {
                payload.put(String.valueOf(key), value);
            }
        }
        return payload;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTerminalStatus(String status) {
        return "success".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize agent payload", exception);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? defaultValue : string;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Long longValue(Object value, Long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value, Map<String, Object> defaultValue) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return defaultValue;
    }

    private List<String> stringListValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    enum AgentPhase {
        PLAN,
        SELECT_TOOL,
        EXECUTE_TOOL,
        OBSERVE,
        DECIDE_NEXT,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    static final class ExecutionContext {
        private final long runId;
        private final long tenantId;
        private final InternalAgentRunRequest request;
        private AgentPhase phase = AgentPhase.PLAN;
        private int checkpointNo = 0;
        private int modelSteps = 0;
        private int toolCalls = 0;
        private boolean resumed = false;
        private String selectedToolId;
        private String selectedToolReason;
        private String observationSummary;
        private String answer;
        private String errorMessage;
        private Map<String, Object> toolInput = Map.of();
        private ToolResult toolResult;
        private Long currentStepId;
        private Long currentToolCallId;

        private ExecutionContext(long runId, long tenantId, InternalAgentRunRequest request) {
            this.runId = runId;
            this.tenantId = tenantId;
            this.request = request;
        }
    }
}
