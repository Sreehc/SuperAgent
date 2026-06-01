package com.superagent.auth.domain;

public record Tenant(
        long id,
        String name,
        String code,
        String status
) {
}
