package com.superagent.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ToolRegistryService {

    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper;

    public ToolRegistryService(AgentRunRepository agentRunRepository, ObjectMapper objectMapper) {
        this.agentRunRepository = agentRunRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, ToolSpec> listEnabledTools(long tenantId) {
        Map<String, ToolSpec> tools = new LinkedHashMap<>();
        for (AgentRunRepository.EnabledToolRow row : agentRunRepository.findEnabledTools(tenantId)) {
            if (!row.pluginEnabled()) {
                continue;
            }
            for (ToolSpec spec : parseManifest(row)) {
                boolean hasExplicitBinding = row.toolId() != null && !row.toolId().isBlank() && spec.id().equals(row.toolId());
                boolean highRisk = isHighRisk(spec);
                if (highRisk) {
                    if (hasExplicitBinding && row.toolEnabled()) {
                        tools.put(spec.id(), spec);
                    }
                    continue;
                }
                if (hasExplicitBinding) {
                    if (row.toolEnabled()) {
                        tools.put(spec.id(), spec);
                    }
                    continue;
                }
                tools.putIfAbsent(spec.id(), spec);
            }
        }
        return tools;
    }

    private boolean isHighRisk(ToolSpec spec) {
        return "high".equalsIgnoreCase(spec.riskLevel())
                || "http.request".equals(spec.id())
                || "python.sandbox".equals(spec.id());
    }

    private List<ToolSpec> parseManifest(AgentRunRepository.EnabledToolRow row) {
        List<ToolSpec> specs = new ArrayList<>();
        try {
            JsonNode manifest = objectMapper.readTree(row.manifestJson());
            String riskLevel = manifest.path("riskLevel").asText("standard");
            for (JsonNode tool : manifest.path("tools")) {
                specs.add(new ToolSpec(
                        tool.path("id").asText(),
                        row.pluginId(),
                        row.pluginKey(),
                        manifest.path("version").asText("0.1.0"),
                        tool.path("kind").asText("custom"),
                        Map.of(),
                        Map.of(),
                        tool.path("timeoutMs").asInt(10_000),
                        tool.path("retryPolicy").asText("none"),
                        tool.path("riskLevel").asText(riskLevel),
                        tool.path("supportsStreaming").asBoolean(false)
                ));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return specs;
    }
}
