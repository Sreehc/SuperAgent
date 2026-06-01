package com.superagent.auth.security;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;

    public JwtTokenService(SuperAgentProperties properties) {
        String jwtSecret = properties.getSecurity().getJwtSecret();
        if (jwtSecret.length() < 32) {
            throw new AppException(ErrorCode.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR, "JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = properties.getSecurity().getAccessTokenTtlSeconds();
    }

    public String issueAccessToken(long userId, String username) {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(Date.from(issuedAt.toInstant()))
                .expiration(Date.from(issuedAt.plusSeconds(accessTokenTtlSeconds).toInstant()))
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type", String.class))) {
                throw new JwtAuthenticationException("Unsupported token type");
            }

            return new AccessTokenClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class)
            );
        } catch (ExpiredJwtException exception) {
            throw new JwtAuthenticationException("Access token expired");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new JwtAuthenticationException("Invalid access token");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public record AccessTokenClaims(long userId, String username) {
    }
}
