package com.superagent.knowledge.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record ChunkingProfile(
        long id,
        long tenantId,
        String code,
        String name,
        String strategy,
        Map<String, Object> config,
        boolean isDefault,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
