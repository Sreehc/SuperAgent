package com.superagent.observability.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.observability.domain.AdminTraceDetail;
import com.superagent.observability.domain.AdminTraceSummary;
import com.superagent.observability.domain.RerankTraceDetail;
import com.superagent.observability.domain.RetrievalTraceDetail;
import com.superagent.observability.repository.TraceQueryRepository;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TraceAdminService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final TraceQueryRepository traceQueryRepository;

    public TraceAdminService(CurrentAuthenticatedUser currentAuthenticatedUser, TraceQueryRepository traceQueryRepository) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.traceQueryRepository = traceQueryRepository;
    }

    public ConversationService.PagedResult<AdminTraceSummary> listTraces(
            Integer page,
            Integer pageSize,
            String status,
            String executionMode,
            Long userId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        long total = traceQueryRepository.countTraces(tenantContext.tenantId(), status, executionMode, userId, from, to);
        return new ConversationService.PagedResult<>(
                traceQueryRepository.listTraces(
                        tenantContext.tenantId(),
                        status,
                        executionMode,
                        userId,
                        from,
                        to,
                        resolvedPage,
                        resolvedPageSize
                ),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    public AdminTraceDetail getTraceDetail(long exchangeId) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return traceQueryRepository.findTraceDetail(tenantContext.tenantId(), exchangeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Trace not found"));
    }

    public ConversationService.PagedResult<RetrievalTraceDetail> listRetrievals(
            Integer page,
            Integer pageSize,
            Long exchangeId,
            String channel
    ) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        long total = traceQueryRepository.countRetrievals(tenantContext.tenantId(), exchangeId, channel);
        return new ConversationService.PagedResult<>(
                traceQueryRepository.listRetrievals(tenantContext.tenantId(), exchangeId, channel, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    public ConversationService.PagedResult<RerankTraceDetail> listReranks(
            Integer page,
            Integer pageSize,
            Long exchangeId,
            String status
    ) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        long total = traceQueryRepository.countReranks(tenantContext.tenantId(), exchangeId, status);
        return new ConversationService.PagedResult<>(
                traceQueryRepository.listReranks(tenantContext.tenantId(), exchangeId, status, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    private void requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Trace admin permission required");
        }
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize, int defaultValue, int maxValue) {
        if (pageSize == null || pageSize < 1) {
            return defaultValue;
        }
        return Math.min(pageSize, maxValue);
    }
}
