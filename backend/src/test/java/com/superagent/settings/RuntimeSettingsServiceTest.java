package com.superagent.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.settings.repository.AuditLogRepository;
import com.superagent.settings.repository.RuntimeSettingsRepository;
import com.superagent.settings.service.RuntimeSettingsService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuntimeSettingsServiceTest {

    @Mock
    private com.superagent.auth.security.CurrentAuthenticatedUser currentAuthenticatedUser;

    @Mock
    private RuntimeSettingsRepository runtimeSettingsRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void shouldDefaultToolsToDisabled() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getTools().setWebSearchEnabled(false);
        properties.getTools().setHttpToolEnabled(false);
        properties.getTools().setGraphToolEnabled(false);
        properties.getTools().setCodeExecutionEnabled(false);
        properties.getTools().setToolTimeoutMs(10_000);
        properties.getTools().setSearchProvider("tavily");
        properties.getTools().setAllowedHttpDomains(List.of());

        when(runtimeSettingsRepository.findSection(10001L, "tools")).thenReturn(Optional.empty());

        RuntimeSettingsService service = new RuntimeSettingsService(
                currentAuthenticatedUser,
                runtimeSettingsRepository,
                auditLogRepository,
                properties
        );

        var settings = service.resolveToolSettings(10001L);

        assertThat(settings.webSearchEnabled()).isFalse();
        assertThat(settings.httpToolEnabled()).isFalse();
        assertThat(settings.graphToolEnabled()).isFalse();
        assertThat(settings.codeExecutionEnabled()).isFalse();
    }

    @Test
    void shouldDefaultAgentMemoryStrategyToSummaryPlusWindow() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAgent().setEnabledDefault(true);
        properties.getAgent().setMaxModelSteps(6);
        properties.getAgent().setMaxToolCalls(6);
        properties.getAgent().setCheckpointEnabled(true);
        properties.getAgent().setCodeExecutionEnabled(false);
        properties.getTools().setWebSearchEnabled(false);
        properties.getTools().setHttpToolEnabled(false);
        properties.getTools().setGraphToolEnabled(false);
        properties.getTools().setCodeExecutionEnabled(false);
        properties.getTools().setToolTimeoutMs(10_000);
        properties.getTools().setAllowedHttpDomains(List.of("example.com"));

        when(runtimeSettingsRepository.findSection(10001L, "agent")).thenReturn(Optional.empty());

        RuntimeSettingsService service = new RuntimeSettingsService(
                currentAuthenticatedUser,
                runtimeSettingsRepository,
                auditLogRepository,
                properties
        );

        var settings = service.resolveAgentSettings(10001L);

        assertThat(settings.defaultMemoryStrategy()).isEqualTo(MemoryStrategy.SUMMARY_PLUS_WINDOW);
    }

    @Test
    void shouldAllowAgentMemoryStrategyOverrideFromTenantSettings() {
        SuperAgentProperties properties = new SuperAgentProperties();
        properties.getAgent().setEnabledDefault(true);
        properties.getAgent().setMaxModelSteps(6);
        properties.getAgent().setMaxToolCalls(6);
        properties.getAgent().setCheckpointEnabled(true);
        properties.getAgent().setCodeExecutionEnabled(false);
        properties.getTools().setWebSearchEnabled(false);
        properties.getTools().setHttpToolEnabled(false);
        properties.getTools().setGraphToolEnabled(false);
        properties.getTools().setCodeExecutionEnabled(false);
        properties.getTools().setToolTimeoutMs(10_000);
        properties.getTools().setAllowedHttpDomains(List.of("example.com"));

        when(runtimeSettingsRepository.findSection(10001L, "agent")).thenReturn(Optional.of(Map.of(
                "defaultMemoryStrategy", "SUMMARY_WINDOW"
        )));

        RuntimeSettingsService service = new RuntimeSettingsService(
                currentAuthenticatedUser,
                runtimeSettingsRepository,
                auditLogRepository,
                properties
        );

        var settings = service.resolveAgentSettings(10001L);

        assertThat(settings.defaultMemoryStrategy()).isEqualTo(MemoryStrategy.SUMMARY_WINDOW);
    }
}
