package com.superagent.auth.domain;

import java.time.OffsetDateTime;

public record RefreshTokenRecord(
        long id,
        long userId,
        Long tenantId,
        String tokenHash,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt
) {
    public boolean isRevoked() {
        return revokedAt != null;
    }
}
