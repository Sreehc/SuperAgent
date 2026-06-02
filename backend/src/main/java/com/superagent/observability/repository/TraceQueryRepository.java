package com.superagent.observability.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.observability.domain.AdminTraceDetail;
import com.superagent.observability.domain.AdminTraceSummary;
import com.superagent.observability.domain.ModelCallTraceDetail;
import com.superagent.observability.domain.RerankTraceDetail;
import com.superagent.observability.domain.RetrievalTraceDetail;
import com.superagent.observability.domain.RetrievalTraceItemDetail;
import com.superagent.observability.domain.TraceStageDetail;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TraceQueryRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final RowMapper<AdminTraceSummary> SUMMARY_ROW_MAPPER = (rs, rowNum) -> new AdminTraceSummary(
            rs.getLong("exchange_id"),
            rs.getLong("session_id"),
            rs.getLong("user_id"),
            rs.getString("execution_mode"),
            rs.getString("status"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            getDurationMs(
                    rs.getObject("started_at", OffsetDateTime.class),
                    rs.getObject("finished_at", OffsetDateTime.class)
            )
    );

    private static final RowMapper<TraceStageDetail> STAGE_ROW_MAPPER = (rs, rowNum) -> {
        OffsetDateTime startedAt = rs.getObject("started_at", OffsetDateTime.class);
        OffsetDateTime finishedAt = rs.getObject("finished_at", OffsetDateTime.class);
        return new TraceStageDetail(
                rs.getLong("id"),
                rs.getString("stage_code"),
                rs.getString("status"),
                rs.getString("input_summary"),
                rs.getString("output_summary"),
                rs.getString("error_message"),
                startedAt,
                finishedAt,
                getDurationMs(startedAt, finishedAt)
        );
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TraceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long countTraces(long tenantId, String status, String executionMode, Long userId, OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM conversation_exchange ce
                JOIN (
                    SELECT id, (metadata ->> 'userId')::bigint AS created_by_user_id
                    FROM conversation_message
                ) cm ON cm.id = ce.user_message_id
                WHERE ce.tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildTraceFilters(sql, tenantId, status, executionMode, userId, from, to);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<AdminTraceSummary> listTraces(
            long tenantId,
            String status,
            String executionMode,
            Long userId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int pageSize
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT ce.id AS exchange_id,
                       ce.session_id,
                       cm.created_by_user_id AS user_id,
                       ce.execution_mode,
                       ce.status,
                       ce.started_at,
                       ce.finished_at
                FROM conversation_exchange ce
                JOIN (
                    SELECT id, session_id,
                           (metadata ->> 'userId')::bigint AS created_by_user_id
                    FROM conversation_message
                ) cm ON cm.id = ce.user_message_id
                WHERE ce.tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildTraceFilters(sql, tenantId, status, executionMode, userId, from, to)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        sql.append("""
                ORDER BY ce.started_at DESC, ce.id DESC
                LIMIT :limit OFFSET :offset
                """);
        return jdbcTemplate.query(sql.toString(), params, SUMMARY_ROW_MAPPER);
    }

    public Optional<AdminTraceDetail> findTraceDetail(long tenantId, long exchangeId) {
        List<AdminTraceSummary> summary = jdbcTemplate.query("""
                        SELECT ce.id AS exchange_id,
                               ce.session_id,
                               cm.created_by_user_id AS user_id,
                               ce.execution_mode,
                               ce.status,
                               ce.started_at,
                               ce.finished_at
                        FROM conversation_exchange ce
                        JOIN (
                            SELECT id, session_id,
                                   (metadata ->> 'userId')::bigint AS created_by_user_id
                            FROM conversation_message
                        ) cm ON cm.id = ce.user_message_id
                        WHERE ce.tenant_id = :tenantId
                          AND ce.id = :exchangeId
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("exchangeId", exchangeId),
                SUMMARY_ROW_MAPPER
        );
        if (summary.isEmpty()) {
            return Optional.empty();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("exchangeId", exchangeId);
        List<TraceStageDetail> stages = jdbcTemplate.query("""
                        SELECT id, stage_code, status, input_summary, output_summary, error_message, started_at, finished_at
                        FROM exchange_trace_stage
                        WHERE tenant_id = :tenantId
                          AND exchange_id = :exchangeId
                        ORDER BY started_at ASC NULLS LAST, id ASC
                        """,
                params,
                STAGE_ROW_MAPPER
        );
        List<ModelCallTraceDetail> modelCalls = jdbcTemplate.query("""
                        SELECT id, stage_id, provider, model, call_type, prompt_summary, output_summary,
                               input_tokens, output_tokens, latency_ms, status, error_message, metadata, created_at
                        FROM model_call_trace
                        WHERE tenant_id = :tenantId
                          AND exchange_id = :exchangeId
                        ORDER BY created_at ASC, id ASC
                        """,
                params,
                (rs, rowNum) -> new ModelCallTraceDetail(
                        rs.getLong("id"),
                        getNullableLong(rs, "stage_id"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("call_type"),
                        rs.getString("prompt_summary"),
                        rs.getString("output_summary"),
                        (Integer) rs.getObject("input_tokens"),
                        (Integer) rs.getObject("output_tokens"),
                        (Integer) rs.getObject("latency_ms"),
                        rs.getString("status"),
                        rs.getString("error_message"),
                        parseJsonMap(rs.getObject("metadata")),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );

        List<RetrievalTraceDetail> retrievals = jdbcTemplate.query("""
                        SELECT id, stage_id, sub_question_no, channel, query_text, filters, result_count,
                               selected_count, latency_ms, created_at
                        FROM retrieval_trace
                        WHERE tenant_id = :tenantId
                          AND exchange_id = :exchangeId
                        ORDER BY sub_question_no ASC, created_at ASC, id ASC
                        """,
                params,
                (rs, rowNum) -> new RetrievalTraceDetail(
                        rs.getLong("id"),
                        getNullableLong(rs, "stage_id"),
                        rs.getInt("sub_question_no"),
                        rs.getString("channel"),
                        rs.getString("query_text"),
                        parseJsonMap(rs.getObject("filters")),
                        rs.getInt("result_count"),
                        rs.getInt("selected_count"),
                        (Integer) rs.getObject("latency_ms"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        List.of()
                )
        );

        Map<Long, List<RetrievalTraceItemDetail>> retrievalItems = jdbcTemplate.query("""
                        SELECT id, retrieval_trace_id, document_id, chunk_id, rank_no, raw_score, fused_score, selected, metadata, created_at
                        FROM retrieval_trace_item
                        WHERE tenant_id = :tenantId
                          AND retrieval_trace_id IN (
                              SELECT id FROM retrieval_trace WHERE tenant_id = :tenantId AND exchange_id = :exchangeId
                          )
                        ORDER BY retrieval_trace_id ASC, rank_no ASC, id ASC
                        """,
                params,
                (rs, rowNum) -> Map.entry(
                        rs.getLong("retrieval_trace_id"),
                        new RetrievalTraceItemDetail(
                                rs.getLong("id"),
                                rs.getLong("document_id"),
                                rs.getLong("chunk_id"),
                                rs.getInt("rank_no"),
                                rs.getBigDecimal("raw_score"),
                                rs.getBigDecimal("fused_score"),
                                rs.getBoolean("selected"),
                                parseJsonMap(rs.getObject("metadata")),
                                rs.getObject("created_at", OffsetDateTime.class)
                        )
                )
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                java.util.stream.Collectors.mapping(Map.Entry::getValue, java.util.stream.Collectors.toList())
        ));

        List<RetrievalTraceDetail> retrievalDetails = retrievals.stream()
                .map(retrieval -> new RetrievalTraceDetail(
                        retrieval.id(),
                        retrieval.stageId(),
                        retrieval.subQuestionNo(),
                        retrieval.channel(),
                        retrieval.queryText(),
                        retrieval.filters(),
                        retrieval.resultCount(),
                        retrieval.selectedCount(),
                        retrieval.latencyMs(),
                        retrieval.createdAt(),
                        retrievalItems.getOrDefault(retrieval.id(), List.of())
                ))
                .toList();

        List<RerankTraceDetail> reranks = jdbcTemplate.query("""
                        SELECT id, provider, model, enabled, skipped_reason, input_count, output_count,
                               latency_ms, status, error_message, metadata, created_at
                        FROM rerank_trace
                        WHERE tenant_id = :tenantId
                          AND exchange_id = :exchangeId
                        ORDER BY created_at ASC, id ASC
                        """,
                params,
                (rs, rowNum) -> new RerankTraceDetail(
                        rs.getLong("id"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getBoolean("enabled"),
                        rs.getString("skipped_reason"),
                        rs.getInt("input_count"),
                        rs.getInt("output_count"),
                        (Integer) rs.getObject("latency_ms"),
                        rs.getString("status"),
                        rs.getString("error_message"),
                        parseJsonMap(rs.getObject("metadata")),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );

        AdminTraceSummary item = summary.getFirst();
        Map<String, Object> exchangeMeta = jdbcTemplate.queryForObject("""
                        SELECT route_reason,
                               (
                                   SELECT ar.id
                                   FROM agent_run ar
                                   WHERE ar.tenant_id = ce.tenant_id
                                     AND ar.exchange_id = ce.id
                                   ORDER BY ar.created_at DESC, ar.id DESC
                                   LIMIT 1
                               ) AS agent_run_id,
                               (
                                   SELECT ar.status
                                   FROM agent_run ar
                                   WHERE ar.tenant_id = ce.tenant_id
                                     AND ar.exchange_id = ce.id
                                   ORDER BY ar.created_at DESC, ar.id DESC
                                   LIMIT 1
                               ) AS agent_run_status
                        FROM conversation_exchange ce
                        WHERE ce.tenant_id = :tenantId
                          AND ce.id = :exchangeId
                        """,
                params,
                (rs, rowNum) -> {
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("routeReason", rs.getString("route_reason"));
                    metadata.put("agentRunId", getNullableLong(rs, "agent_run_id"));
                    metadata.put("agentRunStatus", rs.getString("agent_run_status"));
                    return metadata;
                }
        );
        return Optional.of(new AdminTraceDetail(
                item.exchangeId(),
                item.sessionId(),
                item.userId(),
                item.executionMode(),
                item.status(),
                (String) exchangeMeta.get("routeReason"),
                (Long) exchangeMeta.get("agentRunId"),
                (String) exchangeMeta.get("agentRunStatus"),
                item.startedAt(),
                item.finishedAt(),
                item.durationMs(),
                stages,
                modelCalls,
                retrievalDetails,
                reranks
        ));
    }

    public long countRetrievals(long tenantId, Long exchangeId, String channel) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM retrieval_trace
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildRetrievalFilters(sql, tenantId, exchangeId, channel);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<RetrievalTraceDetail> listRetrievals(long tenantId, Long exchangeId, String channel, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, stage_id, sub_question_no, channel, query_text, filters, result_count,
                       selected_count, latency_ms, created_at
                FROM retrieval_trace
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildRetrievalFilters(sql, tenantId, exchangeId, channel)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        sql.append("""
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """);
        List<RetrievalTraceDetail> retrievals = jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new RetrievalTraceDetail(
                rs.getLong("id"),
                getNullableLong(rs, "stage_id"),
                rs.getInt("sub_question_no"),
                rs.getString("channel"),
                rs.getString("query_text"),
                parseJsonMap(rs.getObject("filters")),
                rs.getInt("result_count"),
                rs.getInt("selected_count"),
                (Integer) rs.getObject("latency_ms"),
                rs.getObject("created_at", OffsetDateTime.class),
                List.of()
        ));
        return retrievals.stream()
                .map(retrieval -> new RetrievalTraceDetail(
                        retrieval.id(),
                        retrieval.stageId(),
                        retrieval.subQuestionNo(),
                        retrieval.channel(),
                        retrieval.queryText(),
                        retrieval.filters(),
                        retrieval.resultCount(),
                        retrieval.selectedCount(),
                        retrieval.latencyMs(),
                        retrieval.createdAt(),
                        loadRetrievalItemsForTraceId(tenantId, retrieval.id())
                ))
                .toList();
    }

    public long countReranks(long tenantId, Long exchangeId, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM rerank_trace
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildRerankFilters(sql, tenantId, exchangeId, status);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    public List<RerankTraceDetail> listReranks(long tenantId, Long exchangeId, String status, int page, int pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, provider, model, enabled, skipped_reason, input_count, output_count,
                       latency_ms, status, error_message, metadata, created_at
                FROM rerank_trace
                WHERE tenant_id = :tenantId
                """);
        MapSqlParameterSource params = buildRerankFilters(sql, tenantId, exchangeId, status)
                .addValue("limit", pageSize)
                .addValue("offset", Math.max(page - 1, 0) * pageSize);
        sql.append("""
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """);
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new RerankTraceDetail(
                rs.getLong("id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getBoolean("enabled"),
                rs.getString("skipped_reason"),
                rs.getInt("input_count"),
                rs.getInt("output_count"),
                (Integer) rs.getObject("latency_ms"),
                rs.getString("status"),
                rs.getString("error_message"),
                parseJsonMap(rs.getObject("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    private MapSqlParameterSource buildTraceFilters(
            StringBuilder sql,
            long tenantId,
            String status,
            String executionMode,
            Long userId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND ce.status = :status");
            params.addValue("status", status.trim());
        }
        if (executionMode != null && !executionMode.isBlank()) {
            sql.append(" AND ce.execution_mode = :executionMode");
            params.addValue("executionMode", executionMode.trim());
        }
        if (userId != null) {
            sql.append(" AND (cm.created_by_user_id = :userId)");
            params.addValue("userId", userId);
        }
        if (from != null) {
            sql.append(" AND ce.started_at >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND ce.started_at <= :to");
            params.addValue("to", to);
        }
        return params;
    }

    private MapSqlParameterSource buildRetrievalFilters(StringBuilder sql, long tenantId, Long exchangeId, String channel) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (exchangeId != null) {
            sql.append(" AND exchange_id = :exchangeId ");
            params.addValue("exchangeId", exchangeId);
        }
        if (channel != null && !channel.isBlank()) {
            sql.append(" AND channel = :channel ");
            params.addValue("channel", channel.trim());
        }
        return params;
    }

    private MapSqlParameterSource buildRerankFilters(StringBuilder sql, long tenantId, Long exchangeId, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (exchangeId != null) {
            sql.append(" AND exchange_id = :exchangeId ");
            params.addValue("exchangeId", exchangeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status ");
            params.addValue("status", status.trim());
        }
        return params;
    }

    private List<RetrievalTraceItemDetail> loadRetrievalItemsForTraceId(long tenantId, long retrievalTraceId) {
        return jdbcTemplate.query("""
                        SELECT id, document_id, chunk_id, rank_no, raw_score, fused_score, selected, metadata, created_at
                        FROM retrieval_trace_item
                        WHERE tenant_id = :tenantId
                          AND retrieval_trace_id = :retrievalTraceId
                        ORDER BY rank_no ASC, id ASC
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("retrievalTraceId", retrievalTraceId),
                (rs, rowNum) -> new RetrievalTraceItemDetail(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getLong("chunk_id"),
                        rs.getInt("rank_no"),
                        rs.getBigDecimal("raw_score"),
                        rs.getBigDecimal("fused_score"),
                        rs.getBoolean("selected"),
                        parseJsonMap(rs.getObject("metadata")),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );
    }

    private Map<String, Object> parseJsonMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        String json = value.toString();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", json);
        }
    }

    private static Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static long getDurationMs(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return 0L;
        }
        return ChronoUnit.MILLIS.between(startedAt, finishedAt);
    }
}
