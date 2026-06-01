package com.superagent.auth.security;

import com.superagent.auth.domain.RefreshTokenRecord;
import com.superagent.auth.repository.RefreshTokenRepository;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.infra.config.SuperAgentProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            SuperAgentProperties properties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlSeconds = properties.getSecurity().getRefreshTokenTtlSeconds();
    }

    public IssuedRefreshToken issue(long userId, Long tenantId) {
        String token = "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        String tokenHash = hash(token);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(refreshTokenTtlSeconds);
        long tokenId = refreshTokenRepository.create(userId, tenantId, tokenHash, expiresAt);
        return new IssuedRefreshToken(tokenId, token, expiresAt);
    }

    public RefreshTokenRecord requireValid(String refreshToken) {
        RefreshTokenRecord record = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (record.isRevoked()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token revoked");
        }
        if (record.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        return record;
    }

    public void revoke(long tokenId) {
        refreshTokenRepository.revokeById(tokenId);
    }

    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(record -> refreshTokenRepository.revokeById(record.id()));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record IssuedRefreshToken(long tokenId, String token, OffsetDateTime expiresAt) {
    }
}
