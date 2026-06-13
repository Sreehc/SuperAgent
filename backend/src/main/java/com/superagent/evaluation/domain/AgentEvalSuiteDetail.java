package com.superagent.evaluation.domain;

import java.util.List;

public record AgentEvalSuiteDetail(
        AgentEvalSuite suite,
        List<AgentEvalCase> cases,
        List<AgentEvalRun> recentRuns
) {
}
