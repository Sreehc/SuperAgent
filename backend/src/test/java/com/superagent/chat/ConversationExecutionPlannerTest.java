package com.superagent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.service.ConversationExecutionPlanner;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationExecutionPlannerTest {

    private final ConversationExecutionPlanner planner = new ConversationExecutionPlanner();

    @Test
    void shouldRouteAmbiguousQuestionToClarification() {
        var plan = planner.plan("这个怎么配？", 10L, List.of("上一个问题"));

        assertThat(plan.executionMode()).isEqualTo(ExecutionMode.CLARIFICATION);
        assertThat(plan.routeReason()).isEqualTo("insufficient_context_needs_clarification");
        assertThat(plan.steps()).contains("clarify_missing_subject");
    }

    @Test
    void shouldRouteKnowledgeBaseQuestionToRag() {
        var plan = planner.plan("退款规则是什么？", 10L, List.of("上一个问题"));

        assertThat(plan.executionMode()).isEqualTo(ExecutionMode.RAG_QA);
        assertThat(plan.routeReason()).isEqualTo("knowledge_base_selected");
        assertThat(plan.steps()).contains("retrieve_evidence");
    }

    @Test
    void shouldRouteOpenEndedQuestionToAgentMode() {
        var plan = planner.plan("请搜索今天最新的退款政策变化", null, List.of());

        assertThat(plan.executionMode()).isEqualTo(ExecutionMode.REACT_AGENT);
        assertThat(plan.routeReason()).isEqualTo("open_ended_or_realtime_request_routed_to_agent");
        assertThat(plan.summary()).isEqualTo("run_react_agent_pipeline");
    }

    @Test
    void shouldRouteKnowledgeBaseRealtimeTaskToAgentMode() {
        var plan = planner.plan("请联网搜索今天最新退款政策并结合知识库总结差异", 10L, List.of("上一个问题"));

        assertThat(plan.executionMode()).isEqualTo(ExecutionMode.REACT_AGENT);
        assertThat(plan.routeReason()).isEqualTo("open_ended_or_realtime_request_routed_to_agent");
    }

    @Test
    void shouldKeepDirectQuestionWithoutKnowledgeBaseOnRagMode() {
        var plan = planner.plan("退款规则是什么？", null, List.of("上一个问题"));

        assertThat(plan.executionMode()).isEqualTo(ExecutionMode.RAG_QA);
        assertThat(plan.routeReason()).isEqualTo("direct_chat_with_memory_only");
    }
}
