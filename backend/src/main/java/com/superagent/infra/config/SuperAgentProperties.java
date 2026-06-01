package com.superagent.infra.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "super-agent")
public class SuperAgentProperties {

    @Valid
    private final App app = new App();

    @Valid
    private final Storage storage = new Storage();

    @Valid
    private final Messaging messaging = new Messaging();

    @Valid
    private final Ai ai = new Ai();

    @Valid
    private final Security security = new Security();

    @Valid
    private final Web web = new Web();

    @Valid
    private final DocumentProcessing documentProcessing = new DocumentProcessing();

    @Valid
    private final Rag rag = new Rag();

    public App getApp() {
        return app;
    }

    public Storage getStorage() {
        return storage;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public Ai getAi() {
        return ai;
    }

    public Security getSecurity() {
        return security;
    }

    public Web getWeb() {
        return web;
    }

    public DocumentProcessing getDocumentProcessing() {
        return documentProcessing;
    }

    public Rag getRag() {
        return rag;
    }

    public static class App {

        @NotBlank
        private String name;

        @NotBlank
        private String apiBasePath;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getApiBasePath() {
            return apiBasePath;
        }

        public void setApiBasePath(String apiBasePath) {
            this.apiBasePath = apiBasePath;
        }
    }

    public static class Storage {

        @NotBlank
        private String minioEndpoint;

        @NotBlank
        private String minioAccessKey;

        @NotBlank
        private String minioSecretKey;

        @NotBlank
        private String minioBucket;

        @NotNull
        private Boolean minioAutoCreateBucket;

        @Min(1)
        private long uploadMaxFileSizeBytes;

        @NotNull
        private List<@NotBlank String> uploadAllowedExtensions = new ArrayList<>();

        public String getMinioEndpoint() {
            return minioEndpoint;
        }

        public void setMinioEndpoint(String minioEndpoint) {
            this.minioEndpoint = minioEndpoint;
        }

        public String getMinioAccessKey() {
            return minioAccessKey;
        }

        public void setMinioAccessKey(String minioAccessKey) {
            this.minioAccessKey = minioAccessKey;
        }

        public String getMinioSecretKey() {
            return minioSecretKey;
        }

        public void setMinioSecretKey(String minioSecretKey) {
            this.minioSecretKey = minioSecretKey;
        }

        public String getMinioBucket() {
            return minioBucket;
        }

        public void setMinioBucket(String minioBucket) {
            this.minioBucket = minioBucket;
        }

        public Boolean getMinioAutoCreateBucket() {
            return minioAutoCreateBucket;
        }

        public void setMinioAutoCreateBucket(Boolean minioAutoCreateBucket) {
            this.minioAutoCreateBucket = minioAutoCreateBucket;
        }

        public long getUploadMaxFileSizeBytes() {
            return uploadMaxFileSizeBytes;
        }

        public void setUploadMaxFileSizeBytes(long uploadMaxFileSizeBytes) {
            this.uploadMaxFileSizeBytes = uploadMaxFileSizeBytes;
        }

        public List<String> getUploadAllowedExtensions() {
            return uploadAllowedExtensions;
        }

        public void setUploadAllowedExtensions(List<String> uploadAllowedExtensions) {
            this.uploadAllowedExtensions = uploadAllowedExtensions;
        }
    }

    public static class Messaging {

        @NotBlank
        private String bootstrapServers;

        @NotBlank
        private String documentTaskTopic;

        @NotNull
        private Boolean kafkaEnabled;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getDocumentTaskTopic() {
            return documentTaskTopic;
        }

        public void setDocumentTaskTopic(String documentTaskTopic) {
            this.documentTaskTopic = documentTaskTopic;
        }

        public Boolean getKafkaEnabled() {
            return kafkaEnabled;
        }

        public void setKafkaEnabled(Boolean kafkaEnabled) {
            this.kafkaEnabled = kafkaEnabled;
        }
    }

    public static class Ai {

        @NotBlank
        private String openaiCompatibleBaseUrl;

        @NotBlank
        private String apiKey;

        @NotBlank
        private String chatModel;

        @NotBlank
        private String embeddingModel;

        @NotNull
        private Boolean rerankEnabled;

        @NotBlank
        private String embeddingProvider = "openai-compatible";

        @Min(1)
        private int embeddingDimension = 1536;

        public String getOpenaiCompatibleBaseUrl() {
            return openaiCompatibleBaseUrl;
        }

        public void setOpenaiCompatibleBaseUrl(String openaiCompatibleBaseUrl) {
            this.openaiCompatibleBaseUrl = openaiCompatibleBaseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Boolean getRerankEnabled() {
            return rerankEnabled;
        }

        public void setRerankEnabled(Boolean rerankEnabled) {
            this.rerankEnabled = rerankEnabled;
        }

        public String getEmbeddingProvider() {
            return embeddingProvider;
        }

        public void setEmbeddingProvider(String embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }
    }

    public static class Security {

        @NotBlank
        private String jwtSecret;

        @Min(1)
        private long accessTokenTtlSeconds;

        @Min(1)
        private long refreshTokenTtlSeconds;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getAccessTokenTtlSeconds() {
            return accessTokenTtlSeconds;
        }

        public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
            this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        }

        public long getRefreshTokenTtlSeconds() {
            return refreshTokenTtlSeconds;
        }

        public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
            this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        }
    }

    public static class Web {

        @NotNull
        private List<@NotBlank String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class DocumentProcessing {

        @Min(128)
        private int chunkSize = 1000;

        @Min(0)
        private int chunkOverlap = 120;

        @Min(1)
        private int maxChunkCount = 5000;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getMaxChunkCount() {
            return maxChunkCount;
        }

        public void setMaxChunkCount(int maxChunkCount) {
            this.maxChunkCount = maxChunkCount;
        }
    }

    public static class Rag {

        @NotNull
        private Boolean rewriteEnabled = true;

        @NotNull
        private Boolean subQuestionEnabled = true;

        @Min(1)
        private int vectorTopK = 20;

        @Min(1)
        private int keywordTopK = 20;

        @Min(1)
        private int rrfK = 60;

        @Min(1)
        private int evidenceLimit = 8;

        @Min(0)
        private double minRelevanceScore = 0.35d;

        @Min(1)
        private int maxSubQuestions = 4;

        public Boolean getRewriteEnabled() {
            return rewriteEnabled;
        }

        public void setRewriteEnabled(Boolean rewriteEnabled) {
            this.rewriteEnabled = rewriteEnabled;
        }

        public Boolean getSubQuestionEnabled() {
            return subQuestionEnabled;
        }

        public void setSubQuestionEnabled(Boolean subQuestionEnabled) {
            this.subQuestionEnabled = subQuestionEnabled;
        }

        public int getVectorTopK() {
            return vectorTopK;
        }

        public void setVectorTopK(int vectorTopK) {
            this.vectorTopK = vectorTopK;
        }

        public int getKeywordTopK() {
            return keywordTopK;
        }

        public void setKeywordTopK(int keywordTopK) {
            this.keywordTopK = keywordTopK;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public int getEvidenceLimit() {
            return evidenceLimit;
        }

        public void setEvidenceLimit(int evidenceLimit) {
            this.evidenceLimit = evidenceLimit;
        }

        public double getMinRelevanceScore() {
            return minRelevanceScore;
        }

        public void setMinRelevanceScore(double minRelevanceScore) {
            this.minRelevanceScore = minRelevanceScore;
        }

        public int getMaxSubQuestions() {
            return maxSubQuestions;
        }

        public void setMaxSubQuestions(int maxSubQuestions) {
            this.maxSubQuestions = maxSubQuestions;
        }
    }
}
