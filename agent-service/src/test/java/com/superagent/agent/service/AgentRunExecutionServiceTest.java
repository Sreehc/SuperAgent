package com.superagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.domain.ToolResult;
import com.superagent.agent.domain.ToolSpec;
import com.superagent.agent.repository.AgentRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentRunExecutionServiceTest {

    @Mock
    private AgentRunRepository repository;

    @Mock
    private AgentRunStreamRegistry streamRegistry;

    @Mock
    private ToolRegistryService toolRegistryService;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Mock
    private TenantRuntimeSettingsService runtimeSettingsService;

    private AgentRunExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new AgentRunExecutionService(
                repository,
                streamRegistry,
                toolRegistryService,
                toolExecutionService,
                runtimeSettingsService,
                new ObjectMapper(),
                Runnable::run
        );
    }

    @Test
    void shouldCoverStableCheckpointsAndStructuredToolFailure() {
        InternalAgentRunRequest request = new InternalAgentRunRequest(
                9L,
                3L,
                7L,
                11L,
                5L,
                "ADMIN",
                "请联网查询今天的行业变化",
                null,
                "SUMMARY_PLUS_WINDOW",
                List.of("最近一次消息")
        );
        ToolSpec webSearch = new ToolSpec("web.search", 1L, "core-tools", "0.1.0", "web", Map.of(), Map.of(), 10_000, "none", "standard", false);

        when(runtimeSettingsService.resolveExecutionPolicy(9L))
                .thenReturn(new TenantRuntimeSettingsService.ExecutionPolicy(true, 6, 6, true));
        when(toolRegistryService.listEnabledTools(9L)).thenReturn(Map.of("web.search", webSearch));
        when(repository.createStep(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(101L, 102L, 103L, 103L, 104L, 105L, 106L);
        when(repository.createToolCall(anyLong(), anyLong(), any(), anyString(), any(), anyString())).thenReturn(301L);
        when(toolExecutionService.execute(eq(webSearch), any()))
                .thenReturn(new ToolResult("web.search", "failed", "搜索结果被限流", Map.of("statusCode", 429), "rate_limited"));

        executionService.execute(55L, request, false);

        ArgumentCaptor<String> checkpointTypes = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeastOnce()).saveCheckpoint(eq(9L), eq(55L), anyInt(), any(), checkpointTypes.capture(), anyString());
        assertThat(checkpointTypes.getAllValues()).contains(
                "model_decision",
                "tool_selected",
                "tool_call_started",
                "tool_call_finished",
                "observation_ready",
                "answer_ready"
        );

        verify(toolExecutionService).execute(eq(webSearch), any());
        verify(repository).updateRunProgress(9L, 55L, 3, 1, "success", null);
        verify(streamRegistry, never()).emit(eq(55L), eq("error"), anyMap());
    }

    @Test
    void shouldResumeFromStableCheckpointWithoutRepeatingCompletedSteps() {
        InternalAgentRunRequest request = new InternalAgentRunRequest(
                1L,
                2L,
                3L,
                4L,
                5L,
                "ADMIN",
                "请搜索最新动态",
                null,
                "SUMMARY_PLUS_WINDOW",
                List.of()
        );
        String payload = """
                {
                  "nextPhase":"OBSERVE",
                  "modelStepCount":2,
                  "toolCallCount":1,
                  "selectedToolId":"web.search",
                  "selectedToolReason":"open_ended_or_realtime_request",
                  "toolCallId":88,
                  "toolInput":{"question":"请搜索最新动态"},
                  "toolStatus":"success",
                  "toolSummary":"返回 3 条网页搜索结果",
                  "toolOutput":{"results":[{"title":"A"}]}
                }
                """;

        when(runtimeSettingsService.resolveExecutionPolicy(1L))
                .thenReturn(new TenantRuntimeSettingsService.ExecutionPolicy(true, 6, 6, true));
        when(toolRegistryService.listEnabledTools(1L)).thenReturn(Map.of());
        when(repository.findLatestStableCheckpoint(1L, 99L))
                .thenReturn(java.util.Optional.of(new AgentRunRepository.CheckpointRecord(4, 33L, "tool_call_finished", true, payload, java.time.OffsetDateTime.now())));
        when(repository.createStep(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(104L, 105L, 106L);

        executionService.execute(99L, request, true);

        ArgumentCaptor<Integer> stepNos = ArgumentCaptor.forClass(Integer.class);
        verify(repository, atLeastOnce()).createStep(eq(1L), eq(99L), stepNos.capture(), anyString(), anyString(), any(), any(), any(), any(), any(), anyString());
        assertThat(stepNos.getAllValues()).contains(4, 5, 6);
        assertThat(stepNos.getAllValues()).doesNotContain(1, 2, 3);
        verify(toolExecutionService, never()).execute(any(), any());
        verify(repository).updateRunProgress(1L, 99L, 3, 1, "success", null);
    }

    @Test
    void shouldStopBeforeExecutingToolWhenGuardrailReached() {
        InternalAgentRunRequest request = new InternalAgentRunRequest(
                7L,
                2L,
                3L,
                4L,
                5L,
                "ADMIN",
                "请搜索今天的新闻",
                null,
                "SUMMARY_PLUS_WINDOW",
                List.of()
        );
        ToolSpec webSearch = new ToolSpec("web.search", 1L, "core-tools", "0.1.0", "web", Map.of(), Map.of(), 10_000, "none", "standard", false);

        when(runtimeSettingsService.resolveExecutionPolicy(7L))
                .thenReturn(new TenantRuntimeSettingsService.ExecutionPolicy(true, 1, 6, true));
        when(toolRegistryService.listEnabledTools(7L)).thenReturn(Map.of("web.search", webSearch));
        when(repository.createStep(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(201L, 202L, 203L);

        executionService.execute(77L, request, false);

        verify(toolExecutionService, never()).execute(any(), any());
        verify(repository).updateRunProgress(7L, 77L, 1, 0, "success", null);
        verify(streamRegistry).emit(eq(77L), eq("done"), anyMap());
    }
}
