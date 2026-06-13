package com.superagent.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogItem(
        long id,
        Long tenantId,
        Long actorId,
        String action,
        String resourceType,
        Long resourceId,
        Map<String, Object> detail,
        OffsetDateTime createdAt
) {
}
