package com.superagent.chat.service;

import com.superagent.chat.domain.ExecutionMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ConversationExecutionPlanner {

    public ExecutionPlan plan(String message, Long knowledgeBaseId, List<String> recentMessages) {
        String normalized = message == null ? "" : message.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (needsClarification(normalized, lower)) {
            return new ExecutionPlan(
                    ExecutionMode.CLARIFICATION,
                    "insufficient_context_needs_clarification",
                    BigDecimal.valueOf(0.88d),
                    "ask_for_clarification",
                    List.of("clarify_missing_subject", "pause_rag_retrieval")
            );
        }

        if (knowledgeBaseId != null) {
            return new ExecutionPlan(
                    ExecutionMode.RAG_QA,
                    "knowledge_base_selected",
                    BigDecimal.valueOf(0.93d),
                    "run_rag_pipeline",
                    List.of("assemble_memory", "rewrite_question", "retrieve_evidence", "answer_with_citations")
            );
        }

        if (looksLikeOpenEndedOrRealtime(lower)) {
            return new ExecutionPlan(
                    ExecutionMode.REACT_AGENT_RESERVED,
                    "open_ended_or_realtime_request_reserved_for_agent",
                    BigDecimal.valueOf(0.72d),
                    "fallback_to_non_agent_response",
                    List.of("record_reserved_agent_route", "respond_without_tool_execution")
            );
        }

        return new ExecutionPlan(
                ExecutionMode.RAG_QA,
                "direct_chat_with_memory_only",
                BigDecimal.valueOf(0.81d),
                "run_rag_pipeline_without_knowledge_base",
                List.of("assemble_memory", "rewrite_question", "retrieve_evidence_if_available", "answer_or_no_evidence")
        );
    }

    private boolean needsClarification(String normalized, String lower) {
        if (normalized.isBlank() || normalized.length() <= 3) {
            return true;
        }
        return List.of("这个", "那个", "它", "他", "她", "怎么弄", "怎么配", "怎么做", "什么意思")
                .stream()
                .anyMatch(lower::contains)
                && !containsSpecificContext(lower);
    }

    private boolean containsSpecificContext(String lower) {
        return lower.contains("退款")
                || lower.contains("配置")
                || lower.contains("部署")
                || lower.contains("订单")
                || lower.contains("接口")
                || lower.contains("日志")
                || lower.contains("文档");
    }

    private boolean looksLikeOpenEndedOrRealtime(String lower) {
        return lower.contains("搜索")
                || lower.contains("联网")
                || lower.contains("今天")
                || lower.contains("最新")
                || lower.contains("实时")
                || lower.contains("帮我规划")
                || lower.contains("帮我执行");
    }

    public record ExecutionPlan(
            ExecutionMode executionMode,
            String routeReason,
            BigDecimal routeConfidence,
            String summary,
            List<String> steps
    ) {
    }
}
