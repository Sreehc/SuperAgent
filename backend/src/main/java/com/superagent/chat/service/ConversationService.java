package com.superagent.chat.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.ConversationSession;
import com.superagent.chat.domain.ConversationStatus;
import com.superagent.chat.domain.ExecutionMode;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.domain.MessageRole;
import com.superagent.chat.repository.ConversationRepository;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.rag.domain.RagEvidence;
import com.superagent.rag.domain.RagResponse;
import com.superagent.rag.domain.RagResponseDiagnostics;
import com.superagent.rag.domain.RetrievalResult;
import com.superagent.rag.service.RagOrchestrationService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ConversationService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final ConversationRepository conversationRepository;
    private final RagOrchestrationService ragOrchestrationService;
    private final ConversationStreamRegistry streamRegistry;
    private final Executor conversationExecutor = new SimpleAsyncTaskExecutor("conversation-stream-");

    public ConversationService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            ConversationRepository conversationRepository,
            RagOrchestrationService ragOrchestrationService,
            ConversationStreamRegistry streamRegistry
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.conversationRepository = conversationRepository;
        this.ragOrchestrationService = ragOrchestrationService;
        this.streamRegistry = streamRegistry;
    }

    public ConversationSession createConversation(String title, Long knowledgeBaseId, MemoryStrategy memoryStrategy) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        String resolvedTitle = (title == null || title.isBlank()) ? "新会话" : title.trim();
        return conversationRepository.createSession(
                tenantContext.tenantId(),
                principal.userId(),
                resolvedTitle,
                memoryStrategy == null ? MemoryStrategy.NONE : memoryStrategy,
                knowledgeBaseId
        );
    }

    public PagedResult<ConversationSession> listConversations(Integer page, Integer pageSize, String status, String keyword) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 20, 100);
        boolean tenantAdmin = isTenantAdmin(principal);

        long total = conversationRepository.countVisibleSessions(
                tenantContext.tenantId(),
                principal.userId(),
                tenantAdmin,
                status,
                keyword
        );
        List<ConversationSession> items = conversationRepository.findVisibleSessions(
                tenantContext.tenantId(),
                principal.userId(),
                tenantAdmin,
                status,
                keyword,
                resolvedPage,
                resolvedPageSize
        );
        return new PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public ConversationSession getConversation(long sessionId) {
        return requireAccessibleSession(sessionId);
    }

    public ConversationSession updateConversation(long sessionId, String title, Long knowledgeBaseId, MemoryStrategy memoryStrategy, ConversationStatus status) {
        ConversationSession existing = requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        String resolvedTitle = (title == null || title.isBlank()) ? existing.title() : title.trim();
        MemoryStrategy resolvedMemoryStrategy = memoryStrategy == null ? existing.memoryStrategy() : memoryStrategy;
        Long resolvedKnowledgeBaseId = knowledgeBaseId == null ? existing.knowledgeBaseId() : knowledgeBaseId;
        ConversationStatus resolvedStatus = status == null ? existing.status() : status;
        return conversationRepository.updateSession(
                existing.id(),
                tenantContext.tenantId(),
                resolvedTitle,
                resolvedMemoryStrategy,
                resolvedKnowledgeBaseId,
                resolvedStatus
        );
    }

    public boolean deleteConversation(long sessionId) {
        ConversationSession session = requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        return conversationRepository.softDeleteSession(session.id(), tenantContext.tenantId());
    }

    public PagedResult<ConversationMessage> listMessages(long sessionId, Integer page, Integer pageSize) {
        ConversationSession session = requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = normalizePage(page);
        int resolvedPageSize = normalizePageSize(pageSize, 50, 200);
        long total = conversationRepository.countMessages(session.id(), tenantContext.tenantId());
        List<ConversationMessage> items = conversationRepository.findMessages(session.id(), tenantContext.tenantId(), resolvedPage, resolvedPageSize);
        return new PagedResult<>(items, resolvedPage, resolvedPageSize, total);
    }

    public SseEmitter streamMessage(long sessionId, StreamRequest request) {
        ConversationSession session = requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();

        if (streamRegistry.get(tenantContext.tenantId(), sessionId) != null) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Another generation is already running for this session");
        }

        SseEmitter emitter = new SseEmitter(0L);
        ConversationStreamRegistry.ActiveConversation activeConversation =
                streamRegistry.register(tenantContext.tenantId(), sessionId, emitter);
        if (activeConversation == null) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Another generation is already running for this session");
        }

        emitter.onCompletion(() -> streamRegistry.remove(tenantContext.tenantId(), sessionId));
        emitter.onTimeout(() -> streamRegistry.remove(tenantContext.tenantId(), sessionId));
        emitter.onError(error -> streamRegistry.remove(tenantContext.tenantId(), sessionId));

        conversationExecutor.execute(() -> {
            TenantContextHolder.set(tenantContext);
            try {
                generateConversation(session, principal, tenantContext, request, activeConversation);
            } finally {
                TenantContextHolder.clear();
            }
        });
        return emitter;
    }

    public StopResult stopConversation(long sessionId) {
        requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        ConversationStreamRegistry.ActiveConversation activeConversation = streamRegistry.get(tenantContext.tenantId(), sessionId);
        if (activeConversation == null) {
            return new StopResult(false, sessionId);
        }
        activeConversation.requestStop();
        return new StopResult(true, sessionId);
    }

    private void generateConversation(
            ConversationSession session,
            AuthenticatedUserPrincipal principal,
            TenantContext tenantContext,
            StreamRequest request,
            ConversationStreamRegistry.ActiveConversation activeConversation
    ) {
        long tenantId = tenantContext.tenantId();
        long sessionId = session.id();
        SseEmitter emitter = activeConversation.emitter();

        ConversationMessage userMessage = null;
        ConversationMessage assistantMessage = null;
        long exchangeId = -1L;
        try {
            MemoryStrategy resolvedMemoryStrategy = request.memoryStrategy() == null ? session.memoryStrategy() : request.memoryStrategy();
            Long resolvedKnowledgeBaseId = request.knowledgeBaseId() == null ? session.knowledgeBaseId() : request.knowledgeBaseId();
            conversationRepository.updateSessionDefaults(sessionId, tenantId, resolvedMemoryStrategy, resolvedKnowledgeBaseId);

            userMessage = conversationRepository.createMessageWithMetadata(
                    tenantId,
                    sessionId,
                    MessageRole.user,
                    request.message().trim(),
                    "success",
                    null,
                    Map.of("userId", principal.userId())
            );
            conversationRepository.touchSession(sessionId, tenantId, userMessage.createdAt());

            var exchange = conversationRepository.createExchange(
                    tenantId,
                    sessionId,
                    userMessage.id(),
                    ExecutionMode.RAG_QA,
                    "running",
                    resolvedKnowledgeBaseId == null ? "direct_chat_with_memory_only" : "rag_with_knowledge_base",
                    BigDecimal.valueOf(0.92d)
            );
            exchangeId = exchange.id();

            sendEvent(emitter, "start", new StartEvent(exchangeId, userMessage.id()));
            List<String> recentMessages = conversationRepository.findMessages(sessionId, tenantId, 1, 6).stream()
                    .map(ConversationMessage::content)
                    .toList();
            emitTraceStage(exchangeId, tenantId, emitter, "memory_assembly", "recent_messages=" + recentMessages.size(), "已组装最近会话记忆");
            RagResponse ragResponse = ragOrchestrationService.answer(
                    request.message().trim(),
                    resolvedKnowledgeBaseId,
                    recentMessages,
                    request.ragOptions()
            );
            emitTraceStage(exchangeId, tenantId, emitter, "query_rewrite", "original=" + excerpt(request.message().trim()), ragResponse.rewrittenQuestion());
            if (ragResponse.subQuestions().size() > 1) {
                emitTraceStage(exchangeId, tenantId, emitter, "sub_question_split", "count=" + ragResponse.subQuestions().size(), String.join(" | ", ragResponse.subQuestions()));
            }
            persistRetrievalTrace(exchangeId, tenantId, emitter, ragResponse.diagnostics());
            persistRerankTrace(exchangeId, tenantId, emitter, ragResponse.diagnostics());
            emitTraceStage(exchangeId, tenantId, emitter, "prompt_assembly", "question=" + excerpt(ragResponse.rewrittenQuestion()), ragResponse.diagnostics().promptSummary());

            StringBuilder fullText = new StringBuilder();
            long modelStageId = createTraceStage(exchangeId, tenantId, "answer_generation", "prompt=" + ragResponse.diagnostics().promptSummary());
            long generationStartedAt = System.nanoTime();
            for (String delta : ragResponse.answer().deltas()) {
                if (activeConversation.stopRequested()) {
                    break;
                }
                fullText.append(delta);
                sendEvent(emitter, "delta", new DeltaEvent(delta));
                sleep(100L);
            }
            completeTraceStage(modelStageId, tenantId, "success", ragResponse.diagnostics().modelSummary(), null);
            conversationRepository.createModelCallTrace(
                    tenantId,
                    exchangeId,
                    modelStageId,
                    "openai-compatible",
                    "chat-model",
                    "chat",
                    ragResponse.diagnostics().promptSummary(),
                    excerpt(fullText.toString()),
                    null,
                    null,
                    (int) ((System.nanoTime() - generationStartedAt) / 1_000_000L),
                    activeConversation.stopRequested() ? "stopped" : "success",
                    null,
                    Map.of(
                            "rewrittenQuestion", ragResponse.rewrittenQuestion(),
                            "recommendationCount", ragResponse.answer().recommendations().size()
                    )
            );
            sendEvent(emitter, "trace_stage", new TraceStageEvent("answer_generation", "success", 80L));

            String assistantStatus = activeConversation.stopRequested() ? "stopped" : "success";
            assistantMessage = conversationRepository.createMessage(
                    tenantId,
                    sessionId,
                    MessageRole.assistant,
                    fullText.toString(),
                    assistantStatus,
                    null
            );
            conversationRepository.touchSession(sessionId, tenantId, assistantMessage.createdAt());

            for (int index = 0; index < ragResponse.evidences().size(); index++) {
                RagEvidence candidate = ragResponse.evidences().get(index);
                var reference = conversationRepository.createReference(
                        tenantId,
                        exchangeId,
                        candidate.documentId(),
                        candidate.chunkId(),
                        index + 1,
                        candidate.documentTitle(),
                        excerpt(candidate.content()),
                        BigDecimal.valueOf(candidate.score()),
                        "/documents/" + candidate.documentId()
                );
                sendEvent(emitter, "reference", new ReferenceEvent(
                        reference.ordinal(),
                        reference.documentId(),
                        reference.chunkId(),
                        reference.title(),
                        reference.quote(),
                        reference.score()
                ));
            }

            if (!activeConversation.stopRequested()) {
                sendEvent(emitter, "recommendation", new RecommendationEvent(ragResponse.answer().recommendations()));
            }

            conversationRepository.completeExchange(
                    exchangeId,
                    tenantId,
                    assistantMessage.id(),
                    activeConversation.stopRequested() ? "stopped" : "success"
            );

            sendEvent(emitter, "done", new DoneEvent(exchangeId, assistantMessage.id(), activeConversation.stopRequested()));
            emitter.complete();
        } catch (AppException exception) {
            handleStreamError(tenantId, sessionId, exchangeId, emitter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            handleStreamError(tenantId, sessionId, exchangeId, emitter, ErrorCode.MODEL_PROVIDER_ERROR, "RAG generation failed");
        } finally {
            streamRegistry.remove(tenantId, sessionId);
        }
    }

    private void emitTraceStage(long exchangeId, long tenantId, SseEmitter emitter, String stageCode, String inputSummary, String outputSummary) {
        var stage = conversationRepository.createTraceStage(tenantId, exchangeId, stageCode, "running", inputSummary);
        conversationRepository.completeTraceStage(stage.id(), tenantId, "success", outputSummary, null);
        sendEvent(emitter, "trace_stage", new TraceStageEvent(stageCode, "success", 80L));
    }

    private long createTraceStage(long exchangeId, long tenantId, String stageCode, String inputSummary) {
        return conversationRepository.createTraceStage(tenantId, exchangeId, stageCode, "running", inputSummary).id();
    }

    private void completeTraceStage(long stageId, long tenantId, String status, String outputSummary, String errorMessage) {
        conversationRepository.completeTraceStage(stageId, tenantId, status, outputSummary, errorMessage);
    }

    private void persistRetrievalTrace(
            long exchangeId,
            long tenantId,
            SseEmitter emitter,
            RagResponseDiagnostics diagnostics
    ) {
        if (diagnostics == null) {
            return;
        }

        long vectorStageId = createTraceStage(exchangeId, tenantId, "vector_retrieval", diagnostics.memorySummary());
        long keywordStageId = createTraceStage(exchangeId, tenantId, "keyword_retrieval", diagnostics.memorySummary());
        long fusionStageId = createTraceStage(exchangeId, tenantId, "rrf_fusion", "sub_questions=" + diagnostics.retrievalSteps().size());
        long budgetStageId = createTraceStage(exchangeId, tenantId, "evidence_budget", "candidate_steps=" + diagnostics.retrievalSteps().size());

        int selectedTotal = 0;
        for (RagResponseDiagnostics.RetrievalStep step : diagnostics.retrievalSteps()) {
            selectedTotal += step.selectedResults().size();
            long vectorTraceId = conversationRepository.createRetrievalTrace(
                    tenantId,
                    exchangeId,
                    vectorStageId,
                    step.query().subQuestionNo(),
                    "vector",
                    step.query().subQuestion(),
                    buildRetrievalFilters(step.query()),
                    step.vectorResults().size(),
                    countSelectedChunks(step.vectorResults(), step.selectedResults()),
                    null
            );
            persistRetrievalResultItems(tenantId, vectorTraceId, step.vectorResults(), step.selectedResults());

            long keywordTraceId = conversationRepository.createRetrievalTrace(
                    tenantId,
                    exchangeId,
                    keywordStageId,
                    step.query().subQuestionNo(),
                    "keyword",
                    step.query().subQuestion(),
                    buildRetrievalFilters(step.query()),
                    step.keywordResults().size(),
                    countSelectedChunks(step.keywordResults(), step.selectedResults()),
                    null
            );
            persistRetrievalResultItems(tenantId, keywordTraceId, step.keywordResults(), step.selectedResults());

            long fusedTraceId = conversationRepository.createRetrievalTrace(
                    tenantId,
                    exchangeId,
                    fusionStageId,
                    step.query().subQuestionNo(),
                    "rrf",
                    step.query().subQuestion(),
                    buildRetrievalFilters(step.query()),
                    step.fusedResults().size(),
                    step.selectedResults().size(),
                    null
            );
            persistFusedItems(tenantId, fusedTraceId, step.fusedResults(), step.selectedResults());
        }

        completeTraceStage(vectorStageId, tenantId, "success", "vector_retrieval_steps=" + diagnostics.retrievalSteps().size(), null);
        completeTraceStage(keywordStageId, tenantId, "success", "keyword_retrieval_steps=" + diagnostics.retrievalSteps().size(), null);
        completeTraceStage(fusionStageId, tenantId, "success", "fused_evidence_count=" + selectedTotal, null);
        completeTraceStage(budgetStageId, tenantId, "success", "selected_evidence_count=" + selectedTotal, null);

        sendEvent(emitter, "trace_stage", new TraceStageEvent("vector_retrieval", "success", 80L));
        sendEvent(emitter, "trace_stage", new TraceStageEvent("keyword_retrieval", "success", 80L));
        sendEvent(emitter, "trace_stage", new TraceStageEvent("rrf_fusion", "success", 80L));
        sendEvent(emitter, "trace_stage", new TraceStageEvent("evidence_budget", "success", 80L));
    }

    private void persistRerankTrace(
            long exchangeId,
            long tenantId,
            SseEmitter emitter,
            RagResponseDiagnostics diagnostics
    ) {
        if (diagnostics == null || diagnostics.rerankStep() == null) {
            return;
        }
        RagResponseDiagnostics.RerankStep rerankStep = diagnostics.rerankStep();
        var stage = conversationRepository.createTraceStage(
                tenantId,
                exchangeId,
                "rerank",
                "running",
                "enabled=" + rerankStep.enabled() + ", input=" + rerankStep.inputCount()
        );
        conversationRepository.createRerankTrace(
                tenantId,
                exchangeId,
                rerankStep.provider(),
                rerankStep.model(),
                rerankStep.enabled(),
                rerankStep.skippedReason(),
                rerankStep.inputCount(),
                rerankStep.outputCount(),
                null,
                rerankStep.status(),
                null,
                Map.of()
        );
        completeTraceStage(
                stage.id(),
                tenantId,
                rerankStep.status(),
                rerankStep.enabled() ? "reranked=" + rerankStep.outputCount() : rerankStep.skippedReason(),
                null
        );
        sendEvent(emitter, "trace_stage", new TraceStageEvent("rerank", rerankStep.status(), 80L));
    }

    private void persistRetrievalResultItems(long tenantId, long retrievalTraceId, List<RetrievalResult> results, List<RagEvidence> selectedResults) {
        for (int index = 0; index < results.size(); index++) {
            RetrievalResult result = results.get(index);
            conversationRepository.createRetrievalTraceItem(
                    tenantId,
                    retrievalTraceId,
                    result.documentId(),
                    result.chunkId(),
                    index + 1,
                    BigDecimal.valueOf(result.score()),
                    null,
                    containsChunk(selectedResults, result.chunkId()),
                    buildTraceMetadata(result.documentTitle(), result.sectionTitle(), null)
            );
        }
    }

    private void persistFusedItems(long tenantId, long retrievalTraceId, List<RagEvidence> fusedResults, List<RagEvidence> selectedResults) {
        for (int index = 0; index < fusedResults.size(); index++) {
            RagEvidence result = fusedResults.get(index);
            conversationRepository.createRetrievalTraceItem(
                    tenantId,
                    retrievalTraceId,
                    result.documentId(),
                    result.chunkId(),
                    index + 1,
                    BigDecimal.valueOf(result.score()),
                    BigDecimal.valueOf(result.score()),
                    containsChunk(selectedResults, result.chunkId()),
                    buildTraceMetadata(result.documentTitle(), result.sectionTitle(), result.channel())
            );
        }
    }

    private int countSelectedChunks(List<RetrievalResult> results, List<RagEvidence> selectedResults) {
        int count = 0;
        for (RetrievalResult result : results) {
            if (containsChunk(selectedResults, result.chunkId())) {
                count++;
            }
        }
        return count;
    }

    private boolean containsChunk(List<RagEvidence> selectedResults, long chunkId) {
        return selectedResults.stream().anyMatch(evidence -> evidence.chunkId() == chunkId);
    }

    private Map<String, Object> buildTraceMetadata(String documentTitle, String sectionTitle, String channel) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (documentTitle != null && !documentTitle.isBlank()) {
            metadata.put("documentTitle", documentTitle);
        }
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            metadata.put("sectionTitle", sectionTitle);
        }
        if (channel != null && !channel.isBlank()) {
            metadata.put("channel", channel);
        }
        return metadata;
    }

    private Map<String, Object> buildRetrievalFilters(RagResponseDiagnostics.RetrievalStep step) {
        return buildRetrievalFilters(step.query());
    }

    private Map<String, Object> buildRetrievalFilters(com.superagent.rag.domain.RagSearchQuery query) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("knowledgeBaseId", query.knowledgeBaseId());
        filters.put("vectorTopK", query.vectorTopK());
        filters.put("keywordTopK", query.keywordTopK());
        filters.put("rrfK", query.rrfK());
        filters.put("evidenceLimit", query.evidenceLimit());
        filters.put("minRelevanceScore", query.minRelevanceScore());
        return filters;
    }

    private void handleStreamError(long tenantId, long sessionId, long exchangeId, SseEmitter emitter, ErrorCode code, String message) {
        try {
            if (exchangeId > 0) {
                var errorStage = conversationRepository.createTraceStage(
                        tenantId,
                        exchangeId,
                        "error",
                        "failed",
                        "code=" + code.name()
                );
                conversationRepository.completeTraceStage(errorStage.id(), tenantId, "failed", null, message);
                conversationRepository.createModelCallTrace(
                        tenantId,
                        exchangeId,
                        errorStage.id(),
                        "openai-compatible",
                        "chat-model",
                        "chat",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "failed",
                        message,
                        Map.of("errorCode", code.name())
                );
                conversationRepository.completeExchange(exchangeId, tenantId, null, "failed");
            }
            sendEvent(emitter, "error", new ErrorEvent(code.name(), message, exchangeId > 0 ? exchangeId : null));
        } catch (Exception ignored) {
            // Keep stream shutdown best-effort.
        } finally {
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write SSE event");
        }
    }

    private ConversationSession requireAccessibleSession(long sessionId) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        return conversationRepository.findAccessibleSession(
                        sessionId,
                        tenantContext.tenantId(),
                        principal.userId(),
                        isTenantAdmin(principal)
                )
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Conversation session not found"));
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private boolean isTenantAdmin(AuthenticatedUserPrincipal principal) {
        return principal.currentRole() == TenantRole.OWNER || principal.currentRole() == TenantRole.ADMIN;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize, int defaultValue, int maxValue) {
        if (pageSize == null || pageSize < 1) {
            return defaultValue;
        }
        return Math.min(pageSize, maxValue);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Conversation stream interrupted");
        }
    }

    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 220 ? content : content.substring(0, 220);
    }

    public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
    }

    public record StreamRequest(
            String message,
            Long knowledgeBaseId,
            MemoryStrategy memoryStrategy,
            RagOptions ragOptions
    ) {
    }

    public record RagOptions(
            Boolean rewriteEnabled,
            Boolean subQuestionEnabled,
            Integer vectorTopK,
            Integer keywordTopK,
            Integer rrfK,
            Boolean rerankEnabled,
            Integer evidenceLimit,
            Double minRelevanceScore
    ) {
    }

    public record StopResult(boolean stopped, long sessionId) {
    }

    public record StartEvent(long exchangeId, long messageId) {
    }

    public record TraceStageEvent(String stage, String status, long durationMs) {
    }

    public record DeltaEvent(String text) {
    }

    public record ReferenceEvent(
            int ordinal,
            long documentId,
            long chunkId,
            String title,
            String quote,
            BigDecimal score
    ) {
    }

    public record RecommendationEvent(List<String> questions) {
    }

    public record DoneEvent(long exchangeId, long assistantMessageId, boolean stopped) {
    }

    public record ErrorEvent(String code, String message, Long exchangeId) {
    }
}
