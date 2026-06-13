package com.superagent.evaluation.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.evaluation.domain.AgentEvalCase;
import com.superagent.evaluation.domain.AgentEvalRun;
import com.superagent.evaluation.domain.AgentEvalSuite;
import com.superagent.evaluation.domain.AgentEvalSuiteDetail;
import com.superagent.evaluation.repository.AgentEvalRepository;
import com.superagent.settings.repository.AuditLogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvalService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final AgentEvalRepository repository;
    private final AuditLogRepository auditLogRepository;

    public AgentEvalService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            AgentEvalRepository repository,
            AuditLogRepository auditLogRepository
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
    }

    public ConversationService.PagedResult<AgentEvalSuite> listSuites(Integer page, Integer pageSize, String keyword) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize);
        long total = repository.countSuites(tenantContext.tenantId(), keyword);
        return new ConversationService.PagedResult<>(
                repository.listSuites(tenantContext.tenantId(), keyword, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    public AgentEvalSuiteDetail getSuite(long suiteId) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AgentEvalSuite suite = repository.findSuite(tenantContext.tenantId(), suiteId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Eval suite not found"));
        return new AgentEvalSuiteDetail(
                suite,
                repository.listCases(tenantContext.tenantId(), suiteId),
                repository.listRuns(tenantContext.tenantId(), suiteId, null, 1, 10)
        );
    }

    @Transactional
    public AgentEvalSuite createSuite(String suiteKey, String name, String description) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AgentEvalSuite suite = repository.createSuite(
                tenantContext.tenantId(),
                requireSlug(suiteKey, "suiteKey"),
                requireText(name, "name"),
                normalizeOptional(description)
        );
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.suite.created",
                "agent_eval_suite",
                suite.id(),
                Map.of("suiteKey", suite.suiteKey(), "name", suite.name())
        );
        return suite;
    }

    @Transactional
    public AgentEvalSuite updateSuite(long suiteId, String name, String description) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        ensureTenantSuite(tenantContext.tenantId(), suiteId);
        AgentEvalSuite suite = repository.updateSuite(tenantContext.tenantId(), suiteId, normalizeOptional(name), normalizeOptional(description));
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.suite.updated",
                "agent_eval_suite",
                suite.id(),
                Map.of("suiteKey", suite.suiteKey())
        );
        return suite;
    }

    @Transactional
    public AgentEvalCase createCase(long suiteId, String caseKey, Map<String, Object> input, Map<String, Object> expected) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        ensureTenantSuite(tenantContext.tenantId(), suiteId);
        AgentEvalCase evalCase = repository.createCase(
                tenantContext.tenantId(),
                suiteId,
                requireSlug(caseKey, "caseKey"),
                normalizeMap(input),
                normalizeMap(expected)
        );
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.case.created",
                "agent_eval_case",
                evalCase.id(),
                Map.of("suiteId", suiteId, "caseKey", evalCase.caseKey())
        );
        return evalCase;
    }

    @Transactional
    public AgentEvalCase updateCase(long caseId, String caseKey, Map<String, Object> input, Map<String, Object> expected) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AgentEvalCase existing = repository.findCase(tenantContext.tenantId(), caseId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Eval case not found"));
        AgentEvalCase evalCase = repository.updateCase(
                tenantContext.tenantId(),
                caseId,
                caseKey == null ? null : requireSlug(caseKey, "caseKey"),
                input == null ? existing.input() : normalizeMap(input),
                expected == null ? existing.expected() : normalizeMap(expected)
        );
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.case.updated",
                "agent_eval_case",
                evalCase.id(),
                Map.of("suiteId", evalCase.suiteId(), "caseKey", evalCase.caseKey())
        );
        return evalCase;
    }

    @Transactional
    public boolean deleteCase(long caseId) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        AgentEvalCase existing = repository.findCase(tenantContext.tenantId(), caseId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Eval case not found"));
        boolean deleted = repository.deleteCase(tenantContext.tenantId(), caseId);
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.case.deleted",
                "agent_eval_case",
                caseId,
                Map.of("suiteId", existing.suiteId(), "caseKey", existing.caseKey())
        );
        return deleted;
    }

    public ConversationService.PagedResult<AgentEvalRun> listRuns(Integer page, Integer pageSize, Long suiteId, String status) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize);
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        long total = repository.countRuns(tenantContext.tenantId(), suiteId, normalizedStatus);
        return new ConversationService.PagedResult<>(
                repository.listRuns(tenantContext.tenantId(), suiteId, normalizedStatus, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    public AgentEvalRun getRun(long runId) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        return repository.findRun(tenantContext.tenantId(), runId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Eval run not found"));
    }

    @Transactional
    public AgentEvalRun createRun(
            long suiteId,
            String status,
            Integer passedCount,
            Integer failedCount,
            Map<String, Object> report
    ) {
        AuthenticatedUserPrincipal principal = requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        ensureTenantSuite(tenantContext.tenantId(), suiteId);
        Map<String, Object> normalizedReport = normalizeMap(report);
        Counts counts = resolveCounts(passedCount, failedCount, normalizedReport);
        AgentEvalRun run = repository.createRun(
                tenantContext.tenantId(),
                suiteId,
                status == null || status.isBlank() ? inferStatus(counts, normalizedReport) : normalizeStatus(status),
                counts.passed(),
                counts.failed(),
                normalizedReport
        );
        auditLogRepository.append(
                tenantContext.tenantId(),
                principal.userId(),
                "eval.run.created",
                "agent_eval_run",
                run.id(),
                Map.of("suiteId", suiteId, "status", run.status(), "passedCount", run.passedCount(), "failedCount", run.failedCount())
        );
        return run;
    }

    private void ensureTenantSuite(long tenantId, long suiteId) {
        AgentEvalSuite suite = repository.findSuite(tenantId, suiteId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Eval suite not found"));
        if (suite.tenantId() == null || suite.tenantId() != tenantId) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Global eval suites are read-only");
        }
    }

    private AuthenticatedUserPrincipal requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (principal.currentRole() != TenantRole.OWNER && principal.currentRole() != TenantRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Eval admin permission required");
        }
        return principal;
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private String requireSlug(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null || !normalized.matches("[a-zA-Z0-9._:-]{1,128}")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, field + " must be 1-128 chars and use letters, numbers, '.', '_', ':' or '-'");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeOptional(status);
        if (!List.of("pending", "running", "success", "failed").contains(normalized)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "status must be pending, running, success or failed");
        }
        return normalized;
    }

    private String inferStatus(Counts counts, Map<String, Object> report) {
        Object passed = report.get("passed");
        if (passed instanceof Boolean booleanValue) {
            return booleanValue ? "success" : "failed";
        }
        if (counts.passed() + counts.failed() == 0) {
            return "pending";
        }
        return counts.failed() == 0 ? "success" : "failed";
    }

    private Counts resolveCounts(Integer passedCount, Integer failedCount, Map<String, Object> report) {
        if (passedCount != null || failedCount != null) {
            return new Counts(Math.max(passedCount == null ? 0 : passedCount, 0), Math.max(failedCount == null ? 0 : failedCount, 0));
        }
        Object casesValue = report.get("cases");
        if (!(casesValue instanceof List<?> cases)) {
            return new Counts(readInt(report.get("passedCases")), readInt(report.get("failedCases")));
        }
        int passed = 0;
        int failed = 0;
        for (Object item : cases) {
            if (item instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("passed"))) {
                passed++;
            } else {
                failed++;
            }
        }
        return new Counts(passed, failed);
    }

    private int readInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        return 0;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Counts(int passed, int failed) {
    }
}
