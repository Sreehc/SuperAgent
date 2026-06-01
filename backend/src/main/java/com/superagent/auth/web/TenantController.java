package com.superagent.auth.web;

import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.service.TenantAccessService;
import com.superagent.common.api.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/tenants")
public class TenantController {

    private final TenantAccessService tenantAccessService;

    public TenantController(TenantAccessService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping
    public ApiResponse<List<TenantItem>> listTenants() {
        return ApiResponse.success(tenantAccessService.listCurrentUserTenants().stream()
                .map(tenant -> new TenantItem(
                        tenant.tenantId(),
                        tenant.tenantName(),
                        tenant.tenantCode(),
                        tenant.role().name(),
                        tenant.tenantStatus()
                ))
                .toList());
    }

    @PostMapping("/{tenantId}/switch")
    public ApiResponse<SwitchTenantResponse> switchTenant(@PathVariable long tenantId) {
        TenantAccessService.SwitchTenantResult result = tenantAccessService.switchTenant(tenantId);
        return ApiResponse.success(new SwitchTenantResponse(result.tenantId(), result.role()));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @GetMapping("/{tenantId}/members")
    public ApiResponse<List<MemberItem>> listTenantMembers(@PathVariable long tenantId) {
        return ApiResponse.success(tenantAccessService.listTenantMembers(tenantId).stream()
                .map(member -> new MemberItem(
                        member.userId(),
                        member.username(),
                        member.displayName(),
                        member.role().name(),
                        member.status(),
                        member.joinedAt().toString()
                ))
                .toList());
    }

    public record TenantItem(long id, String name, String code, String role, String status) {
    }

    public record SwitchTenantResponse(long tenantId, String role) {
    }

    public record MemberItem(
            long userId,
            String username,
            String displayName,
            String role,
            String status,
            String joinedAt
    ) {
    }
}
