package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record KnowledgeDomain(
        long id,
        long tenantId,
        String code,
        String name,
        String description,
        String status,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
