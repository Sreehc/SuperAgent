package com.superagent.auth.web;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.service.AuthService;
import com.superagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return ApiResponse.success(new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                new UserSummary(result.user().id(), result.user().username(), result.user().displayName()),
                new TenantSummary(result.defaultTenant().tenantId(), result.defaultTenant().tenantName(), result.defaultTenant().role().name())
        ));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.RefreshResult result = authService.refresh(request.refreshToken());
        return ApiResponse.success(new RefreshResponse(result.accessToken(), result.refreshToken(), result.expiresIn()));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request) {
        return ApiResponse.success(new LogoutResponse(authService.logout(request.refreshToken())));
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

    public record RefreshRequest(@NotBlank(message = "refreshToken is required") String refreshToken) {
    }

    public record LogoutRequest(@NotBlank(message = "refreshToken is required") String refreshToken) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserSummary user,
            TenantSummary defaultTenant
    ) {
    }

    public record RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
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
