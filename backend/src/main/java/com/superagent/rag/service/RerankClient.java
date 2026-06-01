package com.superagent.rag.service;

import com.superagent.rag.domain.RagEvidence;
import java.util.List;

public interface RerankClient {

    List<RagEvidence> rerank(String query, List<RagEvidence> evidences);
}
