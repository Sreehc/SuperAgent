package com.superagent.observability.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminTraceDetail(
        long exchangeId,
        long sessionId,
        long userId,
        String executionMode,
        String status,
        String routeReason,
        Long agentRunId,
        String agentRunStatus,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        long durationMs,
        List<TraceStageDetail> stages,
        List<ModelCallTraceDetail> modelCalls,
        List<RetrievalTraceDetail> retrievals,
        List<RerankTraceDetail> reranks
) {
}
