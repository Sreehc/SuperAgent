package com.superagent.knowledge.domain;

public enum KnowledgeDocumentStatus {
    uploaded,
    parsing,
    chunking,
    embedding,
    indexing,
    ready,
    failed,
    deleted
}
