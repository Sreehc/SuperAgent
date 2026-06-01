package com.superagent.observability.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RetrievalTraceDetail(
        long id,
        Long stageId,
        int subQuestionNo,
        String channel,
        String queryText,
        Map<String, Object> filters,
        int resultCount,
        int selectedCount,
        Integer latencyMs,
        OffsetDateTime createdAt,
        List<RetrievalTraceItemDetail> items
) {
}
