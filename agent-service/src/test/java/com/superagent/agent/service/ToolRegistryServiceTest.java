package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolRegistryServiceTest {

    private static final String MANIFEST = """
            {
              "version": "0.1.0",
              "riskLevel": "standard",
              "tools": [
                {
                  "id": "web.search",
                  "kind": "web",
                  "supportsStreaming": false,
                  "timeoutMs": 12000,
                  "retryPolicy": "retry:2",
                  "inputSchema": { "required": ["question"] },
                  "outputSchema": { "type": "search_results" }
                },
                { "id": "python.sandbox", "kind": "python", "riskLevel": "high", "supportsStreaming": false }
              ]
            }
            """;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Test
    void shouldHideHighRiskToolsWithoutExplicitTenantBinding() {
        ToolRegistryService service = new ToolRegistryService(agentRunRepository, new ObjectMapper());
        when(agentRunRepository.findEnabledTools(10001L)).thenReturn(List.of(
                new AgentRunRepository.EnabledToolRow(1L, "core-tools", MANIFEST, true, null, false)
        ));

        var tools = service.listEnabledTools(10001L);

        assertThat(tools).containsKey("web.search");
        assertThat(tools).doesNotContainKey("python.sandbox");
        assertThat(tools.get("web.search").timeoutMs()).isEqualTo(12_000);
        assertThat(tools.get("web.search").retryPolicy()).isEqualTo("retry:2");
        assertThat(tools.get("web.search").inputSchema()).containsEntry("required", List.of("question"));
        assertThat(tools.get("web.search").outputSchema()).containsEntry("type", "search_results");
    }

    @Test
    void shouldExposeHighRiskToolsAfterExplicitTenantBinding() {
        ToolRegistryService service = new ToolRegistryService(agentRunRepository, new ObjectMapper());
        when(agentRunRepository.findEnabledTools(10001L)).thenReturn(List.of(
                new AgentRunRepository.EnabledToolRow(1L, "core-tools", MANIFEST, true, "python.sandbox", true)
        ));

        var tools = service.listEnabledTools(10001L);

        assertThat(tools).containsKey("python.sandbox");
    }

    @Test
    void shouldHideToolsWhenPluginIsDisabled() {
        ToolRegistryService service = new ToolRegistryService(agentRunRepository, new ObjectMapper());
        when(agentRunRepository.findEnabledTools(10001L)).thenReturn(List.of(
                new AgentRunRepository.EnabledToolRow(1L, "core-tools", MANIFEST, false, "web.search", true)
        ));

        var tools = service.listEnabledTools(10001L);

        assertThat(tools).isEmpty();
    }
}
