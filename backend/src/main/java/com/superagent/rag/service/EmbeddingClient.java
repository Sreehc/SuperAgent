package com.superagent.rag.service;

import java.util.List;

public interface EmbeddingClient {

    EmbeddingResult embed(List<String> inputs);

    record EmbeddingResult(
            String provider,
            String model,
            int dimension,
            List<List<Double>> vectors
    ) {
    }
}
