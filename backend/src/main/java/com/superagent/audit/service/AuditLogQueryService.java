package com.superagent.audit.service;

import com.superagent.audit.domain.AuditLogItem;
import com.superagent.audit.repository.AuditLogQueryRepository;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AuditLogQueryRepository repository;

    public AuditLogQueryService(CurrentAuthenticatedUser currentAuthenticatedUser, AuditLogQueryRepository repository) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
    }

    public ConversationService.PagedResult<AuditLogItem> list(
            Integer page,
            Integer pageSize,
            String action,
            String resourceType,
            Long resourceId,
            Long actorId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        long total = repository.count(tenantContext.tenantId(), action, resourceType, resourceId, actorId, from, to);
        return new ConversationService.PagedResult<>(
                repository.list(tenantContext.tenantId(), action, resourceType, resourceId, actorId, from, to, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    private void requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Audit log permission required");
        }
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }
}
