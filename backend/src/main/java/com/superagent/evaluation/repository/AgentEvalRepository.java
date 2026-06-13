package com.superagent.evaluation.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.evaluation.domain.AgentEvalCase;
import com.superagent.evaluation.domain.AgentEvalRun;
import com.superagent.evaluation.domain.AgentEvalSuite;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AgentEvalRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentEvalRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long countSuites(long tenantId, String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM agent_eval_suite aes
                WHERE (aes.tenant_id = :tenantId OR aes.tenant_id IS NULL)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        appendKeywordFilter(sql, params, keyword);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<AgentEvalSuite> listSuites(long tenantId, String keyword, int page, int pageSize) {
        StringBuilder sql = suiteSelectSql("""
                WHERE (aes.tenant_id = :tenantId OR aes.tenant_id IS NULL)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        appendKeywordFilter(sql, params, keyword);
        sql.append(" ORDER BY aes.updated_at DESC, aes.id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> toSuite(rs));
    }

    public Optional<AgentEvalSuite> findSuite(long tenantId, long suiteId) {
        return jdbcTemplate.query(
                suiteSelectSql("""
                        WHERE aes.id = :suiteId
                          AND (aes.tenant_id = :tenantId OR aes.tenant_id IS NULL)
                        """).toString(),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteId", suiteId),
                (rs, rowNum) -> toSuite(rs)
        ).stream().findFirst();
    }

    public AgentEvalSuite createSuite(long tenantId, String suiteKey, String name, String description) {
        long id = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_eval_suite (tenant_id, suite_key, name, description)
                        VALUES (:tenantId, :suiteKey, :name, :description)
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteKey", suiteKey)
                        .addValue("name", name)
                        .addValue("description", description),
                Long.class
        );
        return findSuite(tenantId, id).orElseThrow();
    }

    public AgentEvalSuite updateSuite(long tenantId, long suiteId, String name, String description) {
        jdbcTemplate.update("""
                        UPDATE agent_eval_suite
                        SET name = COALESCE(:name, name),
                            description = :description
                        WHERE id = :suiteId
                          AND tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteId", suiteId)
                        .addValue("name", blankToNull(name))
                        .addValue("description", blankToNull(description))
        );
        return findSuite(tenantId, suiteId).orElseThrow();
    }

    public List<AgentEvalCase> listCases(long tenantId, long suiteId) {
        return jdbcTemplate.query("""
                        SELECT c.id, c.suite_id, c.case_key, c.input_json::text AS input_json, c.expected_json::text AS expected_json,
                               c.created_at, c.updated_at
                        FROM agent_eval_case c
                        JOIN agent_eval_suite s ON s.id = c.suite_id
                        WHERE c.suite_id = :suiteId
                          AND (s.tenant_id = :tenantId OR s.tenant_id IS NULL)
                        ORDER BY c.case_key ASC, c.id ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteId", suiteId),
                (rs, rowNum) -> toCase(rs)
        );
    }

    public Optional<AgentEvalCase> findCase(long tenantId, long caseId) {
        return jdbcTemplate.query("""
                        SELECT c.id, c.suite_id, c.case_key, c.input_json::text AS input_json, c.expected_json::text AS expected_json,
                               c.created_at, c.updated_at
                        FROM agent_eval_case c
                        JOIN agent_eval_suite s ON s.id = c.suite_id
                        WHERE c.id = :caseId
                          AND s.tenant_id = :tenantId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("caseId", caseId),
                (rs, rowNum) -> toCase(rs)
        ).stream().findFirst();
    }

    public AgentEvalCase createCase(long tenantId, long suiteId, String caseKey, Map<String, Object> input, Map<String, Object> expected) {
        long id = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_eval_case (suite_id, case_key, input_json, expected_json)
                        SELECT id, :caseKey, CAST(:inputJson AS jsonb), CAST(:expectedJson AS jsonb)
                        FROM agent_eval_suite
                        WHERE id = :suiteId
                          AND tenant_id = :tenantId
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteId", suiteId)
                        .addValue("caseKey", caseKey)
                        .addValue("inputJson", writeJson(input))
                        .addValue("expectedJson", writeJson(expected)),
                Long.class
        );
        return findCase(tenantId, id).orElseThrow();
    }

    public AgentEvalCase updateCase(long tenantId, long caseId, String caseKey, Map<String, Object> input, Map<String, Object> expected) {
        jdbcTemplate.update("""
                        UPDATE agent_eval_case c
                        SET case_key = COALESCE(:caseKey, c.case_key),
                            input_json = CAST(:inputJson AS jsonb),
                            expected_json = CAST(:expectedJson AS jsonb)
                        FROM agent_eval_suite s
                        WHERE s.id = c.suite_id
                          AND s.tenant_id = :tenantId
                          AND c.id = :caseId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("caseId", caseId)
                        .addValue("caseKey", blankToNull(caseKey))
                        .addValue("inputJson", writeJson(input))
                        .addValue("expectedJson", writeJson(expected))
        );
        return findCase(tenantId, caseId).orElseThrow();
    }

    public boolean deleteCase(long tenantId, long caseId) {
        return jdbcTemplate.update("""
                        DELETE FROM agent_eval_case c
                        USING agent_eval_suite s
                        WHERE s.id = c.suite_id
                          AND s.tenant_id = :tenantId
                          AND c.id = :caseId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("caseId", caseId)
        ) > 0;
    }

    public long countRuns(long tenantId, Long suiteId, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM agent_eval_run r
                JOIN agent_eval_suite s ON s.id = r.suite_id
                WHERE (s.tenant_id = :tenantId OR s.tenant_id IS NULL)
                """);
        MapSqlParameterSource params = buildRunFilters(sql, tenantId, suiteId, status);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<AgentEvalRun> listRuns(long tenantId, Long suiteId, String status, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.suite_id, s.suite_key, r.status, r.passed_count, r.failed_count,
                       r.report_json::text AS report_json, r.created_at, r.updated_at, r.finished_at
                FROM agent_eval_run r
                JOIN agent_eval_suite s ON s.id = r.suite_id
                WHERE (s.tenant_id = :tenantId OR s.tenant_id IS NULL)
                """);
        MapSqlParameterSource params = buildRunFilters(sql, tenantId, suiteId, status)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        sql.append(" ORDER BY r.created_at DESC, r.id DESC LIMIT :limit OFFSET :offset");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> toRun(rs));
    }

    public Optional<AgentEvalRun> findRun(long tenantId, long runId) {
        return jdbcTemplate.query("""
                        SELECT r.id, r.suite_id, s.suite_key, r.status, r.passed_count, r.failed_count,
                               r.report_json::text AS report_json, r.created_at, r.updated_at, r.finished_at
                        FROM agent_eval_run r
                        JOIN agent_eval_suite s ON s.id = r.suite_id
                        WHERE r.id = :runId
                          AND (s.tenant_id = :tenantId OR s.tenant_id IS NULL)
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("runId", runId),
                (rs, rowNum) -> toRun(rs)
        ).stream().findFirst();
    }

    public AgentEvalRun createRun(
            long tenantId,
            long suiteId,
            String status,
            int passedCount,
            int failedCount,
            Map<String, Object> report
    ) {
        long id = jdbcTemplate.queryForObject("""
                        INSERT INTO agent_eval_run (suite_id, status, passed_count, failed_count, report_json, finished_at)
                        SELECT id, :status, :passedCount, :failedCount, CAST(:reportJson AS jsonb),
                               CASE WHEN :status IN ('success', 'failed') THEN NOW() ELSE NULL END
                        FROM agent_eval_suite
                        WHERE id = :suiteId
                          AND tenant_id = :tenantId
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("suiteId", suiteId)
                        .addValue("status", status)
                        .addValue("passedCount", passedCount)
                        .addValue("failedCount", failedCount)
                        .addValue("reportJson", writeJson(report)),
                Long.class
        );
        return findRun(tenantId, id).orElseThrow();
    }

    private StringBuilder suiteSelectSql(String whereClause) {
        return new StringBuilder("""
                SELECT aes.id,
                       aes.tenant_id,
                       aes.suite_key,
                       aes.name,
                       aes.description,
                       COALESCE(cases.case_count, 0) AS case_count,
                       COALESCE(runs.run_count, 0) AS run_count,
                       aes.created_at,
                       aes.updated_at
                FROM agent_eval_suite aes
                LEFT JOIN (
                    SELECT suite_id, COUNT(*) AS case_count
                    FROM agent_eval_case
                    GROUP BY suite_id
                ) cases ON cases.suite_id = aes.id
                LEFT JOIN (
                    SELECT suite_id, COUNT(*) AS run_count
                    FROM agent_eval_run
                    GROUP BY suite_id
                ) runs ON runs.suite_id = aes.id
                """).append(whereClause);
    }

    private void appendKeywordFilter(StringBuilder sql, MapSqlParameterSource params, String keyword) {
        String normalized = blankToNull(keyword);
        if (normalized != null) {
            sql.append(" AND (LOWER(aes.suite_key) LIKE :keyword OR LOWER(aes.name) LIKE :keyword) ");
            params.addValue("keyword", "%" + normalized.toLowerCase() + "%");
        }
    }

    private MapSqlParameterSource buildRunFilters(StringBuilder sql, long tenantId, Long suiteId, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (suiteId != null) {
            sql.append(" AND r.suite_id = :suiteId ");
            params.addValue("suiteId", suiteId);
        }
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null) {
            sql.append(" AND r.status = :status ");
            params.addValue("status", normalizedStatus);
        }
        return params;
    }

    private AgentEvalSuite toSuite(ResultSet rs) throws java.sql.SQLException {
        return new AgentEvalSuite(
                rs.getLong("id"),
                getNullableLong(rs, "tenant_id"),
                rs.getString("suite_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("case_count"),
                rs.getInt("run_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private AgentEvalCase toCase(ResultSet rs) throws java.sql.SQLException {
        return new AgentEvalCase(
                rs.getLong("id"),
                rs.getLong("suite_id"),
                rs.getString("case_key"),
                parseMap(rs.getString("input_json")),
                parseMap(rs.getString("expected_json")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private AgentEvalRun toRun(ResultSet rs) throws java.sql.SQLException {
        return new AgentEvalRun(
                rs.getLong("id"),
                rs.getLong("suite_id"),
                rs.getString("suite_key"),
                rs.getString("status"),
                rs.getInt("passed_count"),
                rs.getInt("failed_count"),
                parseMap(rs.getString("report_json")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize eval JSON", exception);
        }
    }

    private Long getNullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
