CREATE INDEX idx_embedding_vector
    ON document_embedding
    USING hnsw (embedding vector_cosine_ops);
