package com.superagent.agent.service;

import com.superagent.agent.domain.AdminAgentCheckpoint;
import com.superagent.agent.domain.AdminAgentRunDetail;
import com.superagent.agent.domain.AdminAgentRunSummary;
import com.superagent.agent.domain.AdminAgentRunStep;
import com.superagent.agent.domain.AdminPluginItem;
import com.superagent.agent.domain.AdminToolCallDetail;
import com.superagent.agent.repository.AgentAdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.settings.repository.AuditLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentAdminService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AgentAdminRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AgentAdminService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            AgentAdminRepository repository,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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
        AuthenticatedUserPrincipal principal = requireOwnerOrAdmin();
        TenantContext tenantContext = requireTenantContext();
        repository.updatePluginInstallation(tenantContext.tenantId(), pluginId, enabled);
        auditLogRepository.append(tenantContext.tenantId(), principal.userId(), enabled ? "plugin.enabled" : "plugin.disabled", "plugin", pluginId, Map.of("enabled", enabled));
        return true;
    }

    public List<AgentAdminRepository.ToolBindingRecord> listToolBindings() {
        requireAdminRole();
        return repository.listToolBindings(requireTenantContext().tenantId());
    }

    public AgentAdminRepository.ToolBindingRecord updateToolBinding(
            long bindingId,
            Boolean enabled,
            String riskLevel,
            Map<String, Object> config
    ) {
        AuthenticatedUserPrincipal principal = requireOwnerOrAdmin();
        TenantContext tenantContext = requireTenantContext();
        repository.findToolBinding(tenantContext.tenantId(), bindingId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tool binding not found"));
        if (riskLevel != null && !riskLevel.isBlank()) {
            String normalized = riskLevel.trim().toLowerCase();
            if (!java.util.Set.of("low", "standard", "high").contains(normalized)) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid risk level");
            }
        }
        String configJson = null;
        if (config != null) {
            try {
                configJson = objectMapper.writeValueAsString(config);
            } catch (Exception exception) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid config json");
            }
        }
        boolean updated = repository.updateToolBinding(tenantContext.tenantId(), bindingId, enabled, riskLevel, configJson);
        if (!updated) {
            throw new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tool binding not found");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        if (enabled != null) detail.put("enabled", enabled);
        if (riskLevel != null) detail.put("riskLevel", riskLevel);
        if (config != null) detail.put("configKeys", new java.util.ArrayList<>(config.keySet()));
        auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "tool_binding.updated", "tool_binding", bindingId, detail);
        return repository.findToolBinding(tenantContext.tenantId(), bindingId).orElseThrow();
    }

    public AdminPluginItem installPlugin(String pluginKey, String version, String displayName, Map<String, Object> manifest) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        if (pluginKey == null || pluginKey.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "pluginKey is required");
        }
        if (version == null || version.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "version is required");
        }
        validateManifest(manifest);
        String manifestJson = "{}";
        try {
            manifestJson = objectMapper.writeValueAsString(manifest == null ? Map.of() : manifest);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "invalid manifest json");
        }
        long pluginId = repository.installPlugin(
                tenantContext.tenantId(),
                pluginKey.trim(),
                version.trim(),
                displayName == null || displayName.isBlank() ? pluginKey.trim() : displayName.trim(),
                manifestJson
        );
        auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "plugin.installed", "plugin", pluginId, Map.of(
                "pluginKey", pluginKey.trim(),
                "version", version.trim()
        ));
        return repository.findPlugin(tenantContext.tenantId(), pluginId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Plugin not found after install"));
    }

    public boolean uninstallPlugin(long pluginId) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        repository.findPlugin(tenantContext.tenantId(), pluginId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Plugin not found"));
        boolean uninstalled = repository.uninstallPlugin(tenantContext.tenantId(), pluginId);
        if (uninstalled) {
            auditLogRepository.append(tenantContext.tenantId(), principal.userId(), "plugin.uninstalled", "plugin", pluginId, Map.of());
        }
        return uninstalled;
    }

    private void validateManifest(Map<String, Object> manifest) {
        if (manifest == null) {
            return;
        }
        Object tools = manifest.get("tools");
        if (tools != null && !(tools instanceof List<?>)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "manifest.tools must be a list");
        }
        if (tools instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> tool) || !(tool.get("toolId") instanceof String toolId) || toolId.isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "manifest.tools[].toolId is required");
                }
            }
        }
    }

    private AuthenticatedUserPrincipal requireOwnerOrAdmin() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Owner or admin permission required");
        }
        return principal;
    }

    public SecretUpdateResult updateToolSecret(String toolId, String secretKey, String secretValue) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        String normalizedToolId = normalizeRequired(toolId, "toolId is required");
        String normalizedSecretKey = normalizeRequired(secretKey, "secretKey is required");
        String normalizedSecretValue = normalizeRequired(secretValue, "secretValue is required");
        repository.upsertToolSecret(tenantContext.tenantId(), normalizedToolId, normalizedSecretKey, normalizedSecretValue);
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "tools.secret.updated",
                "tool_secret",
                null,
                Map.of(
                        "toolId", normalizedToolId,
                        "secretKey", normalizedSecretKey,
                        "configured", true
                )
        );
        return new SecretUpdateResult(normalizedToolId, normalizedSecretKey, true);
    }

    public SecretUpdateResult deleteToolSecret(String toolId, String secretKey) {
        AuthenticatedUserPrincipal principal = requireOwner();
        TenantContext tenantContext = requireTenantContext();
        String normalizedToolId = normalizeRequired(toolId, "toolId is required");
        String normalizedSecretKey = normalizeRequired(secretKey, "secretKey is required");
        repository.deleteToolSecret(tenantContext.tenantId(), normalizedToolId, normalizedSecretKey);
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "tools.secret.deleted",
                "tool_secret",
                null,
                Map.of(
                        "toolId", normalizedToolId,
                        "secretKey", normalizedSecretKey,
                        "configured", false
                )
        );
        return new SecretUpdateResult(normalizedToolId, normalizedSecretKey, false);
    }

    private void requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Agent admin permission required");
        }
    }

    private AuthenticatedUserPrincipal requireOwner() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Owner permission required");
        }
        return principal;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    public record SecretUpdateResult(String toolId, String secretKey, boolean configured) {
    }
}
