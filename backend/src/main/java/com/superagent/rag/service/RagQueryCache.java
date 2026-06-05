package com.superagent.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.infra.config.SuperAgentProperties;
import com.superagent.rag.domain.RetrievalResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RagQueryCache {

    private static final TypeReference<List<RetrievalResult>> RETRIEVAL_RESULT_LIST = new TypeReference<>() {
    };

    private final SuperAgentProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Map<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    public RagQueryCache(
            SuperAgentProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public CachedRetrievalResult getOrLoad(
            String channel,
            long tenantId,
            Long knowledgeBaseId,
            Long knowledgeDomainId,
            Long chunkingProfileId,
            String category,
            List<String> tags,
            String query,
            int topK,
            boolean versionConsistencyEnabled,
            boolean cacheEnabled,
            long ttlSeconds,
            Supplier<List<RetrievalResult>> loader
    ) {
        if (!cacheEnabled) {
            List<RetrievalResult> results = markCacheHit(channel, loader.get(), false);
            recordRequest(channel, false);
            return new CachedRetrievalResult(results, false);
        }

        String cacheKey = buildCacheKey(
                channel,
                tenantId,
                knowledgeBaseId,
                knowledgeDomainId,
                chunkingProfileId,
                category,
                tags,
                query,
                topK,
                versionConsistencyEnabled
        );
        List<RetrievalResult> cached = readRedis(cacheKey);
        if (cached == null) {
            cached = readLocal(cacheKey);
        }
        if (cached != null) {
            recordRequest(channel, true);
            return new CachedRetrievalResult(markCacheHit(channel, cached, true), true);
        }

        List<RetrievalResult> loaded = loader.get();
        writeLocal(cacheKey, loaded, ttlSeconds);
        writeRedis(cacheKey, loaded, ttlSeconds);
        recordRequest(channel, false);
        return new CachedRetrievalResult(markCacheHit(channel, loaded, false), false);
    }

    private List<RetrievalResult> readRedis(String cacheKey) {
        StringRedisTemplate redisTemplate = activeRedisTemplate();
        if (redisTemplate == null) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(cacheKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, RETRIEVAL_RESULT_LIST);
        } catch (Exception exception) {
            return null;
        }
    }

    private void writeRedis(String cacheKey, List<RetrievalResult> results, long ttlSeconds) {
        StringRedisTemplate redisTemplate = activeRedisTemplate();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(results),
                    Duration.ofSeconds(normalizeTtlSeconds(ttlSeconds))
            );
        } catch (Exception ignored) {
        }
    }

    private List<RetrievalResult> readLocal(String cacheKey) {
        LocalCacheEntry entry = localCache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            localCache.remove(cacheKey);
            return null;
        }
        return entry.results();
    }

    private void writeLocal(String cacheKey, List<RetrievalResult> results, long ttlSeconds) {
        localCache.put(
                cacheKey,
                new LocalCacheEntry(
                        results,
                        Instant.now().plusSeconds(normalizeTtlSeconds(ttlSeconds))
                )
        );
    }

    private StringRedisTemplate activeRedisTemplate() {
        if (!Boolean.TRUE.equals(properties.getRedis().getEnabled())) {
            return null;
        }
        return redisTemplateProvider.getIfAvailable();
    }

    private List<RetrievalResult> markCacheHit(String channel, List<RetrievalResult> results, boolean cacheHit) {
        return results.stream()
                .map(result -> new RetrievalResult(
                        result.channel(),
                        result.knowledgeBaseId(),
                        result.documentId(),
                        result.chunkId(),
                        result.documentTitle(),
                        result.chunkNo(),
                        result.content(),
                        result.sectionTitle(),
                        result.score(),
                        mergeMetadata(result.metadata(), channel, cacheHit)
                ))
                .toList();
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> metadata, String channel, boolean cacheHit) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            merged.putAll(metadata);
        }
        merged.put("queryCacheHit", cacheHit);
        merged.put("queryCacheChannel", channel);
        return merged;
    }

    private void recordRequest(String channel, boolean cacheHit) {
        Counter.builder("superagent.rag.query_cache.requests")
                .tag("channel", channel)
                .tag("result", cacheHit ? "hit" : "miss")
                .register(meterRegistry)
                .increment();
    }

    private String buildCacheKey(
            String channel,
            long tenantId,
            Long knowledgeBaseId,
            Long knowledgeDomainId,
            Long chunkingProfileId,
            String category,
            List<String> tags,
            String query,
            int topK,
            boolean versionConsistencyEnabled
    ) {
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedTags = tags == null || tags.isEmpty() ? "" : String.join("|", tags.stream().sorted().toList());
        return "rag:query-cache:%s:t%d:kb%s:top%d:vc%s:q%s".formatted(
                channel,
                tenantId,
                "%s:kd%s:cp%s:c%s:t%s".formatted(
                        knowledgeBaseId == null ? "all" : knowledgeBaseId,
                        knowledgeDomainId == null ? "all" : knowledgeDomainId,
                        chunkingProfileId == null ? "all" : chunkingProfileId,
                        digest(normalizedCategory),
                        digest(normalizedTags)
                ),
                topK,
                versionConsistencyEnabled,
                digest(normalizedQuery)
        );
    }

    private String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            return Integer.toHexString(Objects.hashCode(value));
        }
    }

    private long normalizeTtlSeconds(long ttlSeconds) {
        return ttlSeconds > 0 ? ttlSeconds : properties.getRag().getQueryResultCacheTtlSeconds();
    }

    public record CachedRetrievalResult(
            List<RetrievalResult> results,
            boolean cacheHit
    ) {
    }

    private record LocalCacheEntry(
            List<RetrievalResult> results,
            Instant expiresAt
    ) {
    }
}
