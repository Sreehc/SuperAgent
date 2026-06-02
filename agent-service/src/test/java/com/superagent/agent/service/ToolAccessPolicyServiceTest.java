package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolAccessPolicyServiceTest {

    @Mock
    private TenantRuntimeSettingsService runtimeSettingsService;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Test
    void shouldResolveBindingTemplateAndSecretAliases() {
        ToolAccessPolicyService service = new ToolAccessPolicyService(runtimeSettingsService, agentRunRepository, new ObjectMapper());
        ToolSpec spec = new ToolSpec("web.search", 1L, "core-tools", "0.1.0", "web", Map.of(), Map.of(), 10_000, "retry:2", "standard", false);

        when(runtimeSettingsService.resolveToolPolicy(10001L)).thenReturn(
                new TenantRuntimeSettingsService.ToolPolicy(true, false, false, false, 8_000, "tavily", List.of("example.com"))
        );
        when(agentRunRepository.findToolBinding(10001L, "web.search")).thenReturn(Optional.of(
                new AgentRunRepository.TenantToolBindingRow(
                        1L,
                        true,
                        "standard",
                        """
                        {
                          "timeoutMs": 5000,
                          "maxResults": 3,
                          "allowedMethods": ["get"],
                          "parameterTemplate": { "scope": "news" },
                          "secretRefs": { "apiKey": "tavily_prod" }
                        }
                        """
                )
        ));
        when(agentRunRepository.findToolSecrets(10001L, "web.search")).thenReturn(Map.of(
                "tavily_prod", "secret-value"
        ));

        ToolAccessPolicyService.ToolAccessPolicy policy = service.resolve(10001L, "ADMIN", spec);

        assertThat(policy.executable()).isTrue();
        assertThat(policy.timeoutMs()).isEqualTo(5000);
        assertThat(policy.maxRetries()).isEqualTo(2);
        assertThat(policy.maxResults()).isEqualTo(3);
        assertThat(policy.parameterTemplate()).containsEntry("scope", "news");
        assertThat(policy.secrets()).containsEntry("apiKey", "secret-value");
        assertThat(policy.allowedMethods()).containsExactly("GET");
    }

    @Test
    void shouldBlockHighRiskToolForMemberRole() {
        ToolAccessPolicyService service = new ToolAccessPolicyService(runtimeSettingsService, agentRunRepository, new ObjectMapper());
        ToolSpec spec = new ToolSpec("http.request", 1L, "core-tools", "0.1.0", "http", Map.of(), Map.of(), 10_000, "none", "high", false);

        when(runtimeSettingsService.resolveToolPolicy(10001L)).thenReturn(
                new TenantRuntimeSettingsService.ToolPolicy(true, true, false, false, 8_000, "tavily", List.of("example.com"))
        );
        when(agentRunRepository.findToolBinding(10001L, "http.request")).thenReturn(Optional.of(
                new AgentRunRepository.TenantToolBindingRow(1L, true, "high", "{\"allowedMethods\":[\"GET\",\"POST\"]}")
        ));
        when(agentRunRepository.findToolSecrets(10001L, "http.request")).thenReturn(Map.of());

        ToolAccessPolicyService.ToolAccessPolicy policy = service.resolve(10001L, "MEMBER", spec);

        assertThat(policy.highRisk()).isTrue();
        assertThat(policy.executable()).isFalse();
        assertThat(policy.featureEnabled()).isTrue();
        assertThat(policy.bindingEnabled()).isTrue();
    }
}
