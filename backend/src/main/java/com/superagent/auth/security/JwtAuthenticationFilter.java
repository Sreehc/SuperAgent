package com.superagent.auth.security;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.UserAccount;
import com.superagent.auth.repository.TenantRepository;
import com.superagent.auth.repository.UserAccountRepository;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final JwtTokenService jwtTokenService;
    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            JwtTokenService.AccessTokenClaims claims = jwtTokenService.parseAccessToken(authorization.substring(7));
            UserAccount userAccount = userAccountRepository.findById(claims.userId())
                    .filter(user -> "active".equalsIgnoreCase(user.status()))
                    .orElseThrow(() -> new JwtAuthenticationException("User not found or disabled"));

            List<TenantMembership> memberships = tenantRepository.findMembershipsByUserId(userAccount.id()).stream()
                    .filter(membership -> "active".equalsIgnoreCase(membership.status()))
                    .filter(membership -> "active".equalsIgnoreCase(membership.tenantStatus()))
                    .toList();

            Long currentTenantId = resolveTenantId(request, userAccount, memberships);
            Optional<TenantMembership> currentMembership = currentTenantId == null
                    ? Optional.empty()
                    : memberships.stream().filter(membership -> membership.tenantId() == currentTenantId).findFirst();

            if (currentTenantId != null && currentMembership.isEmpty()) {
                throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant access denied");
            }

            AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                    userAccount.id(),
                    userAccount.username(),
                    userAccount.displayName(),
                    userAccount.defaultTenantId(),
                    memberships,
                    currentMembership.map(TenantMembership::tenantId).orElse(null),
                    currentMembership.map(TenantMembership::role).orElse(null)
            );

            List<SimpleGrantedAuthority> authorities = currentMembership
                    .map(membership -> List.of(new SimpleGrantedAuthority(membership.role().authority())))
                    .orElseGet(List::of);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities)
            );
            currentMembership.ifPresent(membership -> TenantContextHolder.set(new TenantContext(membership.tenantId(), membership.role())));

            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            handlerExceptionResolver.resolveException(request, response, null, exception);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private Long resolveTenantId(
            HttpServletRequest request,
            UserAccount userAccount,
            List<TenantMembership> memberships
    ) {
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                return Long.parseLong(tenantHeader);
            } catch (NumberFormatException exception) {
                throw new AppException(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "Invalid X-Tenant-Id header");
            }
        }

        if (userAccount.defaultTenantId() != null) {
            return userAccount.defaultTenantId();
        }

        return memberships.isEmpty() ? null : memberships.getFirst().tenantId();
    }
}
