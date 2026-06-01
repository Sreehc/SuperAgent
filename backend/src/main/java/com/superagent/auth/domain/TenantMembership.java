package com.superagent.auth.domain;

import java.time.OffsetDateTime;

public record TenantMembership(
        long tenantId,
        String tenantName,
        String tenantCode,
        String tenantStatus,
        long userId,
        TenantRole role,
        String status,
        OffsetDateTime joinedAt
) {
}
