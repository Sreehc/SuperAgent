package com.superagent.auth.web;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.service.AuthService;
import com.superagent.common.api.ApiResponse;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    public AuthController(AuthService authService, RefreshTokenCookieManager refreshTokenCookieManager) {
        this.authService = authService;
        this.refreshTokenCookieManager = refreshTokenCookieManager;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        refreshTokenCookieManager.setRefreshToken(response, result.refreshToken());
        return ApiResponse.success(new LoginResponse(
                result.accessToken(),
                result.expiresIn(),
                new UserSummary(result.user().id(), result.user().username(), result.user().displayName()),
                new TenantSummary(result.defaultTenant().tenantId(), result.defaultTenant().tenantName(), result.defaultTenant().role().name())
        ));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.RefreshResult result = authService.refresh(requireRefreshToken(request));
        refreshTokenCookieManager.setRefreshToken(response, result.refreshToken());
        return ApiResponse.success(new RefreshResponse(result.accessToken(), result.expiresIn()));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        boolean revoked = authService.logout(requireRefreshToken(request));
        refreshTokenCookieManager.clearRefreshToken(response);
        return ApiResponse.success(new LogoutResponse(revoked));
    }

    private String requireRefreshToken(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieManager.requireRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        return refreshToken;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        AuthService.MeResult result = authService.currentUser();
        return ApiResponse.success(new MeResponse(
                result.id(),
                result.username(),
                result.displayName(),
                result.tenants().stream().map(tenant -> new TenantMembershipSummary(
                        tenant.tenantId(),
                        tenant.tenantName(),
                        tenant.role().name()
                )).toList()
        ));
    }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            long expiresIn,
            UserSummary user,
            TenantSummary defaultTenant
    ) {
    }

    public record RefreshResponse(String accessToken, long expiresIn) {
    }

    public record LogoutResponse(boolean revoked) {
    }

    public record MeResponse(
            long id,
            String username,
            String displayName,
            List<TenantMembershipSummary> tenants
    ) {
    }

    public record UserSummary(long id, String username, String displayName) {
    }

    public record TenantSummary(long id, String name, String role) {
    }

    public record TenantMembershipSummary(long id, String name, String role) {
    }
}
