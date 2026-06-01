package com.superagent.auth.repository;

import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.TenantRole;
import java.time.OffsetDateTime;
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
}
