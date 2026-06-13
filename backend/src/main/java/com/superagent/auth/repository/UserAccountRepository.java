package com.superagent.auth.repository;

import com.superagent.auth.domain.UserAccount;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private static final RowMapper<UserAccount> USER_ROW_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("email"),
            rs.getString("status"),
            rs.getObject("default_tenant_id", Long.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserAccountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT id, username, password_hash, display_name, email, status, default_tenant_id
                        FROM user_account
                        WHERE username = :username AND deleted_at IS NULL
                        """,
                Map.of("username", username),
                USER_ROW_MAPPER
        ).stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, username, password_hash, display_name, email, status, default_tenant_id
                        FROM user_account
                        WHERE id = :id AND deleted_at IS NULL
                        """,
                Map.of("id", id),
                USER_ROW_MAPPER
        ).stream().findFirst();
    }

    public long create(String username, String passwordHash, String displayName, String email, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO user_account (username, password_hash, display_name, email, status)
                        VALUES (:username, :passwordHash, :displayName, :email, :status)
                        RETURNING id
                        """,
                Map.of(
                        "username", username,
                        "passwordHash", passwordHash,
                        "displayName", displayName,
                        "email", email,
                        "status", status
                ),
                Long.class
        );
    }

    public void updateDefaultTenantId(long userId, long tenantId) {
        jdbcTemplate.update("""
                        UPDATE user_account
                        SET default_tenant_id = :tenantId
                        WHERE id = :userId
                        """,
                Map.of("tenantId", tenantId, "userId", userId)
        );
    }

    public void clearDefaultTenantId(long userId, long tenantId) {
        jdbcTemplate.update("""
                        UPDATE user_account
                        SET default_tenant_id = NULL
                        WHERE id = :userId AND default_tenant_id = :tenantId
                        """,
                Map.of("tenantId", tenantId, "userId", userId)
        );
    }

    public void setDefaultTenantId(long userId, Long tenantId) {
        jdbcTemplate.update("""
                        UPDATE user_account
                        SET default_tenant_id = :tenantId
                        WHERE id = :userId
                        """,
                Map.of("tenantId", tenantId, "userId", userId)
        );
    }
}
