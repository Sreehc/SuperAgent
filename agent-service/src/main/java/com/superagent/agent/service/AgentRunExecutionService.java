package com.superagent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolInvocation;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AgentRunExecutionService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunStreamRegistry streamRegistry;
    private final ToolRegistryService toolRegistryService;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;
    private final Executor executor = new SimpleAsyncTaskExecutor("agent-run-");

    public AgentRunExecutionService(
            AgentRunRepository agentRunRepository,
            AgentRunStreamRegistry streamRegistry,
            ToolRegistryService toolRegistryService,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper
    ) {
        this.agentRunRepository = agentRunRepository;
        this.streamRegistry = streamRegistry;
        this.toolRegistryService = toolRegistryService;
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
    }

    public long createAndStart(InternalAgentRunRequest request) {
        long runId = agentRunRepository.createRun(
                request.tenantId(),
                request.sessionId(),
                request.exchangeId(),
                request.triggerMessageId(),
                request.question(),
                request.memoryStrategy()
        );
        streamRegistry.ensureRun(runId);
        executor.execute(() -> execute(runId, request, false));
        return runId;
    }

    public boolean resume(long runId) {
        return agentRunRepository.findRun(runId)
                .map(run -> {
                    executor.execute(() -> execute(runId, new InternalAgentRunRequest(
                            run.tenantId(),
                            0L,
                            0L,
                            0L,
                            0L,
                            "resume",
                            null,
                            "SUMMARY_PLUS_WINDOW",
                            List.of()
                    ), true));
                    return true;
                })
                .orElse(false);
    }

    public boolean cancel(long runId) {
        streamRegistry.cancel(runId);
        return agentRunRepository.findRun(runId)
                .map(run -> {
                    agentRunRepository.updateRunProgress(run.tenantId(), runId, 0, 0, "cancelled", "Cancelled by operator");
                    return true;
                })
                .orElse(false);
    }

    private void execute(long runId, InternalAgentRunRequest request, boolean resumed) {
        String toolId = selectTool(request);
        int modelSteps = 0;
        int toolCalls = 0;
        try {
            Map<String, ToolSpec> enabledTools = toolRegistryService.listEnabledTools(request.tenantId());
            if (resumed) {
                streamRegistry.emit(runId, "resume", Map.of("runId", runId, "status", "resumed"));
            }
            long planStepId = agentRunRepository.createStep(request.tenantId(), runId, 1, "PLAN", "success",
                    resumed ? "resume_from_checkpoint" : "route_to_agent_service");
            agentRunRepository.saveCheckpoint(request.tenantId(), runId, 1, planStepId, "plan",
                    objectMapper.writeValueAsString(Map.of("question", request.question(), "resumed", resumed)));
            streamRegistry.emit(runId, "agent_step", Map.of(
                    "runId", runId,
                    "stepNo", 1,
                    "phase", "PLAN",
                    "status", "success",
                    "summary", resumed ? "从最近 checkpoint 恢复执行" : "Agent 已完成初始规划"
            ));
            streamRegistry.emit(runId, "checkpoint", Map.of("runId", runId, "checkpointNo", 1, "phase", "PLAN", "stable", true));

            if (streamRegistry.isCancelled(runId)) {
                finishCancelled(request.tenantId(), runId);
                return;
            }

            long toolStepId = agentRunRepository.createStep(request.tenantId(), runId, 2, "SELECT_TOOL", "success", "select_" + toolId);
            streamRegistry.emit(runId, "agent_step", Map.of(
                    "runId", runId,
                    "stepNo", 2,
                    "phase", "SELECT_TOOL",
                    "status", "success",
                    "summary", "选择工具 " + toolId
            ));
            ToolSpec toolSpec = enabledTools.get(toolId);
            if (toolSpec == null) {
                throw new IllegalStateException("Tool not enabled for tenant: " + toolId);
            }
            Map<String, Object> toolInput = buildToolInput(request, toolId);
            long toolCallId = agentRunRepository.createToolCall(
                    request.tenantId(),
                    runId,
                    toolStepId,
                    toolId,
                    toolSpec.pluginId(),
                    request.question()
            );
            toolCalls++;
            streamRegistry.emit(runId, "tool_start", Map.of(
                    "runId", runId,
                    "toolId", toolId,
                    "stepNo", 2,
                    "summary", "开始执行 " + toolId
            ));

            ToolResult toolResult = toolExecutionService.execute(toolSpec, new ToolInvocation(
                    request.tenantId(),
                    runId,
                    toolStepId,
                    toolId,
                    toolInput
            ));
            agentRunRepository.completeToolCall(
                    request.tenantId(),
                    toolCallId,
                    toolResult.summary(),
                    120,
                    toolResult.status(),
                    toolResult.errorMessage(),
                    objectMapper.writeValueAsString(buildToolMetadata(toolSpec, toolInput, toolResult))
            );
            streamRegistry.emit(runId, "tool_result", Map.of(
                    "runId", runId,
                    "toolId", toolId,
                    "status", toolResult.status(),
                    "summary", toolResult.summary(),
                    "output", toolResult.output()
            ));

            agentRunRepository.saveCheckpoint(request.tenantId(), runId, 2, toolStepId, "tool_result",
                    objectMapper.writeValueAsString(Map.of("toolId", toolId, "summary", toolResult.summary(), "output", toolResult.output())));
            streamRegistry.emit(runId, "checkpoint", Map.of("runId", runId, "checkpointNo", 2, "phase", "EXECUTE_TOOL", "stable", true));

            if (streamRegistry.isCancelled(runId)) {
                finishCancelled(request.tenantId(), runId);
                return;
            }

            long observeStepId = agentRunRepository.createStep(request.tenantId(), runId, 3, "DECIDE_NEXT", "success", "finish_with_answer");
            modelSteps++;
            streamRegistry.emit(runId, "agent_step", Map.of(
                    "runId", runId,
                    "stepNo", 3,
                    "phase", "DECIDE_NEXT",
                    "status", "success",
                    "summary", "已根据工具观察结果生成最终回答"
            ));
            String answer = buildAnswer(toolId, toolResult);
            agentRunRepository.saveCheckpoint(request.tenantId(), runId, 3, observeStepId, "answer_ready",
                    objectMapper.writeValueAsString(Map.of("answerLength", answer.length())));

            for (String chunk : slice(answer, 18)) {
                if (streamRegistry.isCancelled(runId)) {
                    finishCancelled(request.tenantId(), runId);
                    return;
                }
                streamRegistry.emit(runId, "delta", Map.of("text", chunk));
                Thread.sleep(50L);
            }
            streamRegistry.emit(runId, "recommendation", Map.of("questions", List.of("继续追问这个主题", "切换到知识库验证细节")));
            agentRunRepository.updateRunProgress(request.tenantId(), runId, modelSteps, toolCalls, "success", null);
            streamRegistry.emit(runId, "done", Map.of("runId", runId, "status", "success", "stopped", false));
        } catch (Exception exception) {
            agentRunRepository.updateRunProgress(request.tenantId(), runId, modelSteps, toolCalls, "failed", exception.getMessage());
            streamRegistry.emit(runId, "error", Map.of("code", "AGENT_EXECUTION_FAILED", "message", "Agent run failed", "runId", runId));
        } finally {
            streamRegistry.complete(runId);
        }
    }

    private Map<String, Object> buildToolInput(InternalAgentRunRequest request, String toolId) {
        if ("python.sandbox".equals(toolId)) {
            return Map.of("code", "print('sandbox execution ok')", "question", request.question());
        }
        if ("http.request".equals(toolId)) {
            return Map.of("url", "https://example.com", "method", "GET", "question", request.question());
        }
        if ("graph.query".equals(toolId)) {
            Map<String, Object> input = new java.util.LinkedHashMap<>();
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
        Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("question", request.question());
        if (request.knowledgeBaseId() != null) {
            input.put("knowledgeBaseId", request.knowledgeBaseId());
        }
        return input;
    }

    private String buildAnswer(String toolId, ToolResult toolResult) {
        if ("knowledge.search".equals(toolId)) {
            return "我已根据知识库证据完成回答，并保留了后续多工具编排扩展位。";
        }
        if ("web.search".equals(toolId)) {
            return "我已根据联网搜索结果完成回答，并生成了结构化搜索证据摘要。";
        }
        if ("web.fetch".equals(toolId)) {
            return "我已根据网页抓取结果完成回答，并保留了引用上下文。";
        }
        if ("http.request".equals(toolId)) {
            return "我已根据受控 HTTP 请求结果完成回答。";
        }
        if ("graph.query".equals(toolId)) {
            return "我已根据图谱证据完成回答，并提取了实体与关联关系。";
        }
        if ("python.sandbox".equals(toolId)) {
            return "我已根据受控 Python Sandbox 的执行结果完成回答。";
        }
        return "我已完成当前 Agent 工具执行。";
    }

    private Map<String, Object> buildToolMetadata(ToolSpec toolSpec, Map<String, Object> toolInput, ToolResult toolResult) {
        return Map.of(
                "pluginKey", toolSpec.pluginKey() == null ? "" : toolSpec.pluginKey(),
                "pluginVersion", toolSpec.pluginVersion() == null ? "" : toolSpec.pluginVersion(),
                "riskLevel", toolSpec.riskLevel(),
                "input", toolInput,
                "output", toolResult.output()
        );
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

    private void finishCancelled(long tenantId, long runId) {
        agentRunRepository.updateRunProgress(tenantId, runId, 0, 0, "cancelled", "Cancelled by request");
        streamRegistry.emit(runId, "done", Map.of("runId", runId, "status", "cancelled", "stopped", true));
    }

    private List<String> slice(String text, int chunkSize) {
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < text.length(); index += chunkSize) {
            chunks.add(text.substring(index, Math.min(text.length(), index + chunkSize)));
        }
        return chunks;
    }
}
