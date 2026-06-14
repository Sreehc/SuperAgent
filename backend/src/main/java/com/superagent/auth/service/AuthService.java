package com.superagent.auth.service;

import com.superagent.auth.domain.RefreshTokenRecord;
import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.UserAccount;
import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.repository.UserAccountRepository;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.JwtTokenService;
import com.superagent.auth.security.RefreshTokenService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.settings.repository.AuditLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AuditLogRepository auditLogRepository;

    public AuthService(
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            CurrentAuthenticatedUser currentAuthenticatedUser,
            AuditLogRepository auditLogRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.auditLogRepository = auditLogRepository;
    }

    public LoginResult login(String username, String password) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .filter(user -> "active".equalsIgnoreCase(user.status()))
                .orElse(null);
        if (userAccount == null) {
            recordLoginFailure(null, username, "user_not_found_or_disabled");
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (!passwordEncoder.matches(password, userAccount.passwordHash())) {
            recordLoginFailure(userAccount.id(), username, "invalid_password");
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        List<TenantMembership> memberships = tenantRepository.findMembershipsByUserId(userAccount.id()).stream()
                .filter(membership -> "active".equalsIgnoreCase(membership.status()))
                .filter(membership -> "active".equalsIgnoreCase(membership.tenantStatus()))
                .toList();
        if (memberships.isEmpty()) {
            recordLoginFailure(userAccount.id(), username, "no_active_membership");
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "User has no active tenant membership");
        }

        TenantMembership defaultMembership = resolveDefaultMembership(userAccount, memberships);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(userAccount.id(), defaultMembership.tenantId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", username);
        detail.put("tenantId", defaultMembership.tenantId());
        auditLogRepository.append(defaultMembership.tenantId(), userAccount.id(), "auth.login.success", "user_account", userAccount.id(), detail);

        return new LoginResult(
                jwtTokenService.issueAccessToken(userAccount.id(), userAccount.username()),
                refreshToken.token(),
                jwtTokenService.getAccessTokenTtlSeconds(),
                userAccount,
                defaultMembership
        );
    }

    public RefreshResult refresh(String refreshTokenValue) {
        RefreshTokenRecord refreshToken = refreshTokenService.requireValid(refreshTokenValue);
        UserAccount userAccount = userAccountRepository.findById(refreshToken.userId())
                .filter(user -> "active".equalsIgnoreCase(user.status()))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "User not found or disabled"));

        List<TenantMembership> memberships = tenantRepository.findMembershipsByUserId(userAccount.id()).stream()
                .filter(membership -> "active".equalsIgnoreCase(membership.status()))
                .filter(membership -> "active".equalsIgnoreCase(membership.tenantStatus()))
                .toList();
        if (memberships.isEmpty()) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "User has no active tenant membership");
        }

        refreshTokenService.revoke(refreshToken.id());
        RefreshTokenService.IssuedRefreshToken rotatedToken = refreshTokenService.issue(
                userAccount.id(),
                refreshToken.tenantId() != null ? refreshToken.tenantId() : resolveDefaultMembership(userAccount, memberships).tenantId()
        );

        return new RefreshResult(
                jwtTokenService.issueAccessToken(userAccount.id(), userAccount.username()),
                rotatedToken.token(),
                jwtTokenService.getAccessTokenTtlSeconds()
        );
    }

    public boolean logout(String refreshTokenValue) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        RefreshTokenRecord refreshToken = refreshTokenService.requireValid(refreshTokenValue);
        if (refreshToken.userId() != principal.userId()) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Refresh token does not belong to current user");
        }
        refreshTokenService.revoke(refreshToken.id());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", principal.username());
        if (principal.currentTenantId() != null) {
            detail.put("tenantId", principal.currentTenantId());
        }
        auditLogRepository.append(principal.currentTenantId(), principal.userId(), "auth.logout", "user_account", principal.userId(), detail);
        return true;
    }

    public MeResult currentUser() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        return new MeResult(
                principal.userId(),
                principal.username(),
                principal.displayName(),
                principal.memberships()
        );
    }

    private void recordLoginFailure(Long userId, String username, String reason) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("username", username);
            detail.put("reason", reason);
            auditLogRepository.append(null, userId, "auth.login.failed", "user_account", userId, detail);
        } catch (Exception ignored) {
            // never block login flow on audit failure
        }
    }

    private TenantMembership resolveDefaultMembership(UserAccount userAccount, List<TenantMembership> memberships) {
        if (userAccount.defaultTenantId() != null) {
            return memberships.stream()
                    .filter(membership -> membership.tenantId() == userAccount.defaultTenantId())
                    .findFirst()
                    .orElse(memberships.getFirst());
        }
        return memberships.getFirst();
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserAccount user,
            TenantMembership defaultTenant
    ) {
    }

    public record RefreshResult(
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {
    }

    public record MeResult(
            long id,
            String username,
            String displayName,
            List<TenantMembership> tenants
    ) {
    }
}
