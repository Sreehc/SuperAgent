package com.superagent.audit.web;

import com.superagent.audit.domain.AuditLogItem;
import com.superagent.audit.service.AuditLogQueryService;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/audit-logs")
public class AuditLogAdminController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogAdminController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<AuditLogResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        ConversationService.PagedResult<AuditLogItem> result = auditLogQueryService.list(
                page,
                pageSize,
                action,
                resourceType,
                resourceId,
                actorId,
                from,
                to
        );
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toResponse).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    private AuditLogResponse toResponse(AuditLogItem item) {
        return new AuditLogResponse(
                item.id(),
                item.actorId(),
                item.action(),
                item.resourceType(),
                item.resourceId(),
                item.detail(),
                item.createdAt()
        );
    }

    public record AuditLogResponse(
            long id,
            Long actorId,
            String action,
            String resourceType,
            Long resourceId,
            Map<String, Object> detail,
            OffsetDateTime createdAt
    ) {
    }

    public record PagedResponse<T>(List<T> items, int page, int pageSize, long total) {
    }
}
