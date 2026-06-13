package com.superagent.auth.repository;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.TenantRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantMemberRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TenantMemberRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long tenantId, long userId, TenantRole role, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO tenant_member (tenant_id, user_id, role, status)
                        VALUES (:tenantId, :userId, :role, :status)
                        RETURNING id
                        """,
                Map.of(
                        "tenantId", tenantId,
                        "userId", userId,
                        "role", role.name(),
                        "status", status
                ),
                Long.class
        );
    }

    public Optional<TenantMembership> findByUserIdAndTenantId(long userId, long tenantId) {
        return jdbcTemplate.query("""
                        SELECT
                            tm.tenant_id,
                            t.name AS tenant_name,
                            t.code AS tenant_code,
                            t.status AS tenant_status,
                            tm.user_id,
                            tm.role,
                            tm.status AS membership_status,
                            tm.joined_at
                        FROM tenant_member tm
                        JOIN tenant t ON t.id = tm.tenant_id
                        WHERE tm.user_id = :userId AND tm.tenant_id = :tenantId
                        """,
                Map.of("userId", userId, "tenantId", tenantId),
                (rs, rowNum) -> new TenantMembership(
                        rs.getLong("tenant_id"),
                        rs.getString("tenant_name"),
                        rs.getString("tenant_code"),
                        rs.getString("tenant_status"),
                        rs.getLong("user_id"),
                        TenantRole.valueOf(rs.getString("role")),
                        rs.getString("membership_status"),
                        rs.getObject("joined_at", OffsetDateTime.class)
                )
        ).stream().findFirst();
    }

    public boolean exists(long tenantId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM tenant_member
                        WHERE tenant_id = :tenantId AND user_id = :userId
                        """,
                Map.of("tenantId", tenantId, "userId", userId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public int countActiveOwners(long tenantId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM tenant_member
                        WHERE tenant_id = :tenantId
                          AND role = 'OWNER'
                          AND status = 'active'
                        """,
                Map.of("tenantId", tenantId),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public void updateRole(long tenantId, long userId, TenantRole role) {
        jdbcTemplate.update("""
                        UPDATE tenant_member
                        SET role = :role
                        WHERE tenant_id = :tenantId AND user_id = :userId
                        """,
                Map.of(
                        "tenantId", tenantId,
                        "userId", userId,
                        "role", role.name()
                )
        );
    }

    public void updateStatus(long tenantId, long userId, String status) {
        jdbcTemplate.update("""
                        UPDATE tenant_member
                        SET status = :status
                        WHERE tenant_id = :tenantId AND user_id = :userId
                        """,
                Map.of(
                        "tenantId", tenantId,
                        "userId", userId,
                        "status", status
                )
        );
    }

    public void delete(long tenantId, long userId) {
        jdbcTemplate.update("""
                        DELETE FROM tenant_member
                        WHERE tenant_id = :tenantId AND user_id = :userId
                        """,
                Map.of("tenantId", tenantId, "userId", userId)
        );
    }

    public List<Long> findTenantIdsByUserId(long userId) {
        return jdbcTemplate.queryForList("""
                        SELECT tenant_id
                        FROM tenant_member
                        WHERE user_id = :userId
                        ORDER BY joined_at ASC
                        """,
                Map.of("userId", userId),
                Long.class
        );
    }
}
