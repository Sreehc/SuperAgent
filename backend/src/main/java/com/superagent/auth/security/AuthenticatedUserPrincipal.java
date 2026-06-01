package com.superagent.auth.security;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.TenantRole;
import java.util.List;

public record AuthenticatedUserPrincipal(
        long userId,
        String username,
        String displayName,
        Long defaultTenantId,
        List<TenantMembership> memberships,
        Long currentTenantId,
        TenantRole currentRole
) {
}
