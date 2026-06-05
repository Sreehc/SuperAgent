package com.superagent.rag.service;

import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.settings.domain.RerankSettings;
import com.superagent.settings.service.RuntimeSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "super-agent.ai.rerank-provider", havingValue = "openai-compatible")
public class OpenAiCompatibleRerankClient implements RerankClient {

    private final RuntimeSettingsService runtimeSettingsService;
    private final SuperAgentProperties properties;

    public OpenAiCompatibleRerankClient(RuntimeSettingsService runtimeSettingsService, SuperAgentProperties properties) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.properties = properties;
    }

    @Override
    public RerankResult rerank(String query, List<RagEvidence> evidences) {
        RerankSettings settings = resolveSettings();
        if (!settings.enabled()) {
            return new RerankResult(evidences, settings.provider(), settings.model(), "skipped", "disabled_by_config", null, null);
        }
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()
                || settings.model() == null || settings.model().isBlank()
                || settings.apiKey() == null || settings.apiKey().isBlank()) {
            return new RerankResult(evidences, settings.provider(), settings.model(), "skipped", "incomplete_config", null, null);
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(Math.max(1L, properties.getAi().getHttpConnectTimeoutMillis())));
        requestFactory.setReadTimeout(Math.toIntExact(Math.max(1L, properties.getAi().getHttpReadTimeoutMillis())));
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(settings.baseUrl())
                .defaultHeader("Authorization", "Bearer " + settings.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        Instant startedAt = Instant.now();
        try {
            List<RerankInput> inputs = new ArrayList<>(evidences.size());
            Map<Integer, RagEvidence> evidenceByIndex = new LinkedHashMap<>();
            for (int index = 0; index < evidences.size(); index++) {
                RagEvidence evidence = evidences.get(index);
                inputs.add(new RerankInput(index, buildInputText(evidence)));
                evidenceByIndex.put(index, evidence);
            }
            RerankApiResponse response = client.post()
                    .uri("/rerank")
                    .body(new RerankApiRequest(settings.model(), query, inputs))
                    .retrieve()
                    .body(RerankApiResponse.class);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return failureFallback(evidences, settings, "empty_response", startedAt);
            }
            List<RagEvidence> reranked = response.data().stream()
                    .sorted(Comparator.comparingInt(RerankItem::rank))
                    .map(item -> evidenceByIndex.get(item.index()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (reranked.isEmpty()) {
                return failureFallback(evidences, settings, "empty_result", startedAt);
            }
            return new RerankResult(
                    reranked,
                    settings.provider(),
                    response.model() == null || response.model().isBlank() ? settings.model() : response.model(),
                    "success",
                    null,
                    null,
                    latencyMs(startedAt)
            );
        } catch (Exception exception) {
            return failureFallback(evidences, settings, "provider_error", startedAt);
        }
    }

    private RerankSettings resolveSettings() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            return new RerankSettings(false, "openai-compatible", null, null, null);
        }
        return runtimeSettingsService.resolveRerankSettings(tenantContext.tenantId());
    }

    private RerankResult failureFallback(List<RagEvidence> evidences, RerankSettings settings, String reason, Instant startedAt) {
        return new RerankResult(
                evidences,
                settings.provider(),
                settings.model(),
                "failed",
                null,
                reason,
                latencyMs(startedAt)
        );
    }

    private Integer latencyMs(Instant startedAt) {
        return Math.toIntExact(Duration.between(startedAt, Instant.now()).toMillis());
    }

    private String buildInputText(RagEvidence evidence) {
        return evidence.documentTitle() + "\n" + evidence.sectionTitle() + "\n" + evidence.content();
    }

    public record RerankApiRequest(String model, String query, List<RerankInput> input) {
    }

    public record RerankInput(int index, String text) {
    }

    public record RerankApiResponse(String model, List<RerankItem> data) {
    }

    public record RerankItem(int index, int rank, Double score) {
    }
}
