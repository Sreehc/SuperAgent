package com.superagent.observability.web;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ApiResponse;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/observability/trends")
public class TrendsAdminController {

    private static final double DEFAULT_INPUT_USD_PER_1K = 0.005;
    private static final double DEFAULT_OUTPUT_USD_PER_1K = 0.015;

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TrendsAdminController(CurrentAuthenticatedUser currentAuthenticatedUser, NamedParameterJdbcTemplate jdbcTemplate) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/usage")
    public ApiResponse<UsageTrendResponse> usage(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model
    ) {
        long tenantId = requireAdminAndTenant();
        int normalizedDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(normalizedDays);

        StringBuilder sql = new StringBuilder("""
                SELECT (created_at AT TIME ZONE 'UTC')::date AS bucket,
                       provider,
                       model,
                       COALESCE(SUM(input_tokens), 0) AS input_tokens,
                       COALESCE(SUM(output_tokens), 0) AS output_tokens,
                       COUNT(*) AS call_count
                FROM model_call_trace
                WHERE tenant_id = :tenantId
                  AND created_at >= :since
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("since", since);
        if (provider != null && !provider.isBlank()) {
            sql.append(" AND provider = :provider ");
            params.addValue("provider", provider.trim());
        }
        if (model != null && !model.isBlank()) {
            sql.append(" AND model = :model ");
            params.addValue("model", model.trim());
        }
        sql.append(" GROUP BY bucket, provider, model ORDER BY bucket ASC, provider ASC, model ASC");

        List<UsageTrendPoint> points = jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            long inputTokens = rs.getLong("input_tokens");
            long outputTokens = rs.getLong("output_tokens");
            double estimatedUsd = (inputTokens * DEFAULT_INPUT_USD_PER_1K + outputTokens * DEFAULT_OUTPUT_USD_PER_1K) / 1000.0;
            return new UsageTrendPoint(
                    rs.getObject("bucket", LocalDate.class).toString(),
                    rs.getString("provider"),
                    rs.getString("model"),
                    inputTokens,
                    outputTokens,
                    rs.getLong("call_count"),
                    Math.round(estimatedUsd * 10000.0) / 10000.0
            );
        });
        long totalInput = points.stream().mapToLong(UsageTrendPoint::inputTokens).sum();
        long totalOutput = points.stream().mapToLong(UsageTrendPoint::outputTokens).sum();
        long totalCalls = points.stream().mapToLong(UsageTrendPoint::callCount).sum();
        double totalCost = points.stream().mapToDouble(UsageTrendPoint::estimatedCostUsd).sum();
        return ApiResponse.success(new UsageTrendResponse(points, totalInput, totalOutput, totalCalls,
                Math.round(totalCost * 10000.0) / 10000.0, normalizedDays));
    }

    @GetMapping("/quality")
    public ApiResponse<QualityTrendResponse> quality(
            @RequestParam(required = false, defaultValue = "30") Integer days
    ) {
        long tenantId = requireAdminAndTenant();
        int normalizedDays = normalizeDays(days);
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(normalizedDays);

        List<FeedbackTrendPoint> feedbackTrend = jdbcTemplate.query("""
                        SELECT (created_at AT TIME ZONE 'UTC')::date AS bucket,
                               SUM(CASE WHEN rating = 'up' THEN 1 ELSE 0 END) AS up_count,
                               SUM(CASE WHEN rating = 'down' THEN 1 ELSE 0 END) AS down_count,
                               SUM(CASE WHEN rating = 'correction' THEN 1 ELSE 0 END) AS correction_count,
                               COUNT(*) AS total
                        FROM conversation_feedback
                        WHERE tenant_id = :tenantId
                          AND created_at >= :since
                        GROUP BY bucket
                        ORDER BY bucket ASC
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("since", since),
                (rs, rowNum) -> new FeedbackTrendPoint(
                        rs.getObject("bucket", LocalDate.class).toString(),
                        rs.getLong("up_count"),
                        rs.getLong("down_count"),
                        rs.getLong("correction_count"),
                        rs.getLong("total")
                )
        );

        List<EvalTrendPoint> evalTrend = jdbcTemplate.query("""
                        SELECT (rc.created_at AT TIME ZONE 'UTC')::date AS bucket,
                               SUM(CASE WHEN rc.status = 'passed' THEN 1 ELSE 0 END) AS passed_count,
                               SUM(CASE WHEN rc.status IN ('failed', 'error') THEN 1 ELSE 0 END) AS failed_count,
                               COUNT(*) AS total
                        FROM agent_eval_run_case rc
                        JOIN agent_eval_run r ON r.id = rc.run_id
                        JOIN agent_eval_suite s ON s.id = r.suite_id
                        WHERE (s.tenant_id = :tenantId OR s.tenant_id IS NULL)
                          AND rc.created_at >= :since
                        GROUP BY bucket
                        ORDER BY bucket ASC
                        """,
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("since", since),
                (rs, rowNum) -> {
                    long passed = rs.getLong("passed_count");
                    long total = rs.getLong("total");
                    double passRate = total == 0 ? 0.0 : Math.round((passed * 1000.0 / total)) / 10.0;
                    return new EvalTrendPoint(
                            rs.getObject("bucket", LocalDate.class).toString(),
                            passed,
                            rs.getLong("failed_count"),
                            total,
                            passRate
                    );
                }
        );

        return ApiResponse.success(new QualityTrendResponse(feedbackTrend, evalTrend, normalizedDays));
    }

    private long requireAdminAndTenant() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Admin permission required");
        }
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return context.tenantId();
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) return 30;
        return Math.min(days, 180);
    }

    public record UsageTrendPoint(
            String date,
            String provider,
            String model,
            long inputTokens,
            long outputTokens,
            long callCount,
            double estimatedCostUsd
    ) {
    }

    public record UsageTrendResponse(
            List<UsageTrendPoint> points,
            long totalInputTokens,
            long totalOutputTokens,
            long totalCallCount,
            double totalEstimatedCostUsd,
            int days
    ) {
    }

    public record FeedbackTrendPoint(
            String date,
            long upCount,
            long downCount,
            long correctionCount,
            long total
    ) {
    }

    public record EvalTrendPoint(
            String date,
            long passedCount,
            long failedCount,
            long total,
            double passRatePercent
    ) {
    }

    public record QualityTrendResponse(
            List<FeedbackTrendPoint> feedback,
            List<EvalTrendPoint> evaluation,
            int days
    ) {
    }
}
