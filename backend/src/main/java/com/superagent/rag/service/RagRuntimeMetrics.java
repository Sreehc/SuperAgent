package com.superagent.rag.service;

import com.superagent.rag.domain.RagResponse;
import com.superagent.rag.domain.RagResponseDiagnostics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RagRuntimeMetrics {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    private final MeterRegistry meterRegistry;

    public RagRuntimeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRetrievalStep(RagResponseDiagnostics.RetrievalStep step) {
        recordLatency("vector", step.vectorLatencyMs());
        recordLatency("keyword", step.keywordLatencyMs());
        recordLatency("rrf", step.fusedLatencyMs());
    }

    public void recordAnswer(RagResponse response) {
        String outcome = response.evidences().isEmpty() ? "no_evidence" : "grounded";
        Counter.builder("superagent.rag.answer.total")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        Counter.builder("superagent.rag.selected_evidence.total")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment(response.evidences().size());
        if ("no_evidence".equals(outcome)) {
            Counter.builder("superagent.rag.no_evidence.total")
                    .register(meterRegistry)
                    .increment();
        }

        RagResponseDiagnostics diagnostics = response.diagnostics();
        if (diagnostics == null) {
            return;
        }

        if (diagnostics.fallbackReason() != null && !diagnostics.fallbackReason().isBlank()) {
            Counter.builder("superagent.rag.fallback.total")
                    .tag("reason", diagnostics.fallbackReason())
                    .register(meterRegistry)
                    .increment();
        }

        if (diagnostics.citationAppended()) {
            Counter.builder("superagent.rag.citation_appended.total")
                    .register(meterRegistry)
                    .increment();
        }

        RagResponseDiagnostics.RerankStep rerankStep = diagnostics.rerankStep();
        if (rerankStep != null) {
            if (rerankStep.enabled()) {
                Counter.builder("superagent.rag.rerank.total")
                        .tag("status", rerankStep.status())
                        .register(meterRegistry)
                        .increment();
                recordRerankLatency(rerankStep.status(), rerankStep.latencyMs());
            }
            if (rerankStep.enabled() && !"success".equals(rerankStep.status())) {
                Counter.builder("superagent.rag.rerank.fallback.total")
                        .tag("status", rerankStep.status())
                        .register(meterRegistry)
                        .increment();
            }
        }

        if ("grounded".equals(outcome)) {
            Counter.builder("superagent.rag.citation.coverage.total")
                    .tag("result", hasValidCitation(response.answer().fullText(), response.evidences().size()) ? "covered" : "missing")
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void recordLatency(String channel, Integer latencyMs) {
        if (latencyMs == null) {
            return;
        }
        Timer.builder("superagent.rag.retrieval.latency")
                .tag("channel", channel)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, latencyMs)));
    }

    private void recordRerankLatency(String status, Integer latencyMs) {
        if (latencyMs == null) {
            return;
        }
        Timer.builder("superagent.rag.rerank.latency")
                .tag("status", status == null || status.isBlank() ? "unknown" : status)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, latencyMs)));
    }

    private boolean hasValidCitation(String text, int evidenceCount) {
        if (text == null || text.isBlank() || evidenceCount <= 0) {
            return false;
        }
        Matcher matcher = CITATION_PATTERN.matcher(text);
        LinkedHashSet<Integer> validOrdinals = new LinkedHashSet<>();
        while (matcher.find()) {
            int ordinal = Integer.parseInt(matcher.group(1));
            if (ordinal >= 1 && ordinal <= evidenceCount) {
                validOrdinals.add(ordinal);
            }
        }
        return !validOrdinals.isEmpty();
    }
}
