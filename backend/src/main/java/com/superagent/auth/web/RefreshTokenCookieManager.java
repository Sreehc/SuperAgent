package com.superagent.auth.web;

import com.superagent.infra.config.SuperAgentProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

    private final SuperAgentProperties.Security securityProperties;

    public RefreshTokenCookieManager(SuperAgentProperties properties) {
        this.securityProperties = properties.getSecurity();
    }

    public String requireRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> securityProperties.getRefreshCookieName().equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public void setRefreshToken(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(securityProperties.getRefreshTokenTtlSeconds()))
                .build()
                .toString());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(securityProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(securityProperties.getRefreshCookieSecure()))
                .sameSite(securityProperties.getRefreshCookieSameSite())
                .path(securityProperties.getRefreshCookiePath());
        String domain = securityProperties.getRefreshCookieDomain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain.trim());
        }
        return builder;
    }
}
