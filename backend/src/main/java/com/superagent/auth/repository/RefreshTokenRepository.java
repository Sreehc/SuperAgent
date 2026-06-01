package com.superagent.auth.repository;

import com.superagent.auth.domain.RefreshTokenRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private static final RowMapper<RefreshTokenRecord> REFRESH_TOKEN_ROW_MAPPER = (rs, rowNum) -> new RefreshTokenRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getObject("tenant_id", Long.class),
            rs.getString("token_hash"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("revoked_at", OffsetDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RefreshTokenRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long userId, Long tenantId, String tokenHash, OffsetDateTime expiresAt) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO refresh_token (user_id, tenant_id, token_hash, expires_at)
                        VALUES (:userId, :tenantId, :tokenHash, :expiresAt)
                        RETURNING id
                        """,
                Map.of(
                        "userId", userId,
                        "tenantId", tenantId,
                        "tokenHash", tokenHash,
                        "expiresAt", expiresAt
                ),
                Long.class
        );
    }

    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, tenant_id, token_hash, expires_at, revoked_at
                        FROM refresh_token
                        WHERE token_hash = :tokenHash
                        """,
                Map.of("tokenHash", tokenHash),
                REFRESH_TOKEN_ROW_MAPPER
        ).stream().findFirst();
    }

    public void revokeById(long id) {
        jdbcTemplate.update("""
                        UPDATE refresh_token
                        SET revoked_at = NOW()
                        WHERE id = :id AND revoked_at IS NULL
                        """,
                Map.of("id", id)
        );
    }

    public void deleteAll() {
        jdbcTemplate.getJdbcTemplate().execute("DELETE FROM refresh_token");
    }
}
