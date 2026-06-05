package com.superagent.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.rag.domain.RagAnswer;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagResponse;
import com.superagent.rag.domain.RagResponseDiagnostics;
import com.superagent.rag.domain.RagSearchQuery;
import com.superagent.rag.service.RagRuntimeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagRuntimeMetricsTest {

    @Test
    void shouldRecordRetrievalAndAnswerMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RagRuntimeMetrics metrics = new RagRuntimeMetrics(meterRegistry);

        RagSearchQuery query = new RagSearchQuery(
                "退款规则是什么？",
                "退款规则是什么？",
                "退款规则是什么？",
                1,
                1L,
                null,
                null,
                null,
                List.of(),
                "single_question",
                "provider_unavailable",
                0.0d,
                true,
                true,
                8,
                8,
                12,
                60,
                1,
                2,
                4,
                2000,
                6000,
                0.35d,
                0.55d,
                0.55d,
                false,
                30L,
                1,
                true,
                1,
                true,
                true,
                false,
                "normal",
                List.of()
        );
        RagResponseDiagnostics.RetrievalStep step = new RagResponseDiagnostics.RetrievalStep(
                query,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                7,
                9,
                3,
                false,
                0,
                0,
                0,
                0
        );
        metrics.recordRetrievalStep(step);

        RagResponse grounded = new RagResponse(
                "退款规则是什么？",
                List.of("退款规则是什么？"),
                List.of(new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        100L,
                        "退款规则文档",
                        1,
                        "退款需在7日内申请",
                        "退款规则",
                        0.92d,
                        Map.of()
                )),
                new RagAnswer(
                        "根据知识库，退款需在7日内申请。[1]",
                        List.of("根据知识库，退款需在7日内申请。[1]"),
                        List.of(),
                        "test-provider",
                        "test-model",
                        10,
                        12,
                        "stop",
                        true
                ),
                new RagResponseDiagnostics(
                        "recent_messages=1",
                        List.of(step),
                        new RagResponseDiagnostics.RerankStep(true, "test-rerank", "rerank-model", "timeout", null, "slow", 15, 3, 3),
                        "rag_prompt_with_evidence_1",
                        "provider=test-provider",
                        "rerank_timeout_used_filtered",
                        true,
                        0.92d,
                        0.55d
                )
        );
        metrics.recordAnswer(grounded);

        RagResponse noEvidence = new RagResponse(
                "发票规则是什么？",
                List.of("发票规则是什么？"),
                List.of(),
                new RagAnswer(
                        "未检索到足够证据。",
                        List.of("未检索到足够证据。"),
                        List.of(),
                        "system",
                        "no-evidence-fallback",
                        null,
                        null,
                        "stop",
                        false
                ),
                new RagResponseDiagnostics(
                        "recent_messages=0",
                        List.of(step),
                        new RagResponseDiagnostics.RerankStep(false, null, null, "skipped", "disabled_by_config", null, null, 0, 0),
                        "no_evidence_fallback_prompt",
                        "provider=system",
                        "no_selected_evidence",
                        false,
                        0.0d,
                        0.55d
                )
        );
        metrics.recordAnswer(noEvidence);

        assertThat(meterRegistry.get("superagent.rag.retrieval.latency").tag("channel", "vector").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("superagent.rag.retrieval.latency").tag("channel", "keyword").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("superagent.rag.retrieval.latency").tag("channel", "rrf").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("superagent.rag.answer.total").tag("outcome", "grounded").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.answer.total").tag("outcome", "no_evidence").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.citation_appended.total").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.fallback.total").tag("reason", "rerank_timeout_used_filtered").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.fallback.total").tag("reason", "no_selected_evidence").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.rerank.total").tag("status", "timeout").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.rerank.fallback.total").tag("status", "timeout").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.rerank.latency").tag("status", "timeout").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("superagent.rag.no_evidence.total").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.citation.coverage.total").tag("result", "covered").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.selected_evidence.total").tag("outcome", "grounded").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("superagent.rag.selected_evidence.total").tag("outcome", "no_evidence").counter().count()).isEqualTo(0.0d);
    }

    @Test
    void shouldMarkGroundedAnswerWithoutValidCitationAsMissingCoverage() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RagRuntimeMetrics metrics = new RagRuntimeMetrics(meterRegistry);

        RagResponse response = new RagResponse(
                "退款规则是什么？",
                List.of("退款规则是什么？"),
                List.of(new RagEvidence(
                        "hybrid",
                        1L,
                        10L,
                        100L,
                        "退款规则文档",
                        1,
                        "退款需在7日内申请",
                        "退款规则",
                        0.92d,
                        Map.of()
                )),
                new RagAnswer(
                        "根据知识库，退款需在7日内申请。",
                        List.of("根据知识库，退款需在7日内申请。"),
                        List.of(),
                        "test-provider",
                        "test-model",
                        10,
                        12,
                        "stop",
                        false
                ),
                new RagResponseDiagnostics(
                        "recent_messages=0",
                        List.of(),
                        new RagResponseDiagnostics.RerankStep(false, null, null, "skipped", "disabled_by_config", null, null, 0, 0),
                        "rag_prompt_with_evidence_1",
                        "provider=test-provider",
                        null,
                        false,
                        0.92d,
                        0.55d
                )
        );

        metrics.recordAnswer(response);

        assertThat(meterRegistry.get("superagent.rag.citation.coverage.total").tag("result", "missing").counter().count()).isEqualTo(1.0d);
    }
}
