package com.superagent.observability.web;

import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import com.superagent.observability.domain.RerankTraceDetail;
import com.superagent.observability.domain.RetrievalTraceDetail;
import com.superagent.observability.service.TraceAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin")
public class TraceQueryAdminController {

    private final TraceAdminService traceAdminService;

    public TraceQueryAdminController(TraceAdminService traceAdminService) {
        this.traceAdminService = traceAdminService;
    }

    @GetMapping("/retrievals")
    public ApiResponse<TraceAdminController.PagedResponse<RetrievalTraceDetail>> listRetrievals(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long exchangeId,
            @RequestParam(required = false) String channel
    ) {
        ConversationService.PagedResult<RetrievalTraceDetail> result = traceAdminService.listRetrievals(page, pageSize, exchangeId, channel);
        return ApiResponse.success(new TraceAdminController.PagedResponse<>(result.items(), result.page(), result.pageSize(), result.total()));
    }

    @GetMapping("/reranks")
    public ApiResponse<TraceAdminController.PagedResponse<RerankTraceDetail>> listReranks(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long exchangeId,
            @RequestParam(required = false) String status
    ) {
        ConversationService.PagedResult<RerankTraceDetail> result = traceAdminService.listReranks(page, pageSize, exchangeId, status);
        return ApiResponse.success(new TraceAdminController.PagedResponse<>(result.items(), result.page(), result.pageSize(), result.total()));
    }
}
