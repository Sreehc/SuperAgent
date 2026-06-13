package com.superagent.agent.service;

import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.domain.ToolResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentAnswerComposer {

    public String compose(InternalAgentRunRequest request, String toolId, ToolResult toolResult) {
        if (toolResult == null) {
            return "当前 Agent 缺少可用工具观察，已提前结束。";
        }
        if (!"success".equalsIgnoreCase(toolResult.status())) {
            return "工具执行失败，无法完成原始请求。\n\n"
                    + "- 工具：" + safeToolId(toolId, toolResult) + "\n"
                    + "- 摘要：" + safeText(toolResult.summary()) + "\n"
                    + "- 错误：" + safeText(toolResult.errorMessage());
        }
        return switch (safeToolId(toolId, toolResult)) {
            case "knowledge.search" -> composeKnowledgeAnswer(request, toolResult);
            case "web.search" -> composeWebSearchAnswer(request, toolResult);
            case "web.fetch" -> composeWebFetchAnswer(request, toolResult);
            case "http.request" -> composeHttpAnswer(request, toolResult);
            case "graph.query" -> composeGraphAnswer(request, toolResult);
            case "python.sandbox" -> composePythonAnswer(request, toolResult);
            default -> fallbackAnswer(toolResult);
        };
    }

    private String composeKnowledgeAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        List<?> evidence = listValue(toolResult.output().get("evidence"));
        if (evidence.isEmpty()) {
            return "我没有在当前知识库中找到足够证据。\n\n问题：" + safeText(request.question());
        }
        StringBuilder answer = new StringBuilder("我根据知识库检索到了以下证据：\n");
        appendEvidence(answer, evidence, "quote");
        answer.append("\n结论：请优先依据上述文档证据继续判断；如果需要更精确结论，可以继续限定文档、时间或业务场景。");
        return answer.toString();
    }

    private String composeWebSearchAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        List<?> results = listValue(toolResult.output().get("results"));
        if (results.isEmpty()) {
            return "联网搜索没有返回可用结果。\n\n问题：" + safeText(request.question());
        }
        StringBuilder answer = new StringBuilder("我根据联网搜索整理了这些结果：\n");
        appendEvidence(answer, results, "summary");
        answer.append("\n结论：以上结果可作为实时信息参考，建议继续打开来源核对发布时间和原文细节。");
        return answer.toString();
    }

    private String composeWebFetchAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        Map<String, Object> output = toolResult.output();
        return "我已抓取网页并提取正文摘要：\n\n"
                + "- 标题：" + safeText(output.get("title")) + "\n"
                + "- URL：" + safeText(output.get("url")) + "\n"
                + "- 发布时间：" + safeText(output.get("publishedAt")) + "\n"
                + "- 摘要：" + safeText(output.get("summary"));
    }

    private String composeHttpAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        Map<String, Object> output = toolResult.output();
        return "受控 HTTP 请求已完成：\n\n"
                + "- URL：" + safeText(output.get("url")) + "\n"
                + "- 方法：" + safeText(output.get("method")) + "\n"
                + "- 状态码：" + safeText(output.get("statusCode")) + "\n"
                + "- 响应摘要：" + safeText(output.get("body"));
    }

    private String composeGraphAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        List<?> evidence = listValue(toolResult.output().get("evidence"));
        List<?> nodes = listValue(toolResult.output().get("nodes"));
        StringBuilder answer = new StringBuilder("我根据图谱查询得到以下结构化线索：\n");
        if (!evidence.isEmpty()) {
            appendEvidence(answer, evidence, "summary");
        } else if (!nodes.isEmpty()) {
            appendEvidence(answer, nodes, "label");
        } else {
            answer.append("- 暂无可用图谱证据。\n");
        }
        answer.append("\n结论：这些实体和关系可用于继续追问上下游影响、路径或关联文档。");
        return answer.toString();
    }

    private String composePythonAnswer(InternalAgentRunRequest request, ToolResult toolResult) {
        Map<String, Object> output = toolResult.output();
        return "Python Sandbox 执行完成：\n\n"
                + "- 状态：" + safeText(output.get("status")) + "\n"
                + "- 标准输出：\n" + codeBlock(safeText(output.get("stdout"))) + "\n"
                + "- 标准错误：\n" + codeBlock(safeText(output.get("stderr")));
    }

    private String fallbackAnswer(ToolResult toolResult) {
        return safeText(toolResult.summary()).isBlank()
                ? "我已完成当前 Agent 工具执行。"
                : safeText(toolResult.summary());
    }

    private void appendEvidence(StringBuilder answer, List<?> items, String preferredTextKey) {
        int limit = Math.min(items.size(), 5);
        for (int index = 0; index < limit; index++) {
            Object item = items.get(index);
            if (item instanceof Map<?, ?> map) {
                String title = firstNonBlank(map.get("title"), map.get("documentTitle"), map.get("url"), map.get("entity"), map.get("label"));
                String text = firstNonBlank(map.get(preferredTextKey), map.get("quote"), map.get("summary"), map.get("content"));
                String url = safeText(map.get("url"));
                answer.append(index + 1).append(". ").append(title.isBlank() ? "证据" : title);
                if (!url.isBlank()) {
                    answer.append(" (").append(url).append(")");
                }
                if (!text.isBlank()) {
                    answer.append("：").append(text);
                }
                answer.append("\n");
            } else {
                answer.append(index + 1).append(". ").append(safeText(item)).append("\n");
            }
        }
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = safeText(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String safeToolId(String toolId, ToolResult toolResult) {
        return toolId == null || toolId.isBlank() ? toolResult.toolId() : toolId;
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String codeBlock(String value) {
        return "```text\n" + (value.isBlank() ? "(empty)" : value) + "\n```";
    }
}
