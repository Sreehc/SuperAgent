package com.superagent.auth.security;

import com.superagent.auth.domain.TenantRole;

public record TenantContext(long tenantId, TenantRole role) {
}
