package com.superagent.rag.service;

import com.superagent.rag.domain.RagEvidence;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class DisabledRerankClient implements RerankClient {

    @Override
    public List<RagEvidence> rerank(String query, List<RagEvidence> evidences) {
        return evidences;
    }
}
