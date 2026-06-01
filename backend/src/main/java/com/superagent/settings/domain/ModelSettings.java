package com.superagent.settings.domain;

public record ModelSettings(
        String provider,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        String apiKey
) {

    public boolean apiKeySet() {
        return apiKey != null && !apiKey.isBlank();
    }
}
