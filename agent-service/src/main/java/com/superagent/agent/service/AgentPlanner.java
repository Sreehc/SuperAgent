package com.superagent.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.AgentDecision;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Model-driven Agent planner. Outputs structured AgentDecision per ReAct step.
 *
 * <p>Wiring is a two-layer fallback:
 * <ol>
 *   <li>If a {@link ChatModelClient} bean is available, prompt the model to emit
 *       JSON in the agreed schema and parse it.</li>
 *   <li>Otherwise (or if model output cannot be parsed), fall back to a
 *       deterministic heuristic planner that still emits valid AgentDecision
 *       objects. This keeps local dev and the existing test corpus working.</li>
 * </ol>
 */
@Component
public class AgentPlanner {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final ObjectProvider<ChatModelClient> chatModelClientProvider;
    private final ObjectMapper objectMapper;
    private final boolean modelDecisionsEnabled;

    public AgentPlanner(
            ObjectProvider<ChatModelClient> chatModelClientProvider,
            ObjectMapper objectMapper,
            @Value("${super-agent.agent.model-driven-decisions-enabled:true}") boolean modelDecisionsEnabled
    ) {
        this.chatModelClientProvider = chatModelClientProvider;
        this.objectMapper = objectMapper;
        this.modelDecisionsEnabled = modelDecisionsEnabled;
    }

    public AgentDecision decideInitial(
            InternalAgentRunRequest request,
            Map<String, ToolSpec> enabledTools
    ) {
        ChatModelClient client = resolveClient();
        if (client != null) {
            try {
                String prompt = buildInitialPrompt(request, enabledTools);
                String response = client.generate(prompt);
                AgentDecision decision = parseDecision(response, enabledTools);
                if (decision != null) {
                    return decision;
                }
            } catch (Exception ignored) {
                // fall through to heuristic
            }
        }
        return heuristicInitial(request, enabledTools);
    }

    public AgentDecision decideNext(
            InternalAgentRunRequest request,
            Map<String, ToolSpec> enabledTools,
            String previousToolId,
            ToolResult previousResult,
            int toolCallsSoFar
    ) {
        ChatModelClient client = resolveClient();
        if (client != null) {
            try {
                String prompt = buildContinuationPrompt(request, enabledTools, previousToolId, previousResult, toolCallsSoFar);
                String response = client.generate(prompt);
                AgentDecision decision = parseDecision(response, enabledTools);
                if (decision != null) {
                    return decision;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return heuristicContinuation(request, previousResult);
    }

    private ChatModelClient resolveClient() {
        if (!modelDecisionsEnabled) {
            return null;
        }
        return chatModelClientProvider.getIfAvailable();
    }

    private AgentDecision heuristicInitial(InternalAgentRunRequest request, Map<String, ToolSpec> enabledTools) {
        String question = request.question() == null ? "" : request.question().toLowerCase();
        String selected;
        if (question.contains("python") || question.contains("代码") || question.contains("脚本")) {
            selected = "python.sandbox";
        } else if (question.contains("http") || question.contains("接口") || question.contains("请求")) {
            selected = "http.request";
        } else if (question.contains("图谱") || question.contains("实体") || question.contains("关系") || question.contains("关联")) {
            selected = "graph.query";
        } else if (request.knowledgeBaseId() != null && enabledTools.containsKey("knowledge.search")) {
            selected = "knowledge.search";
        } else {
            selected = "web.search";
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question", request.question());
        if (request.knowledgeBaseId() != null) {
            input.put("knowledgeBaseId", request.knowledgeBaseId());
        }
        if ("python.sandbox".equals(selected)) {
            input.put("code", "print('sandbox execution ok')");
        } else if ("http.request".equals(selected)) {
            input.put("url", "https://example.com");
            input.put("method", "GET");
        } else if ("web.fetch".equals(selected)) {
            input.put("url", "https://example.com/a");
        }
        return AgentDecision.callTool(
                "Heuristic selected " + selected + " for the question.",
                selected,
                input,
                0.6
        );
    }

    private AgentDecision heuristicContinuation(InternalAgentRunRequest request, ToolResult previousResult) {
        if (previousResult == null) {
            return AgentDecision.finalAnswer(
                    "No tool observation; answering with what we have.",
                    "我已根据可用信息给出初步回答。",
                    0.4
            );
        }
        if (!"success".equalsIgnoreCase(previousResult.status())) {
            return AgentDecision.finalAnswer(
                    "Previous tool failed; surfacing the error.",
                    "工具执行失败：" + (previousResult.errorMessage() == null ? previousResult.summary() : previousResult.errorMessage()),
                    0.3
            );
        }
        // Defer the final answer text to AgentAnswerComposer (template or LLM-driven) by
        // returning FINAL_ANSWER with a null/blank text — the executor will synthesize.
        return new AgentDecision(
                "Sufficient observation collected; ready to answer.",
                AgentDecision.Action.FINAL_ANSWER,
                null,
                Map.of(),
                null,
                0.7
        );
    }

    private String buildInitialPrompt(InternalAgentRunRequest request, Map<String, ToolSpec> enabledTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a ReAct planner. Choose the next action.\n\n");
        prompt.append("User question: ").append(request.question()).append("\n");
        if (request.knowledgeBaseId() != null) {
            prompt.append("Knowledge base: ").append(request.knowledgeBaseId()).append("\n");
        }
        prompt.append("Available tools:\n");
        for (ToolSpec spec : enabledTools.values()) {
            prompt.append("  - ").append(spec.id()).append(" (kind=").append(spec.kind() == null ? "tool" : spec.kind())
                    .append(", risk=").append(spec.riskLevel() == null ? "standard" : spec.riskLevel()).append(")\n");
        }
        prompt.append("\nReply ONLY with JSON of shape:\n");
        prompt.append("{\"thoughtSummary\":string,\"action\":\"CALL_TOOL|FINAL_ANSWER|ASK_CLARIFICATION|STOP_WITH_ERROR\",\"toolId\":string|null,\"toolInput\":object,\"finalAnswer\":string|null,\"confidence\":number}\n");
        return prompt.toString();
    }

    private String buildContinuationPrompt(
            InternalAgentRunRequest request,
            Map<String, ToolSpec> enabledTools,
            String previousToolId,
            ToolResult previousResult,
            int toolCallsSoFar
    ) {
        StringBuilder prompt = new StringBuilder(buildInitialPrompt(request, enabledTools));
        prompt.append("\nPrevious tool: ").append(previousToolId == null ? "(none)" : previousToolId).append("\n");
        if (previousResult != null) {
            prompt.append("Status: ").append(previousResult.status()).append("\n");
            prompt.append("Summary: ").append(previousResult.summary()).append("\n");
        }
        prompt.append("Tool calls so far: ").append(toolCallsSoFar).append("\n");
        prompt.append("Decide whether to call another tool or to finalize the answer.\n");
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private AgentDecision parseDecision(String response, Map<String, ToolSpec> enabledTools) {
        if (response == null || response.isBlank()) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(response);
        if (!matcher.find()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(matcher.group(), MAP_TYPE);
            String actionStr = parsed.get("action") instanceof String s ? s : null;
            if (actionStr == null) {
                return null;
            }
            AgentDecision.Action action;
            try {
                action = AgentDecision.Action.valueOf(actionStr);
            } catch (IllegalArgumentException exception) {
                return null;
            }
            String thought = parsed.get("thoughtSummary") instanceof String t ? t : "";
            String toolId = parsed.get("toolId") instanceof String tid && !tid.isBlank() ? tid : null;
            Object toolInputObj = parsed.get("toolInput");
            Map<String, Object> toolInput = toolInputObj instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            String finalAnswer = parsed.get("finalAnswer") instanceof String fa && !fa.isBlank() ? fa : null;
            Double confidence = parsed.get("confidence") instanceof Number n ? n.doubleValue() : null;
            if (action == AgentDecision.Action.CALL_TOOL) {
                if (toolId == null || !enabledTools.containsKey(toolId)) {
                    return null;
                }
            }
            return new AgentDecision(thought, action, toolId, toolInput, finalAnswer, confidence);
        } catch (Exception exception) {
            return null;
        }
    }

    /** Optional model client. Provide a Spring bean implementing this to enable real LLM-driven planning. */
    public interface ChatModelClient {
        String generate(String prompt) throws Exception;
    }
}
