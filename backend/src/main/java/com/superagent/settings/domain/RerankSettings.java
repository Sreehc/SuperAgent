package com.superagent.settings.domain;

public record RerankSettings(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        String apiKey
) {

    public boolean apiKeySet() {
        return apiKey != null && !apiKey.isBlank();
    }
}
