package com.superagent.auth.service;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.repository.TenantMemberRepository;
import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TenantAccessService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;

    public TenantAccessService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
    }

    public List<TenantMembership> listCurrentUserTenants() {
        return currentAuthenticatedUser.get().memberships();
    }

    public SwitchTenantResult switchTenant(long tenantId) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantMembership membership = principal.memberships().stream()
                .filter(item -> item.tenantId() == tenantId)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant access denied"));
        return new SwitchTenantResult(membership.tenantId(), membership.role().name());
    }

    public List<TenantRepository.MemberView> listTenantMembers(long tenantId) {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null || tenantContext.tenantId() != tenantId) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context mismatch");
        }
        return tenantRepository.findMembers(tenantId);
    }

    public record SwitchTenantResult(long tenantId, String role) {
    }
}
