package com.superagent.auth.service;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.domain.UserAccount;
import com.superagent.auth.repository.TenantInvitationRepository;
import com.superagent.auth.repository.TenantMemberRepository;
import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.repository.UserAccountRepository;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.settings.repository.AuditLogRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAccessService {

    private static final long INVITATION_TTL_DAYS = 7;

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;

    public TenantAccessService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            TenantInvitationRepository tenantInvitationRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.tenantInvitationRepository = tenantInvitationRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
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

    @Transactional
    public InvitationView createInvitation(long tenantId, String email, TenantRole targetRole) {
        AuthenticatedUserPrincipal principal = requireMembershipMatch(tenantId);
        TenantRole actorRole = principal.currentRole();
        if (actorRole != TenantRole.OWNER && actorRole != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only OWNER or ADMIN can invite");
        }
        if (targetRole == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "role is required");
        }
        if (actorRole == TenantRole.ADMIN && targetRole != TenantRole.MEMBER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "ADMIN can only invite MEMBER");
        }
        String normalizedEmail = normalizeEmail(email);
        if (tenantInvitationRepository.hasPendingForEmail(tenantId, normalizedEmail)) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "An active invitation already exists for this email");
        }
        Optional<UserAccount> existing = userAccountRepository.findByEmail(normalizedEmail);
        if (existing.isPresent() && tenantMemberRepository.exists(tenantId, existing.get().id())) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "User is already a member of this tenant");
        }
        String token = generateToken();
        String tokenHash = hashToken(token);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(INVITATION_TTL_DAYS);
        long invitationId = tenantInvitationRepository.create(
                tenantId,
                normalizedEmail,
                targetRole.name(),
                tokenHash,
                principal.userId(),
                expiresAt
        );
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("invitationId", invitationId);
        detail.put("email", normalizedEmail);
        detail.put("role", targetRole.name());
        auditLogRepository.append(tenantId, principal.userId(), "tenant.invitation.created", "tenant_invitation", invitationId, detail);
        return new InvitationView(
                invitationId,
                tenantId,
                normalizedEmail,
                targetRole.name(),
                "pending",
                expiresAt,
                principal.userId(),
                token
        );
    }

    public List<InvitationListItem> listInvitations(long tenantId, String status, int page, int pageSize) {
        AuthenticatedUserPrincipal principal = requireMembershipMatch(tenantId);
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only OWNER or ADMIN can view invitations");
        }
        return tenantInvitationRepository.listByTenant(tenantId, status, page, pageSize).stream()
                .map(record -> new InvitationListItem(
                        record.id(),
                        record.email(),
                        record.role(),
                        record.status(),
                        record.invitedBy(),
                        record.expiresAt(),
                        record.acceptedAt(),
                        record.createdAt()
                ))
                .toList();
    }

    @Transactional
    public boolean revokeInvitation(long tenantId, long invitationId) {
        AuthenticatedUserPrincipal principal = requireMembershipMatch(tenantId);
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
        }
        TenantInvitationRepository.InvitationRecord invitation = tenantInvitationRepository.findById(tenantId, invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Invitation not found"));
        if (!"pending".equals(invitation.status())) {
            return false;
        }
        boolean updated = tenantInvitationRepository.updateStatus(tenantId, invitationId, "revoked");
        if (updated) {
            auditLogRepository.append(tenantId, principal.userId(), "tenant.invitation.revoked", "tenant_invitation", invitationId, Map.of());
        }
        return updated;
    }

    @Transactional
    public MemberUpdateResult updateMember(long tenantId, long userId, TenantRole newRole, String newStatus) {
        AuthenticatedUserPrincipal principal = requireMembershipMatch(tenantId);
        TenantRole actorRole = principal.currentRole();
        if (actorRole != TenantRole.OWNER && actorRole != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
        }
        TenantMembership target = tenantMemberRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Member not found"));

        if (actorRole == TenantRole.ADMIN && (target.role() == TenantRole.OWNER || target.role() == TenantRole.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "ADMIN cannot manage OWNER or ADMIN");
        }
        if (actorRole == TenantRole.ADMIN && newRole != null && newRole != TenantRole.MEMBER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "ADMIN can only assign MEMBER role");
        }
        if (newRole != null && newRole != target.role()) {
            // Block role downgrade if it would leave zero OWNERs
            if (target.role() == TenantRole.OWNER && newRole != TenantRole.OWNER) {
                ensureNotLastOwner(tenantId);
            }
            if (target.role() == TenantRole.OWNER && actorRole != TenantRole.OWNER) {
                throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only OWNER can change OWNER role");
            }
            tenantMemberRepository.updateRole(tenantId, userId, newRole);
        }
        String normalizedStatus = normalizeStatus(newStatus);
        if (normalizedStatus != null && !normalizedStatus.equals(target.status())) {
            if (target.role() == TenantRole.OWNER && "suspended".equals(normalizedStatus)) {
                ensureNotLastOwner(tenantId);
            }
            tenantMemberRepository.updateStatus(tenantId, userId, normalizedStatus);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        if (newRole != null) detail.put("role", newRole.name());
        if (normalizedStatus != null) detail.put("status", normalizedStatus);
        auditLogRepository.append(tenantId, principal.userId(), "tenant.member.updated", "tenant_member", userId, detail);
        TenantMembership refreshed = tenantMemberRepository.findByUserIdAndTenantId(userId, tenantId).orElseThrow();
        return new MemberUpdateResult(refreshed.userId(), refreshed.role().name(), refreshed.status());
    }

    @Transactional
    public boolean removeMember(long tenantId, long userId) {
        AuthenticatedUserPrincipal principal = requireMembershipMatch(tenantId);
        TenantRole actorRole = principal.currentRole();
        if (actorRole != TenantRole.OWNER && actorRole != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
        }
        TenantMembership target = tenantMemberRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Member not found"));
        if (actorRole == TenantRole.ADMIN && (target.role() == TenantRole.OWNER || target.role() == TenantRole.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "ADMIN cannot remove OWNER or ADMIN");
        }
        if (target.role() == TenantRole.OWNER) {
            ensureNotLastOwner(tenantId);
        }
        if (target.userId() == principal.userId()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Cannot remove yourself; transfer ownership first");
        }
        tenantMemberRepository.delete(tenantId, userId);
        userAccountRepository.clearDefaultTenantId(userId, tenantId);
        auditLogRepository.append(tenantId, principal.userId(), "tenant.member.removed", "tenant_member", userId, Map.of());
        return true;
    }

    private void ensureNotLastOwner(long tenantId) {
        if (tenantMemberRepository.countActiveOwners(tenantId) <= 1) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Cannot remove the last OWNER");
        }
    }

    private AuthenticatedUserPrincipal requireMembershipMatch(long tenantId) {
        TenantContext context = TenantContextHolder.get();
        if (context == null || context.tenantId() != tenantId) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context mismatch");
        }
        return currentAuthenticatedUser.get();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "email is required");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 255 || !trimmed.contains("@")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid email");
        }
        return trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (!"active".equals(normalized) && !"suspended".equals(normalized)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid status");
        }
        return normalized;
    }

    private String generateToken() {
        byte[] buffer = new byte[24];
        new SecureRandom().nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash invitation token", exception);
        }
    }

    public record SwitchTenantResult(long tenantId, String role) {
    }

    public record InvitationView(
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

    public record InvitationListItem(
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

    public record MemberUpdateResult(long userId, String role, String status) {
    }
}
