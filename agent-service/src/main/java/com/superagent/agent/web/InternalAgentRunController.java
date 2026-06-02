package com.superagent.agent.web;

import com.superagent.agent.domain.AgentActionResponse;
import com.superagent.agent.domain.AgentRunCreateResponse;
import com.superagent.agent.domain.InternalAgentRunRequest;
import com.superagent.agent.service.AgentRunExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/internal/agent-runs")
public class InternalAgentRunController {

    private final AgentRunExecutionService executionService;
    private final com.superagent.agent.service.AgentRunStreamRegistry streamRegistry;

    public InternalAgentRunController(
            AgentRunExecutionService executionService,
            com.superagent.agent.service.AgentRunStreamRegistry streamRegistry
    ) {
        this.executionService = executionService;
        this.streamRegistry = streamRegistry;
    }

    @PostMapping
    public AgentRunCreateResponse createRun(@Valid @RequestBody InternalAgentRunRequest request) {
        long runId = executionService.createAndStart(request);
        return new AgentRunCreateResponse(runId, "accepted");
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable long runId) {
        return streamRegistry.register(runId);
    }

    @PostMapping("/{runId}/resume")
    public AgentActionResponse resume(@PathVariable long runId) {
        return new AgentActionResponse(runId, executionService.resume(runId));
    }

    @PostMapping("/{runId}/cancel")
    public AgentActionResponse cancel(@PathVariable long runId) {
        return new AgentActionResponse(runId, executionService.cancel(runId));
    }
}
