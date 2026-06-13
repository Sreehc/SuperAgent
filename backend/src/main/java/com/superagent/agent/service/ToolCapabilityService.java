package com.superagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolCapabilityItem;
import com.superagent.agent.repository.AgentAdminRepository;
import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ToolCapabilityService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AgentAdminRepository repository;
    private final ObjectMapper objectMapper;

    public ToolCapabilityService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            AgentAdminRepository repository,
            ObjectMapper objectMapper
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<ToolCapabilityItem> listCapabilities() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        boolean ownerOrAdmin = principal.currentRole() == TenantRole.OWNER || principal.currentRole() == TenantRole.ADMIN;
        Map<String, ToolCapabilityItem> capabilities = new LinkedHashMap<>();

        for (AgentAdminRepository.PluginToolCapabilityRow row : repository.listPluginToolCapabilityRows(tenantContext.tenantId())) {
            for (ManifestTool tool : parseTools(row)) {
                boolean explicitBinding = row.toolId() != null && row.toolId().equals(tool.id());
                boolean highRisk = isHighRisk(tool);
                boolean enabled = row.pluginEnabled()
                        && (highRisk
                                ? explicitBinding && row.toolEnabled()
                                : !explicitBinding || row.toolEnabled());
                boolean executable = enabled && (!highRisk || ownerOrAdmin);
                String reason = reason(row.pluginEnabled(), explicitBinding, row.toolEnabled(), highRisk, ownerOrAdmin, enabled);
                ToolCapabilityItem item = new ToolCapabilityItem(
                        tool.id(),
                        displayName(tool.id()),
                        tool.kind(),
                        highRisk ? "high" : tool.riskLevel(),
                        enabled,
                        executable,
                        highRisk,
                        reason,
                        description(tool.id()),
                        repository.findConfiguredSecretKeys(tenantContext.tenantId(), tool.id())
                );
                if (explicitBinding || !capabilities.containsKey(tool.id())) {
                    capabilities.put(tool.id(), item);
                }
            }
        }

        return new ArrayList<>(capabilities.values());
    }

    private List<ManifestTool> parseTools(AgentAdminRepository.PluginToolCapabilityRow row) {
        try {
            JsonNode manifest = objectMapper.readTree(row.manifestJson());
            String pluginRiskLevel = manifest.path("riskLevel").asText("standard");
            List<ManifestTool> tools = new ArrayList<>();
            for (JsonNode tool : manifest.path("tools")) {
                String id = tool.path("id").asText("");
                if (!id.isBlank()) {
                    tools.add(new ManifestTool(
                            id,
                            tool.path("kind").asText("custom"),
                            tool.path("riskLevel").asText(pluginRiskLevel)
                    ));
                }
            }
            return tools;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isHighRisk(ManifestTool tool) {
        return "high".equalsIgnoreCase(tool.riskLevel())
                || "http.request".equals(tool.id())
                || "python.sandbox".equals(tool.id());
    }

    private String reason(boolean pluginEnabled, boolean explicitBinding, boolean bindingEnabled, boolean highRisk, boolean ownerOrAdmin, boolean enabled) {
        if (!pluginEnabled) {
            return "plugin_disabled";
        }
        if (highRisk && !explicitBinding) {
            return "high_risk_tool_not_bound";
        }
        if (explicitBinding && !bindingEnabled) {
            return "tool_disabled_for_tenant";
        }
        if (highRisk && !ownerOrAdmin) {
            return "role_not_allowed";
        }
        return enabled ? "enabled_for_role" : "tool_not_enabled";
    }

    private String displayName(String toolId) {
        return switch (toolId) {
            case "knowledge.search" -> "Knowledge Search";
            case "web.search" -> "Web Search";
            case "web.fetch" -> "Web Fetch";
            case "http.request" -> "HTTP Request";
            case "graph.query" -> "Graph Query";
            case "python.sandbox" -> "Python Sandbox";
            default -> toolId;
        };
    }

    private String description(String toolId) {
        return switch (toolId) {
            case "knowledge.search" -> "检索当前租户知识库证据。";
            case "web.search" -> "联网搜索公开网页结果。";
            case "web.fetch" -> "抓取指定网页并提取正文摘要。";
            case "http.request" -> "执行受控 HTTP 请求，受域名、方法和角色限制。";
            case "graph.query" -> "查询文档图谱中的实体、关系和路径。";
            case "python.sandbox" -> "在受控沙箱中执行 Python 代码。";
            default -> "插件工具能力。";
        };
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private record ManifestTool(String id, String kind, String riskLevel) {
    }
}
