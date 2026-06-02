package com.superagent.agent.service;

import java.util.List;

record GraphQueryRequest(
        List<String> terms,
        List<String> normalizedTerms,
        String sourceEntity,
        String targetEntity,
        boolean pathQuery,
        int maxHops
) {
}
