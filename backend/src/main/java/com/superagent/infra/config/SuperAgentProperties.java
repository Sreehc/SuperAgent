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
    private final Redis redis = new Redis();

    @Valid
    private final Security security = new Security();

    @Valid
    private final Web web = new Web();

    @Valid
    private final DocumentProcessing documentProcessing = new DocumentProcessing();

    @Valid
    private final Rag rag = new Rag();

    @Valid
    private final Agent agent = new Agent();

    @Valid
    private final Tools tools = new Tools();

    @Valid
    private final Graph graph = new Graph();

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

    public Redis getRedis() {
        return redis;
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

    public Agent getAgent() {
        return agent;
    }

    public Tools getTools() {
        return tools;
    }

    public Graph getGraph() {
        return graph;
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

        @NotNull
        private Boolean inlineProcessingWhenKafkaDisabled = false;

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

        public Boolean getInlineProcessingWhenKafkaDisabled() {
            return inlineProcessingWhenKafkaDisabled;
        }

        public void setInlineProcessingWhenKafkaDisabled(Boolean inlineProcessingWhenKafkaDisabled) {
            this.inlineProcessingWhenKafkaDisabled = inlineProcessingWhenKafkaDisabled;
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
        private String chatProvider = "local-fake";

        @NotBlank
        private String rerankProvider = "disabled";

        @NotBlank
        private String embeddingProvider = "openai-compatible";

        @Min(1)
        private int embeddingDimension = 1536;

        @Min(1)
        private int embeddingMaxAttempts = 3;

        @Min(0)
        private long embeddingRetryBackoffMillis = 200L;

        @Min(1)
        private long httpConnectTimeoutMillis = 3_000L;

        @Min(1)
        private long httpReadTimeoutMillis = 10_000L;

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

        public String getChatProvider() {
            return chatProvider;
        }

        public void setChatProvider(String chatProvider) {
            this.chatProvider = chatProvider;
        }

        public String getRerankProvider() {
            return rerankProvider;
        }

        public void setRerankProvider(String rerankProvider) {
            this.rerankProvider = rerankProvider;
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

        public int getEmbeddingMaxAttempts() {
            return embeddingMaxAttempts;
        }

        public void setEmbeddingMaxAttempts(int embeddingMaxAttempts) {
            this.embeddingMaxAttempts = embeddingMaxAttempts;
        }

        public long getEmbeddingRetryBackoffMillis() {
            return embeddingRetryBackoffMillis;
        }

        public void setEmbeddingRetryBackoffMillis(long embeddingRetryBackoffMillis) {
            this.embeddingRetryBackoffMillis = embeddingRetryBackoffMillis;
        }

        public long getHttpConnectTimeoutMillis() {
            return httpConnectTimeoutMillis;
        }

        public void setHttpConnectTimeoutMillis(long httpConnectTimeoutMillis) {
            this.httpConnectTimeoutMillis = httpConnectTimeoutMillis;
        }

        public long getHttpReadTimeoutMillis() {
            return httpReadTimeoutMillis;
        }

        public void setHttpReadTimeoutMillis(long httpReadTimeoutMillis) {
            this.httpReadTimeoutMillis = httpReadTimeoutMillis;
        }
    }

    public static class Redis {

        @NotBlank
        private String url = "redis://localhost:6379";

        @NotNull
        private Boolean enabled = true;

        @Min(1)
        private long conversationLockTtlSeconds = 300L;

        @Min(1)
        private long stopSignalTtlSeconds = 300L;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public long getConversationLockTtlSeconds() {
            return conversationLockTtlSeconds;
        }

        public void setConversationLockTtlSeconds(long conversationLockTtlSeconds) {
            this.conversationLockTtlSeconds = conversationLockTtlSeconds;
        }

        public long getStopSignalTtlSeconds() {
            return stopSignalTtlSeconds;
        }

        public void setStopSignalTtlSeconds(long stopSignalTtlSeconds) {
            this.stopSignalTtlSeconds = stopSignalTtlSeconds;
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
        private Boolean queryUnderstandingEnabled = true;

        @NotNull
        private Boolean decompositionEnabled = true;

        @NotNull
        private Boolean rewriteEnabled = true;

        @NotNull
        private Boolean subQuestionEnabled = true;

        @NotNull
        private Boolean versionConsistencyEnabled = true;

        @NotNull
        private Boolean neighborExpansionEnabled = true;

        @Min(1)
        private int vectorTopK = 20;

        @Min(1)
        private int keywordTopK = 20;

        @Min(1)
        private int candidateTopK = 24;

        @Min(1)
        private int rrfK = 60;

        @Min(0)
        private int neighborWindow = 1;

        @Min(1)
        private int maxChunksPerDocument = 3;

        @Min(1)
        private int evidenceLimit = 8;

        @Min(1)
        private int perQuestionEvidenceCharLimit = 4000;

        @Min(1)
        private int totalEvidenceCharLimit = 12000;

        @Min(1)
        private int maxEvidenceContentChars = 1600;

        @Min(0)
        private double minRelevanceScore = 0.35d;

        @Min(0)
        private double answerConfidenceThreshold = 0.55d;

        @Min(1)
        private int noEvidenceMinResults = 1;

        @NotNull
        private Boolean forceCitationEnabled = true;

        @Min(1)
        private int maxSubQuestions = 4;

        @NotNull
        private Boolean queryResultCacheEnabled = false;

        @Min(1)
        private long queryResultCacheTtlSeconds = 30L;

        public Boolean getQueryUnderstandingEnabled() {
            return queryUnderstandingEnabled;
        }

        public void setQueryUnderstandingEnabled(Boolean queryUnderstandingEnabled) {
            this.queryUnderstandingEnabled = queryUnderstandingEnabled;
        }

        public Boolean getDecompositionEnabled() {
            return decompositionEnabled;
        }

        public void setDecompositionEnabled(Boolean decompositionEnabled) {
            this.decompositionEnabled = decompositionEnabled;
        }

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

        public Boolean getVersionConsistencyEnabled() {
            return versionConsistencyEnabled;
        }

        public void setVersionConsistencyEnabled(Boolean versionConsistencyEnabled) {
            this.versionConsistencyEnabled = versionConsistencyEnabled;
        }

        public Boolean getNeighborExpansionEnabled() {
            return neighborExpansionEnabled;
        }

        public void setNeighborExpansionEnabled(Boolean neighborExpansionEnabled) {
            this.neighborExpansionEnabled = neighborExpansionEnabled;
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

        public int getCandidateTopK() {
            return candidateTopK;
        }

        public void setCandidateTopK(int candidateTopK) {
            this.candidateTopK = candidateTopK;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public int getNeighborWindow() {
            return neighborWindow;
        }

        public void setNeighborWindow(int neighborWindow) {
            this.neighborWindow = neighborWindow;
        }

        public int getMaxChunksPerDocument() {
            return maxChunksPerDocument;
        }

        public void setMaxChunksPerDocument(int maxChunksPerDocument) {
            this.maxChunksPerDocument = maxChunksPerDocument;
        }

        public int getEvidenceLimit() {
            return evidenceLimit;
        }

        public void setEvidenceLimit(int evidenceLimit) {
            this.evidenceLimit = evidenceLimit;
        }

        public int getPerQuestionEvidenceCharLimit() {
            return perQuestionEvidenceCharLimit;
        }

        public void setPerQuestionEvidenceCharLimit(int perQuestionEvidenceCharLimit) {
            this.perQuestionEvidenceCharLimit = perQuestionEvidenceCharLimit;
        }

        public int getTotalEvidenceCharLimit() {
            return totalEvidenceCharLimit;
        }

        public void setTotalEvidenceCharLimit(int totalEvidenceCharLimit) {
            this.totalEvidenceCharLimit = totalEvidenceCharLimit;
        }

        public int getMaxEvidenceContentChars() {
            return maxEvidenceContentChars;
        }

        public void setMaxEvidenceContentChars(int maxEvidenceContentChars) {
            this.maxEvidenceContentChars = maxEvidenceContentChars;
        }

        public double getMinRelevanceScore() {
            return minRelevanceScore;
        }

        public void setMinRelevanceScore(double minRelevanceScore) {
            this.minRelevanceScore = minRelevanceScore;
        }

        public double getAnswerConfidenceThreshold() {
            return answerConfidenceThreshold;
        }

        public void setAnswerConfidenceThreshold(double answerConfidenceThreshold) {
            this.answerConfidenceThreshold = answerConfidenceThreshold;
        }

        public int getNoEvidenceMinResults() {
            return noEvidenceMinResults;
        }

        public void setNoEvidenceMinResults(int noEvidenceMinResults) {
            this.noEvidenceMinResults = noEvidenceMinResults;
        }

        public Boolean getForceCitationEnabled() {
            return forceCitationEnabled;
        }

        public void setForceCitationEnabled(Boolean forceCitationEnabled) {
            this.forceCitationEnabled = forceCitationEnabled;
        }

        public int getMaxSubQuestions() {
            return maxSubQuestions;
        }

        public void setMaxSubQuestions(int maxSubQuestions) {
            this.maxSubQuestions = maxSubQuestions;
        }

        public Boolean getQueryResultCacheEnabled() {
            return queryResultCacheEnabled;
        }

        public void setQueryResultCacheEnabled(Boolean queryResultCacheEnabled) {
            this.queryResultCacheEnabled = queryResultCacheEnabled;
        }

        public long getQueryResultCacheTtlSeconds() {
            return queryResultCacheTtlSeconds;
        }

        public void setQueryResultCacheTtlSeconds(long queryResultCacheTtlSeconds) {
            this.queryResultCacheTtlSeconds = queryResultCacheTtlSeconds;
        }
    }

    public static class Agent {

        @NotBlank
        private String serviceBaseUrl = "http://localhost:18081";

        @NotNull
        private Boolean enabledDefault = false;

        @Min(1)
        private int maxModelSteps = 8;

        @Min(1)
        private int maxToolCalls = 10;

        @NotNull
        private Boolean checkpointEnabled = true;

        @NotNull
        private Boolean codeExecutionEnabled = false;

        public String getServiceBaseUrl() {
            return serviceBaseUrl;
        }

        public void setServiceBaseUrl(String serviceBaseUrl) {
            this.serviceBaseUrl = serviceBaseUrl;
        }

        public Boolean getEnabledDefault() {
            return enabledDefault;
        }

        public void setEnabledDefault(Boolean enabledDefault) {
            this.enabledDefault = enabledDefault;
        }

        public int getMaxModelSteps() {
            return maxModelSteps;
        }

        public void setMaxModelSteps(int maxModelSteps) {
            this.maxModelSteps = maxModelSteps;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public Boolean getCheckpointEnabled() {
            return checkpointEnabled;
        }

        public void setCheckpointEnabled(Boolean checkpointEnabled) {
            this.checkpointEnabled = checkpointEnabled;
        }

        public Boolean getCodeExecutionEnabled() {
            return codeExecutionEnabled;
        }

        public void setCodeExecutionEnabled(Boolean codeExecutionEnabled) {
            this.codeExecutionEnabled = codeExecutionEnabled;
        }
    }

    public static class Tools {

        @NotNull
        private Boolean webSearchEnabled = true;

        @NotNull
        private Boolean httpToolEnabled = false;

        @NotNull
        private Boolean graphToolEnabled = false;

        @NotNull
        private Boolean codeExecutionEnabled = false;

        @Min(100)
        private int toolTimeoutMs = 10000;

        @NotBlank
        private String searchProvider = "tavily";

        @NotNull
        private List<String> allowedHttpDomains = new ArrayList<>();

        public Boolean getWebSearchEnabled() {
            return webSearchEnabled;
        }

        public void setWebSearchEnabled(Boolean webSearchEnabled) {
            this.webSearchEnabled = webSearchEnabled;
        }

        public Boolean getHttpToolEnabled() {
            return httpToolEnabled;
        }

        public void setHttpToolEnabled(Boolean httpToolEnabled) {
            this.httpToolEnabled = httpToolEnabled;
        }

        public Boolean getGraphToolEnabled() {
            return graphToolEnabled;
        }

        public void setGraphToolEnabled(Boolean graphToolEnabled) {
            this.graphToolEnabled = graphToolEnabled;
        }

        public Boolean getCodeExecutionEnabled() {
            return codeExecutionEnabled;
        }

        public void setCodeExecutionEnabled(Boolean codeExecutionEnabled) {
            this.codeExecutionEnabled = codeExecutionEnabled;
        }

        public int getToolTimeoutMs() {
            return toolTimeoutMs;
        }

        public void setToolTimeoutMs(int toolTimeoutMs) {
            this.toolTimeoutMs = toolTimeoutMs;
        }

        public String getSearchProvider() {
            return searchProvider;
        }

        public void setSearchProvider(String searchProvider) {
            this.searchProvider = searchProvider;
        }

        public List<String> getAllowedHttpDomains() {
            return allowedHttpDomains;
        }

        public void setAllowedHttpDomains(List<String> allowedHttpDomains) {
            this.allowedHttpDomains = allowedHttpDomains;
        }
    }

    public static class Graph {

        @NotBlank
        private String neo4jUri = "bolt://localhost:7687";

        @NotBlank
        private String neo4jUsername = "neo4j";

        @NotBlank
        private String neo4jPassword = "password";

        @NotNull
        private Boolean enabled = false;

        public String getNeo4jUri() {
            return neo4jUri;
        }

        public void setNeo4jUri(String neo4jUri) {
            this.neo4jUri = neo4jUri;
        }

        public String getNeo4jUsername() {
            return neo4jUsername;
        }

        public void setNeo4jUsername(String neo4jUsername) {
            this.neo4jUsername = neo4jUsername;
        }

        public String getNeo4jPassword() {
            return neo4jPassword;
        }

        public void setNeo4jPassword(String neo4jPassword) {
            this.neo4jPassword = neo4jPassword;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
