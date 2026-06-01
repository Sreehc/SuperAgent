package com.superagent.auth.domain;

public record UserAccount(
        long id,
        String username,
        String passwordHash,
        String displayName,
        String email,
        String status,
        Long defaultTenantId
) {
}
