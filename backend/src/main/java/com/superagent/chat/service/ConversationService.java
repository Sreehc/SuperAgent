package com.superagent.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.agent.service.AgentGatewayClient;
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
import java.util.ArrayList;
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

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final ConversationRepository conversationRepository;
    private final RagOrchestrationService ragOrchestrationService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationStreamRegistry streamRegistry;
    private final ConversationRunLockManager conversationRunLockManager;
    private final ConversationExecutionPlanner conversationExecutionPlanner;
    private final AgentGatewayClient agentGatewayClient;
    private final ObjectMapper objectMapper;
    private final Executor conversationExecutor = new SimpleAsyncTaskExecutor("conversation-stream-");

    public ConversationService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            ConversationRepository conversationRepository,
            RagOrchestrationService ragOrchestrationService,
            ConversationMemoryService conversationMemoryService,
            ConversationStreamRegistry streamRegistry,
            ConversationRunLockManager conversationRunLockManager,
            ConversationExecutionPlanner conversationExecutionPlanner,
            AgentGatewayClient agentGatewayClient,
            ObjectMapper objectMapper
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.conversationRepository = conversationRepository;
        this.ragOrchestrationService = ragOrchestrationService;
        this.conversationMemoryService = conversationMemoryService;
        this.streamRegistry = streamRegistry;
        this.conversationRunLockManager = conversationRunLockManager;
        this.conversationExecutionPlanner = conversationExecutionPlanner;
        this.agentGatewayClient = agentGatewayClient;
        this.objectMapper = objectMapper;
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

        ConversationRunLockManager.ConversationRunLock runLock =
                conversationRunLockManager.acquire(tenantContext.tenantId(), sessionId);
        if (runLock == null) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Another generation is already running for this session");
        }

        SseEmitter emitter = new SseEmitter(0L);
        ConversationStreamRegistry.ActiveConversation activeConversation =
                streamRegistry.register(tenantContext.tenantId(), sessionId, emitter, runLock);
        if (activeConversation == null) {
            conversationRunLockManager.release(tenantContext.tenantId(), sessionId, runLock.ownerToken());
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Another generation is already running for this session");
        }

        emitter.onCompletion(() -> cleanupActiveConversation(tenantContext.tenantId(), sessionId));
        emitter.onTimeout(() -> cleanupActiveConversation(tenantContext.tenantId(), sessionId));
        emitter.onError(error -> cleanupActiveConversation(tenantContext.tenantId(), sessionId));

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
        boolean stopRequested = conversationRunLockManager.requestStop(tenantContext.tenantId(), sessionId);
        if (!stopRequested) {
            return new StopResult(false, sessionId);
        }
        return new StopResult(true, sessionId);
    }

    public ResumeResult resumeConversation(long sessionId) {
        requireAccessibleSession(sessionId);
        TenantContext tenantContext = requireTenantContext();
        var latestExchange = conversationRepository.findLatestExchangeBySessionId(tenantContext.tenantId(), sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "No exchange found for session"));
        if (latestExchange.executionMode() != ExecutionMode.REACT_AGENT) {
            throw new AppException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Latest exchange is not an agent run");
        }
        long runId = conversationRepository.findLatestAgentRunIdByExchangeId(tenantContext.tenantId(), latestExchange.id())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "No agent run found for session"));
        boolean accepted = agentGatewayClient.resumeRun(runId);
        return new ResumeResult(accepted, sessionId, runId);
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
            List<String> recentMessages = conversationMemoryService.buildContext(sessionId, tenantId, resolvedMemoryStrategy);
            ConversationExecutionPlanner.ExecutionPlan executionPlan = conversationExecutionPlanner.plan(
                    request.message().trim(),
                    resolvedKnowledgeBaseId,
                    recentMessages
            );

            var exchange = conversationRepository.createExchange(
                    tenantId,
                    sessionId,
                    userMessage.id(),
                    executionPlan.executionMode(),
                    "running",
                    executionPlan.routeReason(),
                    executionPlan.routeConfidence()
            );
            exchangeId = exchange.id();

            sendEvent(emitter, "start", new StartEvent(exchangeId, userMessage.id()));
            emitTraceStage(
                    exchangeId,
                    tenantId,
                    emitter,
                    "execution_planning",
                    "mode=" + executionPlan.executionMode() + ", steps=" + String.join(">", executionPlan.steps()),
                    executionPlan.summary()
            );
            emitTraceStage(exchangeId, tenantId, emitter, "memory_assembly", "recent_messages=" + recentMessages.size(), "已组装最近会话记忆");

            if (executionPlan.executionMode() == ExecutionMode.CLARIFICATION) {
                RagResponse clarificationResponse = RagResponse.clarification(
                        request.message().trim(),
                        "请补充更明确的对象、配置项或文档范围，我再继续回答。",
                        executionPlan.summary()
                );
                streamAnswer(exchangeId, tenantId, sessionId, emitter, activeConversation, clarificationResponse, false);
                return;
            }

            if (executionPlan.executionMode() == ExecutionMode.REACT_AGENT) {
                streamAgentRun(
                        session,
                        principal,
                        tenantContext,
                        exchangeId,
                        userMessage,
                        request.message().trim(),
                        resolvedKnowledgeBaseId,
                        recentMessages,
                        resolvedMemoryStrategy,
                        emitter,
                        activeConversation
                );
                return;
            }

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
            streamAnswer(exchangeId, tenantId, sessionId, emitter, activeConversation, ragResponse, true);
        } catch (AppException exception) {
            handleStreamError(tenantId, sessionId, exchangeId, emitter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            handleStreamError(tenantId, sessionId, exchangeId, emitter, ErrorCode.MODEL_PROVIDER_ERROR, "RAG generation failed");
        } finally {
            conversationMemoryService.refreshSummaryIfNeeded(tenantId, sessionId);
            cleanupActiveConversation(tenantId, sessionId);
        }
    }

    private void streamAgentRun(
            ConversationSession session,
            AuthenticatedUserPrincipal principal,
            TenantContext tenantContext,
            long exchangeId,
            ConversationMessage userMessage,
            String question,
            Long knowledgeBaseId,
            List<String> recentMessages,
            MemoryStrategy memoryStrategy,
            SseEmitter emitter,
            ConversationStreamRegistry.ActiveConversation activeConversation
    ) {
        long tenantId = tenantContext.tenantId();
        long runId = agentGatewayClient.createRun(new AgentGatewayClient.CreateRunRequest(
                tenantId,
                session.id(),
                exchangeId,
                userMessage.id(),
                principal.userId(),
                principal.currentRole().name(),
                question,
                knowledgeBaseId,
                memoryStrategy,
                recentMessages
        ));
        emitTraceStage(exchangeId, tenantId, emitter, "agent_dispatch", "run_id=" + runId, "agent_service_dispatched");

        StringBuilder fullText = new StringBuilder();
        try {
            agentGatewayClient.streamRun(runId, () -> isStopRequested(activeConversation), (eventName, dataJson) -> {
                if ("delta".equals(eventName)) {
                    Map<String, Object> payload = objectMapper.readValue(dataJson, MAP_TYPE);
                    fullText.append(String.valueOf(payload.getOrDefault("text", "")));
                } else if ("error".equals(eventName)) {
                    Map<String, Object> payload = objectMapper.readValue(dataJson, MAP_TYPE);
                    throw new AppException(
                            ErrorCode.AGENT_SERVICE_ERROR,
                            HttpStatus.BAD_GATEWAY,
                            String.valueOf(payload.getOrDefault("message", "Agent execution failed"))
                    );
                }

                Object payload = objectMapper.readValue(dataJson, Object.class);
                sendEvent(emitter, eventName, payload);
            });

            String assistantStatus = isStopRequested(activeConversation) ? "stopped" : "success";
            ConversationMessage assistantMessage = conversationRepository.createMessage(
                    tenantId,
                    session.id(),
                    MessageRole.assistant,
                    fullText.toString(),
                    assistantStatus,
                    null
            );
            conversationRepository.touchSession(session.id(), tenantId, assistantMessage.createdAt());
            conversationRepository.completeExchange(exchangeId, tenantId, assistantMessage.id(), assistantStatus);
            sendEvent(emitter, "done", new DoneEvent(exchangeId, assistantMessage.id(), isStopRequested(activeConversation)));
            emitter.complete();
        } catch (AppException exception) {
            handleStreamError(tenantId, session.id(), exchangeId, emitter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            handleStreamError(tenantId, session.id(), exchangeId, emitter, ErrorCode.AGENT_SERVICE_ERROR, "Agent execution failed");
        }
    }

    private void streamAnswer(
            long exchangeId,
            long tenantId,
            long sessionId,
            SseEmitter emitter,
            ConversationStreamRegistry.ActiveConversation activeConversation,
            RagResponse ragResponse,
            boolean emitReferences
    ) {
        StringBuilder fullText = new StringBuilder();
        if (emitReferences) {
            emitReferences(exchangeId, tenantId, emitter, ragResponse.evidences());
        }
        long modelStageId = createTraceStage(exchangeId, tenantId, "answer_generation", "prompt=" + ragResponse.diagnostics().promptSummary());
        long generationStartedAt = System.nanoTime();
        for (String delta : ragResponse.answer().deltas()) {
            if (isStopRequested(activeConversation)) {
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
                ragResponse.answer().provider(),
                ragResponse.answer().model(),
                "chat",
                ragResponse.diagnostics().promptSummary(),
                excerpt(fullText.toString()),
                ragResponse.answer().inputTokens(),
                ragResponse.answer().outputTokens(),
                (int) ((System.nanoTime() - generationStartedAt) / 1_000_000L),
                isStopRequested(activeConversation) ? "stopped" : "success",
                null,
                Map.of(
                        "rewrittenQuestion", ragResponse.rewrittenQuestion(),
                        "recommendationCount", ragResponse.answer().recommendations().size(),
                        "finishReason", ragResponse.answer().finishReason()
                )
        );
        sendEvent(emitter, "trace_stage", new TraceStageEvent("answer_generation", "success", 80L));

        String assistantStatus = isStopRequested(activeConversation) ? "stopped" : "success";
        ConversationMessage assistantMessage = conversationRepository.createMessage(
                tenantId,
                sessionId,
                MessageRole.assistant,
                fullText.toString(),
                assistantStatus,
                null
        );
        conversationRepository.touchSession(sessionId, tenantId, assistantMessage.createdAt());

        if (!isStopRequested(activeConversation)) {
            sendEvent(emitter, "recommendation", new RecommendationEvent(ragResponse.answer().recommendations()));
        }

        conversationRepository.completeExchange(
                exchangeId,
                tenantId,
                assistantMessage.id(),
                isStopRequested(activeConversation) ? "stopped" : "success"
        );

        sendEvent(emitter, "done", new DoneEvent(exchangeId, assistantMessage.id(), isStopRequested(activeConversation)));
        emitter.complete();
    }

    private void emitReferences(long exchangeId, long tenantId, SseEmitter emitter, List<RagEvidence> evidences) {
        for (int index = 0; index < evidences.size(); index++) {
            RagEvidence candidate = evidences.get(index);
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
    }

    private boolean isStopRequested(ConversationStreamRegistry.ActiveConversation activeConversation) {
        return conversationRunLockManager.isStopRequested(activeConversation.tenantId(), activeConversation.sessionId());
    }

    private void cleanupActiveConversation(long tenantId, long sessionId) {
        ConversationStreamRegistry.ActiveConversation removed = streamRegistry.remove(tenantId, sessionId);
        if (removed != null) {
            conversationRunLockManager.release(tenantId, sessionId, removed.runLock().ownerToken());
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

    private String normalizeTraceStageStatus(String status) {
        if (status == null || status.isBlank()) {
            return "failed";
        }
        return switch (status) {
            case "pending", "running", "success", "failed", "skipped" -> status;
            default -> "failed";
        };
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
                    buildRetrievalFilters(step),
                    step.vectorResults().size(),
                    countSelectedChunks(step.vectorResults(), step.selectedResults()),
                    step.vectorLatencyMs()
            );
            persistRetrievalResultItems(tenantId, vectorTraceId, step.vectorResults(), step.selectedResults());

            long keywordTraceId = conversationRepository.createRetrievalTrace(
                    tenantId,
                    exchangeId,
                    keywordStageId,
                    step.query().subQuestionNo(),
                    "keyword",
                    step.query().subQuestion(),
                    buildRetrievalFilters(step),
                    step.keywordResults().size(),
                    countSelectedChunks(step.keywordResults(), step.selectedResults()),
                    step.keywordLatencyMs()
            );
            persistRetrievalResultItems(tenantId, keywordTraceId, step.keywordResults(), step.selectedResults());

            long fusedTraceId = conversationRepository.createRetrievalTrace(
                    tenantId,
                    exchangeId,
                    fusionStageId,
                    step.query().subQuestionNo(),
                    "rrf",
                    step.query().subQuestion(),
                    buildRetrievalFilters(step),
                    step.fusedResults().size(),
                    step.selectedResults().size(),
                    step.fusedLatencyMs()
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
                rerankStep.latencyMs(),
                rerankStep.status(),
                rerankStep.errorMessage(),
                buildRerankMetadata(diagnostics)
        );
        String traceStageStatus = normalizeTraceStageStatus(rerankStep.status());
        completeTraceStage(
                stage.id(),
                tenantId,
                traceStageStatus,
                rerankStep.enabled() ? "reranked=" + rerankStep.outputCount() : rerankStep.skippedReason(),
                rerankStep.errorMessage()
        );
        sendEvent(emitter, "trace_stage", new TraceStageEvent("rerank", traceStageStatus, 80L));
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
                    buildTraceMetadata(result.documentTitle(), result.sectionTitle(), result.channel(), result.metadata())
            );
        }
    }

    private void persistFusedItems(long tenantId, long retrievalTraceId, List<RagEvidence> fusedResults, List<RagEvidence> selectedResults) {
        for (int index = 0; index < fusedResults.size(); index++) {
            RagEvidence result = fusedResults.get(index);
            RagEvidence selected = findSelectedEvidence(selectedResults, result.chunkId());
            conversationRepository.createRetrievalTraceItem(
                    tenantId,
                    retrievalTraceId,
                    result.documentId(),
                    result.chunkId(),
                    index + 1,
                    BigDecimal.valueOf(result.score()),
                    BigDecimal.valueOf(selected == null ? result.score() : selected.score()),
                    containsChunk(selectedResults, result.chunkId()),
                    buildTraceMetadata(
                            result.documentTitle(),
                            result.sectionTitle(),
                            result.channel(),
                            selected == null ? result.metadata() : mergeTraceMetadata(result.metadata(), selected.metadata())
                    )
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

    private RagEvidence findSelectedEvidence(List<RagEvidence> selectedResults, long chunkId) {
        return selectedResults.stream()
                .filter(evidence -> evidence.chunkId() == chunkId)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> mergeTraceMetadata(Map<String, Object> baseMetadata, Map<String, Object> selectedMetadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseMetadata != null && !baseMetadata.isEmpty()) {
            merged.putAll(baseMetadata);
        }
        if (selectedMetadata != null && !selectedMetadata.isEmpty()) {
            merged.putAll(selectedMetadata);
        }
        return merged;
    }

    private Map<String, Object> buildTraceMetadata(
            String documentTitle,
            String sectionTitle,
            String channel,
            Map<String, Object> additionalMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (additionalMetadata != null && !additionalMetadata.isEmpty()) {
            metadata.putAll(additionalMetadata);
        }
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
        return buildRetrievalFilters(step.query(), step);
    }

    private Map<String, Object> buildRetrievalFilters(com.superagent.rag.domain.RagSearchQuery query) {
        return buildRetrievalFilters(query, null);
    }

    private Map<String, Object> buildRetrievalFilters(
            com.superagent.rag.domain.RagSearchQuery query,
            RagResponseDiagnostics.RetrievalStep step
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("originalQuestion", query.originalQuestion());
        filters.put("rewrittenQuestion", query.rewrittenQuestion());
        filters.put("subQuestion", query.subQuestion());
        filters.put("subQuestionNo", query.subQuestionNo());
        filters.put("knowledgeBaseId", query.knowledgeBaseId());
        filters.put("knowledgeDomainId", query.knowledgeDomainId());
        filters.put("chunkingProfileId", query.chunkingProfileId());
        filters.put("category", query.category());
        filters.put("tags", query.tags());
        filters.put("answerMode", query.answerMode());
        filters.put("queryUnderstandingSource", query.queryUnderstandingSource());
        filters.put("queryUnderstandingConfidence", query.queryUnderstandingConfidence());
        filters.put("versionConsistencyEnabled", query.versionConsistencyEnabled());
        filters.put("neighborExpansionEnabled", query.neighborExpansionEnabled());
        filters.put("vectorTopK", query.vectorTopK());
        filters.put("keywordTopK", query.keywordTopK());
        filters.put("candidateTopK", query.candidateTopK());
        filters.put("rrfK", query.rrfK());
        filters.put("neighborWindow", query.neighborWindow());
        filters.put("maxChunksPerDocument", query.maxChunksPerDocument());
        filters.put("evidenceLimit", query.evidenceLimit());
        filters.put("perQuestionEvidenceCharLimit", query.perQuestionEvidenceCharLimit());
        filters.put("totalEvidenceCharLimit", query.totalEvidenceCharLimit());
        filters.put("maxEvidenceContentChars", query.maxEvidenceContentChars());
        filters.put("minRelevanceScore", query.minRelevanceScore());
        filters.put("baseAnswerConfidenceThreshold", query.baseAnswerConfidenceThreshold());
        filters.put("answerConfidenceThreshold", query.answerConfidenceThreshold());
        filters.put("rerankEnabled", query.rerankEnabled());
        filters.put("baseNoEvidenceMinResults", query.baseNoEvidenceMinResults());
        filters.put("noEvidenceMinResults", query.noEvidenceMinResults());
        filters.put("baseForceCitationEnabled", query.baseForceCitationEnabled());
        filters.put("forceCitationEnabled", query.forceCitationEnabled());
        filters.put("highRiskGuardApplied", query.highRiskGuardApplied());
        filters.put("questionRiskLevel", query.questionRiskLevel());
        filters.put("questionRiskReasons", query.questionRiskReasons());
        filters.put("queryRewriteApplied", !query.originalQuestion().equals(query.rewrittenQuestion()));
        filters.put("hybridRetrievalEnabled", true);
        if (step != null) {
            filters.put("vectorResultCount", step.vectorResults().size());
            filters.put("keywordResultCount", step.keywordResults().size());
            filters.put("fusedResultCount", step.fusedResults().size());
            filters.put("selectedResultCount", step.selectedResults().size());
            filters.put("neighborExpanded", step.fusedResults().stream()
                    .anyMatch(item -> Boolean.TRUE.equals(item.metadata().get("neighborExpanded"))));
            filters.put("finalTopK", step.selectedResults().size());
            filters.put("diversityLimited", step.diversityLimited());
            filters.put("belowThresholdFilteredCount", step.belowThresholdFilteredCount());
            filters.put("perDocumentTrimmedCount", step.perDocumentTrimmedCount());
            filters.put("contentTrimmedCount", step.contentTrimmedCount());
            filters.put("charBudgetTrimmedCount", step.charBudgetTrimmedCount());
            filters.put("evidenceLimitTrimmedCount", step.evidenceLimitTrimmedCount());
        }
        return filters;
    }

    private Map<String, Object> buildRerankMetadata(RagResponseDiagnostics diagnostics) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memorySummary", diagnostics.memorySummary());
        metadata.put("promptSummary", diagnostics.promptSummary());
        metadata.put("modelSummary", diagnostics.modelSummary());
        metadata.put("fallbackReason", diagnostics.fallbackReason());
        metadata.put("citationAppended", diagnostics.citationAppended());
        metadata.put("answerConfidenceScore", diagnostics.answerConfidenceScore());
        metadata.put("answerConfidenceThreshold", diagnostics.answerConfidenceThreshold());
        metadata.put("retrievalStepCount", diagnostics.retrievalSteps().size());
        metadata.put("subQuestionCount", diagnostics.retrievalSteps().size());
        metadata.put("selectedEvidenceCount", diagnostics.retrievalSteps().stream()
                .mapToInt(step -> step.selectedResults().size())
                .sum());
        if (!diagnostics.retrievalSteps().isEmpty()) {
            com.superagent.rag.domain.RagSearchQuery firstQuery = diagnostics.retrievalSteps().getFirst().query();
            metadata.put("originalQuestion", firstQuery.originalQuestion());
            metadata.put("rewrittenQuestion", firstQuery.rewrittenQuestion());
            metadata.put("knowledgeBaseId", firstQuery.knowledgeBaseId());
            metadata.put("knowledgeDomainId", firstQuery.knowledgeDomainId());
            metadata.put("chunkingProfileId", firstQuery.chunkingProfileId());
            metadata.put("category", firstQuery.category());
            metadata.put("tags", firstQuery.tags());
            metadata.put("baseAnswerConfidenceThreshold", firstQuery.baseAnswerConfidenceThreshold());
            metadata.put("baseNoEvidenceMinResults", firstQuery.baseNoEvidenceMinResults());
            metadata.put("baseForceCitationEnabled", firstQuery.baseForceCitationEnabled());
            metadata.put("maxEvidenceContentChars", firstQuery.maxEvidenceContentChars());
            metadata.put("highRiskGuardApplied", firstQuery.highRiskGuardApplied());
            metadata.put("questionRiskLevel", firstQuery.questionRiskLevel());
            metadata.put("questionRiskReasons", firstQuery.questionRiskReasons());
        }
        return metadata;
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
                        null,
                        null,
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
            Double minRelevanceScore,
            Long knowledgeDomainId,
            Long chunkingProfileId,
            String category,
            java.util.List<String> tags
    ) {
    }

    public record StopResult(boolean stopped, long sessionId) {
    }

    public record ResumeResult(boolean resumed, long sessionId, long runId) {
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
