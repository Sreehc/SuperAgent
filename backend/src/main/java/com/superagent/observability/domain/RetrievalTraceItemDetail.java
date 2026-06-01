package com.superagent.observability.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record RetrievalTraceItemDetail(
        long id,
        long documentId,
        long chunkId,
        int rankNo,
        BigDecimal rawScore,
        BigDecimal fusedScore,
        boolean selected,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
