package com.superagent.auth.web;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.service.TenantAccessService;
import com.superagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/{tenantId}/members")
    public ApiResponse<MemberCreateResponse> createMember(
            @PathVariable long tenantId,
            @Valid @RequestBody CreateMemberRequest request
    ) {
        TenantAccessService.MemberCreateResult result = tenantAccessService.createMember(
                tenantId,
                request.username(),
                request.displayName(),
                request.email(),
                request.password(),
                request.role()
        );
        return ApiResponse.success(new MemberCreateResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.email(),
                result.role(),
                result.status()
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PatchMapping("/{tenantId}/members/{userId}")
    public ApiResponse<MemberUpdateResponse> updateMember(
            @PathVariable long tenantId,
            @PathVariable long userId,
            @Valid @RequestBody UpdateMemberRequest request
    ) {
        TenantAccessService.MemberUpdateResult result = tenantAccessService.updateMember(
                tenantId,
                userId,
                request.role(),
                request.status()
        );
        return ApiResponse.success(new MemberUpdateResponse(result.userId(), result.role(), result.status()));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @DeleteMapping("/{tenantId}/members/{userId}")
    public ApiResponse<MemberRemoveResponse> removeMember(
            @PathVariable long tenantId,
            @PathVariable long userId
    ) {
        return ApiResponse.success(new MemberRemoveResponse(tenantAccessService.removeMember(tenantId, userId)));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/{tenantId}/members/{userId}/password")
    public ApiResponse<PasswordResetResponse> resetMemberPassword(
            @PathVariable long tenantId,
            @PathVariable long userId,
            @Valid @RequestBody ResetMemberPasswordRequest request
    ) {
        TenantAccessService.PasswordResetResult result = tenantAccessService.resetMemberPassword(
                tenantId,
                userId,
                request.password()
        );
        return ApiResponse.success(new PasswordResetResponse(result.userId(), result.reset()));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @GetMapping("/{tenantId}/members/invitations")
    public ApiResponse<List<InvitationItem>> listInvitations(
            @PathVariable long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        return ApiResponse.success(tenantAccessService.listInvitations(tenantId, status, p, ps).stream()
                .map(item -> new InvitationItem(
                        item.id(),
                        item.email(),
                        item.role(),
                        item.status(),
                        item.invitedBy(),
                        item.expiresAt(),
                        item.acceptedAt(),
                        item.createdAt()
                ))
                .toList());
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/{tenantId}/members/invitations")
    public ApiResponse<InvitationCreatedResponse> createInvitation(
            @PathVariable long tenantId,
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        TenantAccessService.InvitationView invitation = tenantAccessService.createInvitation(
                tenantId,
                request.email(),
                request.role()
        );
        return ApiResponse.success(new InvitationCreatedResponse(
                invitation.id(),
                invitation.tenantId(),
                invitation.email(),
                invitation.role(),
                invitation.status(),
                invitation.expiresAt(),
                invitation.invitedBy(),
                invitation.token()
        ));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @DeleteMapping("/{tenantId}/members/invitations/{invitationId}")
    public ApiResponse<RevokeInvitationResponse> revokeInvitation(
            @PathVariable long tenantId,
            @PathVariable long invitationId
    ) {
        return ApiResponse.success(new RevokeInvitationResponse(
                tenantAccessService.revokeInvitation(tenantId, invitationId)
        ));
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

    public record CreateMemberRequest(
            @NotBlank @Size(max = 128) String username,
            @Size(max = 128) String displayName,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotNull TenantRole role
    ) {
    }

    public record MemberCreateResponse(
            long userId,
            String username,
            String displayName,
            String email,
            String role,
            String status
    ) {
    }

    public record UpdateMemberRequest(TenantRole role, String status) {
    }

    public record MemberUpdateResponse(long userId, String role, String status) {
    }

    public record MemberRemoveResponse(boolean removed) {
    }

    public record ResetMemberPasswordRequest(
            @NotBlank @Size(min = 8, max = 128) String password
    ) {
    }

    public record PasswordResetResponse(long userId, boolean reset) {
    }

    public record CreateInvitationRequest(
            @NotBlank @Email String email,
            @NotNull TenantRole role
    ) {
    }

    public record InvitationCreatedResponse(
            long id,
            long tenantId,
            String email,
            String role,
            String status,
            OffsetDateTime expiresAt,
            long invitedBy,
            String token
    ) {
    }

    public record InvitationItem(
            long id,
            String email,
            String role,
            String status,
            long invitedBy,
            OffsetDateTime expiresAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record RevokeInvitationResponse(boolean revoked) {
    }
}
