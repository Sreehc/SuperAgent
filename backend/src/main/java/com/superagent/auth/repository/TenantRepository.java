package com.superagent.auth.repository;

import com.superagent.auth.domain.Tenant;
import com.superagent.auth.domain.TenantMembership;
import com.superagent.auth.domain.TenantRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private static final RowMapper<Tenant> TENANT_ROW_MAPPER = (rs, rowNum) -> new Tenant(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("code"),
            rs.getString("status")
    );

    private static final RowMapper<TenantMembership> MEMBERSHIP_ROW_MAPPER = (rs, rowNum) -> new TenantMembership(
            rs.getLong("tenant_id"),
            rs.getString("tenant_name"),
            rs.getString("tenant_code"),
            rs.getString("tenant_status"),
            rs.getLong("user_id"),
            TenantRole.valueOf(rs.getString("role")),
            rs.getString("membership_status"),
            rs.getObject("joined_at", OffsetDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TenantRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Tenant> findById(long id) {
        return jdbcTemplate.query(
                "SELECT id, name, code, status FROM tenant WHERE id = :id",
                Map.of("id", id),
                TENANT_ROW_MAPPER
        ).stream().findFirst();
    }

    public Optional<Tenant> findByCode(String code) {
        return jdbcTemplate.query(
                "SELECT id, name, code, status FROM tenant WHERE code = :code",
                Map.of("code", code),
                TENANT_ROW_MAPPER
        ).stream().findFirst();
    }

    public long create(String name, String code, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO tenant (name, code, status)
                        VALUES (:name, :code, :status)
                        RETURNING id
                        """,
                Map.of("name", name, "code", code, "status", status),
                Long.class
        );
    }

    public List<TenantMembership> findMembershipsByUserId(long userId) {
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
                        WHERE tm.user_id = :userId
                        ORDER BY tm.tenant_id
                        """,
                Map.of("userId", userId),
                MEMBERSHIP_ROW_MAPPER
        );
    }

    public List<MemberView> findMembers(long tenantId) {
        return jdbcTemplate.query("""
                        SELECT
                            ua.id AS user_id,
                            ua.username,
                            ua.display_name,
                            tm.role,
                            tm.status,
                            tm.joined_at
                        FROM tenant_member tm
                        JOIN user_account ua ON ua.id = tm.user_id
                        WHERE tm.tenant_id = :tenantId
                        ORDER BY tm.joined_at ASC
                        """,
                Map.of("tenantId", tenantId),
                (rs, rowNum) -> new MemberView(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        TenantRole.valueOf(rs.getString("role")),
                        rs.getString("status"),
                        rs.getObject("joined_at", OffsetDateTime.class)
                )
        );
    }

    public record MemberView(
            long userId,
            String username,
            String displayName,
            TenantRole role,
            String status,
            OffsetDateTime joinedAt
    ) {
    }
}
