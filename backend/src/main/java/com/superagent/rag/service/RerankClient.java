package com.superagent.rag.service;

import com.superagent.rag.domain.RagEvidence;
import java.util.List;

public interface RerankClient {

    RerankResult rerank(String query, List<RagEvidence> evidences);

    record RerankResult(
            List<RagEvidence> evidences,
            String provider,
            String model,
            String status,
            String skippedReason,
            String errorMessage,
            Integer latencyMs
    ) {
    }
}
