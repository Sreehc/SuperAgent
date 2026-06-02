package com.superagent.agent.service;

import com.superagent.agent.domain.AdminAgentCheckpoint;
import com.superagent.agent.domain.AdminAgentRunDetail;
import com.superagent.agent.domain.AdminAgentRunSummary;
import com.superagent.agent.domain.AdminAgentRunStep;
import com.superagent.agent.domain.AdminPluginItem;
import com.superagent.agent.domain.AdminToolCallDetail;
import com.superagent.agent.repository.AgentAdminRepository;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentAdminService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AgentAdminRepository repository;

    public AgentAdminService(CurrentAuthenticatedUser currentAuthenticatedUser, AgentAdminRepository repository) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
    }

    public ConversationService.PagedResult<AdminAgentRunSummary> listRuns(Integer page, Integer pageSize, String status) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        long total = repository.countRuns(tenantContext.tenantId(), status);
        List<AdminAgentRunSummary> items = repository.listRuns(tenantContext.tenantId(), status, resolvedPage, resolvedPageSize);
        return new ConversationService.PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public AdminAgentRunSummary getRun(long runId) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return repository.findRun(tenantContext.tenantId(), runId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Agent run not found"));
    }

    public AdminAgentRunDetail getRunDetail(long runId) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AdminAgentRunSummary summary = repository.findRun(tenantContext.tenantId(), runId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Agent run not found"));
        return new AdminAgentRunDetail(
                summary,
                repository.listSteps(tenantContext.tenantId(), runId),
                repository.listCheckpoints(tenantContext.tenantId(), runId),
                repository.listToolCalls(tenantContext.tenantId(), runId, null)
        );
    }

    public List<AdminAgentRunStep> listSteps(long runId) {
        requireAdminRole();
        return repository.listSteps(requireTenantContext().tenantId(), runId);
    }

    public AdminAgentRunSummary getRunByExchangeId(long exchangeId) {
        requireAdminRole();
        return repository.findRunByExchangeId(requireTenantContext().tenantId(), exchangeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Agent run not found"));
    }

    public List<AdminAgentCheckpoint> listCheckpoints(long runId) {
        requireAdminRole();
        return repository.listCheckpoints(requireTenantContext().tenantId(), runId);
    }

    public List<AdminToolCallDetail> listToolCalls(Long runId, String toolId) {
        requireAdminRole();
        return repository.listToolCalls(requireTenantContext().tenantId(), runId, toolId);
    }

    public List<AdminPluginItem> listPlugins() {
        requireAdminRole();
        return repository.listPlugins(requireTenantContext().tenantId());
    }

    public boolean updatePlugin(long pluginId, boolean enabled) {
        requireAdminRole();
        repository.updatePluginInstallation(requireTenantContext().tenantId(), pluginId, enabled);
        return true;
    }

    private void requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Agent admin permission required");
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
