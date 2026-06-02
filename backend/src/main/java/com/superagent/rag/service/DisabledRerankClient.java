package com.superagent.rag.service;

import com.superagent.rag.domain.RagEvidence;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "super-agent.ai.rerank-provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledRerankClient implements RerankClient {

    @Override
    public RerankResult rerank(String query, List<RagEvidence> evidences) {
        return new RerankResult(
                evidences,
                null,
                null,
                "skipped",
                "provider_disabled",
                null,
                null
        );
    }
}
