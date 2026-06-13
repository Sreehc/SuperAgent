package com.superagent.observability.web;

import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import com.superagent.observability.domain.AdminTraceDetail;
import com.superagent.observability.domain.AdminTraceSummary;
import com.superagent.observability.service.TraceAdminService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/traces")
public class TraceAdminController {

    private final TraceAdminService traceAdminService;

    public TraceAdminController(TraceAdminService traceAdminService) {
        this.traceAdminService = traceAdminService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<TraceSummaryItem>> listTraces(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String executionMode,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        ConversationService.PagedResult<AdminTraceSummary> result = traceAdminService.listTraces(
                page,
                pageSize,
                status,
                executionMode,
                userId,
                sessionId,
                toolId,
                from,
                to
        );
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toTraceSummaryItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    @GetMapping("/{exchangeId}")
    public ApiResponse<AdminTraceDetail> getTraceDetail(@PathVariable long exchangeId) {
        return ApiResponse.success(traceAdminService.getTraceDetail(exchangeId));
    }

    private TraceSummaryItem toTraceSummaryItem(AdminTraceSummary summary) {
        return new TraceSummaryItem(
                summary.exchangeId(),
                summary.sessionId(),
                summary.userId(),
                summary.executionMode(),
                summary.status(),
                summary.startedAt(),
                summary.finishedAt(),
                summary.durationMs()
        );
    }

    public record PagedResponse<T>(java.util.List<T> items, int page, int pageSize, long total) {
    }

    public record TraceSummaryItem(
            long exchangeId,
            long sessionId,
            long userId,
            String executionMode,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            long durationMs
    ) {
    }
}
