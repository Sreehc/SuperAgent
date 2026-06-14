package com.superagent.auth.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantInvitationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TenantInvitationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(
            long tenantId,
            String email,
            String role,
            String tokenHash,
            long invitedBy,
            OffsetDateTime expiresAt
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO tenant_invitation (tenant_id, email, role, token_hash, invited_by, expires_at)
                        VALUES (:tenantId, :email, :role, :tokenHash, :invitedBy, :expiresAt)
                        RETURNING id
                        """,
                Map.of(
                        "tenantId", tenantId,
                        "email", email,
                        "role", role,
                        "tokenHash", tokenHash,
                        "invitedBy", invitedBy,
                        "expiresAt", expiresAt
                ),
                Long.class
        );
    }

    public Optional<InvitationRecord> findById(long tenantId, long id) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, email, role, status, token_hash, invited_by, expires_at,
                               accepted_at, accepted_by, created_at, updated_at
                        FROM tenant_invitation
                        WHERE tenant_id = :tenantId AND id = :id
                        """,
                Map.of("tenantId", tenantId, "id", id),
                this::map
        ).stream().findFirst();
    }

    public Optional<InvitationRecord> findByTokenHash(String tokenHash) {
        return jdbcTemplate.query("""
                        SELECT id, tenant_id, email, role, status, token_hash, invited_by, expires_at,
                               accepted_at, accepted_by, created_at, updated_at
                        FROM tenant_invitation
                        WHERE token_hash = :tokenHash
                        """,
                Map.of("tokenHash", tokenHash),
                this::map
        ).stream().findFirst();
    }

    public boolean hasPendingForEmail(long tenantId, String email) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM tenant_invitation
                        WHERE tenant_id = :tenantId
                          AND LOWER(email) = LOWER(:email)
                          AND status = 'pending'
                        """,
                Map.of("tenantId", tenantId, "email", email),
                Integer.class
        );
        return count != null && count > 0;
    }

    public List<InvitationRecord> listByTenant(long tenantId, String status, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, email, role, status, token_hash, invited_by, expires_at,
                       accepted_at, accepted_by, created_at, updated_at
                FROM tenant_invitation
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> map(rs, rowNum));
    }

    public boolean updateStatus(long tenantId, long invitationId, String status) {
        return jdbcTemplate.update("""
                        UPDATE tenant_invitation
                        SET status = :status, updated_at = NOW()
                        WHERE tenant_id = :tenantId AND id = :id
                        """,
                Map.of("tenantId", tenantId, "id", invitationId, "status", status)
        ) > 0;
    }

    public boolean markAccepted(long invitationId, long userId) {
        return jdbcTemplate.update("""
                        UPDATE tenant_invitation
                        SET status = 'accepted', accepted_at = NOW(), accepted_by = :userId, updated_at = NOW()
                        WHERE id = :id AND status = 'pending'
                        """,
                Map.of("id", invitationId, "userId", userId)
        ) > 0;
    }

    private InvitationRecord map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InvitationRecord(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getString("token_hash"),
                rs.getLong("invited_by"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("accepted_at", OffsetDateTime.class),
                (Long) rs.getObject("accepted_by"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    public record InvitationRecord(
            long id,
            long tenantId,
            String email,
            String role,
            String status,
            String tokenHash,
            long invitedBy,
            OffsetDateTime expiresAt,
            OffsetDateTime acceptedAt,
            Long acceptedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
