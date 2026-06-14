package com.superagent.evaluation.web;

import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import com.superagent.evaluation.domain.AgentEvalCase;
import com.superagent.evaluation.domain.AgentEvalRun;
import com.superagent.evaluation.domain.AgentEvalSuite;
import com.superagent.evaluation.domain.AgentEvalSuiteDetail;
import com.superagent.evaluation.repository.AgentEvalRepository;
import com.superagent.evaluation.service.AgentEvalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}/admin/evals")
public class AgentEvalAdminController {

    private final AgentEvalService evalService;

    public AgentEvalAdminController(AgentEvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/suites")
    public ApiResponse<PagedResponse<AgentEvalSuite>> listSuites(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword
    ) {
        ConversationService.PagedResult<AgentEvalSuite> result = evalService.listSuites(page, pageSize, keyword);
        return ApiResponse.success(new PagedResponse<>(result.items(), result.page(), result.pageSize(), result.total()));
    }

    @PostMapping("/suites")
    public ApiResponse<AgentEvalSuite> createSuite(@Valid @RequestBody SuiteRequest request) {
        return ApiResponse.success(evalService.createSuite(request.suiteKey(), request.name(), request.description()));
    }

    @GetMapping("/suites/{suiteId}")
    public ApiResponse<AgentEvalSuiteDetail> getSuite(@PathVariable long suiteId) {
        return ApiResponse.success(evalService.getSuite(suiteId));
    }

    @PatchMapping("/suites/{suiteId}")
    public ApiResponse<AgentEvalSuite> updateSuite(@PathVariable long suiteId, @Valid @RequestBody UpdateSuiteRequest request) {
        return ApiResponse.success(evalService.updateSuite(suiteId, request.name(), request.description()));
    }

    @PostMapping("/suites/{suiteId}/cases")
    public ApiResponse<AgentEvalCase> createCase(@PathVariable long suiteId, @Valid @RequestBody CaseRequest request) {
        return ApiResponse.success(evalService.createCase(suiteId, request.caseKey(), request.input(), request.expected()));
    }

    @PatchMapping("/cases/{caseId}")
    public ApiResponse<AgentEvalCase> updateCase(@PathVariable long caseId, @Valid @RequestBody UpdateCaseRequest request) {
        return ApiResponse.success(evalService.updateCase(caseId, request.caseKey(), request.input(), request.expected()));
    }

    @DeleteMapping("/cases/{caseId}")
    public ApiResponse<DeleteResponse> deleteCase(@PathVariable long caseId) {
        return ApiResponse.success(new DeleteResponse(evalService.deleteCase(caseId)));
    }

    @GetMapping("/runs")
    public ApiResponse<PagedResponse<AgentEvalRun>> listRuns(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) String status
    ) {
        ConversationService.PagedResult<AgentEvalRun> result = evalService.listRuns(page, pageSize, suiteId, status);
        return ApiResponse.success(new PagedResponse<>(result.items(), result.page(), result.pageSize(), result.total()));
    }

    @PostMapping("/suites/{suiteId}/runs")
    public ApiResponse<AgentEvalRun> createRun(@PathVariable long suiteId, @Valid @RequestBody RunRequest request) {
        return ApiResponse.success(evalService.createRun(suiteId, request.status(), request.passedCount(), request.failedCount(), request.report()));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<AgentEvalRun> getRun(@PathVariable long runId) {
        return ApiResponse.success(evalService.getRun(runId));
    }

    @GetMapping("/runs/{runId}/cases")
    public ApiResponse<List<RunCaseItem>> listRunCases(
            @PathVariable long runId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(evalService.listRunCases(runId, status).stream()
                .map(item -> new RunCaseItem(
                        item.id(),
                        item.runId(),
                        item.caseId(),
                        item.caseKey(),
                        item.status(),
                        item.score(),
                        item.actual(),
                        item.expected(),
                        item.diff(),
                        item.latencyMs(),
                        item.errorMessage(),
                        item.createdAt()
                ))
                .toList());
    }

    public record SuiteRequest(
            @NotBlank @Size(max = 128) String suiteKey,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record UpdateSuiteRequest(
            @Size(max = 255) String name,
            @Size(max = 2000) String description
    ) {
    }

    public record CaseRequest(
            @NotBlank @Size(max = 128) String caseKey,
            Map<String, Object> input,
            Map<String, Object> expected
    ) {
    }

    public record UpdateCaseRequest(
            @Size(max = 128) String caseKey,
            Map<String, Object> input,
            Map<String, Object> expected
    ) {
    }

    public record RunRequest(
            String status,
            Integer passedCount,
            Integer failedCount,
            Map<String, Object> report
    ) {
    }

    public record DeleteResponse(boolean deleted) {
    }

    public record PagedResponse<T>(List<T> items, int page, int pageSize, long total) {
    }

    public record RunCaseItem(
            long id,
            long runId,
            long caseId,
            String caseKey,
            String status,
            Double score,
            Map<String, Object> actual,
            Map<String, Object> expected,
            Map<String, Object> diff,
            Integer latencyMs,
            String errorMessage,
            OffsetDateTime createdAt
    ) {
    }
}
