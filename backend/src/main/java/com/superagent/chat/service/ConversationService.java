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
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ConversationService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final ConversationRepository conversationRepository;
    private final ChatModelClient chatModelClient;
    private final ConversationStreamRegistry streamRegistry;
    private final Executor conversationExecutor = new SimpleAsyncTaskExecutor("conversation-stream-");

    public ConversationService(
            CurrentAuthenticatedUser currentAuthenticatedUser,
            ConversationRepository conversationRepository,
            ChatModelClient chatModelClient,
            ConversationStreamRegistry streamRegistry
    ) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.conversationRepository = conversationRepository;
        this.chatModelClient = chatModelClient;
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

        conversationExecutor.execute(() -> generateConversation(session, principal, tenantContext, request, activeConversation));
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

            userMessage = conversationRepository.createMessage(
                    tenantId,
                    sessionId,
                    MessageRole.user,
                    request.message().trim(),
                    "success",
                    null
            );
            conversationRepository.touchSession(sessionId, tenantId, userMessage.createdAt());

            var exchange = conversationRepository.createExchange(
                    tenantId,
                    sessionId,
                    userMessage.id(),
                    ExecutionMode.RAG_QA,
                    "running",
                    resolvedKnowledgeBaseId == null ? "direct_chat_minimal_chain" : "minimal_chain_with_reference",
                    BigDecimal.valueOf(0.92d)
            );
            exchangeId = exchange.id();

            sendEvent(emitter, "start", new StartEvent(exchangeId, userMessage.id()));
            emitTraceStage(exchangeId, tenantId, emitter, "memory_assembly", "已组装最近会话消息窗口");
            emitTraceStage(exchangeId, tenantId, emitter, "minimal_generation", "已调用最小可用模型输出链路");

            List<String> recentMessages = conversationRepository.findMessages(sessionId, tenantId, 1, 6).stream()
                    .map(ConversationMessage::content)
                    .toList();
            ChatModelClient.ModelResponse modelResponse = chatModelClient.generateReply(new ChatModelClient.ModelRequest(
                    request.message().trim(),
                    session.title(),
                    resolvedMemoryStrategy.name(),
                    resolvedKnowledgeBaseId,
                    recentMessages
            ));

            StringBuilder fullText = new StringBuilder();
            for (String delta : modelResponse.deltas()) {
                if (activeConversation.stopRequested()) {
                    break;
                }
                fullText.append(delta);
                sendEvent(emitter, "delta", new DeltaEvent(delta));
                sleep(100L);
            }

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

            Optional<ConversationRepository.ReferenceCandidate> referenceCandidate =
                    conversationRepository.findReferenceCandidate(tenantId, resolvedKnowledgeBaseId);
            if (referenceCandidate.isPresent()) {
                var candidate = referenceCandidate.get();
                var reference = conversationRepository.createReference(
                        tenantId,
                        exchangeId,
                        candidate.documentId(),
                        candidate.chunkId(),
                        1,
                        candidate.title(),
                        candidate.quote(),
                        BigDecimal.valueOf(0.81d),
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
                sendEvent(emitter, "recommendation", new RecommendationEvent(modelResponse.recommendations()));
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
            handleStreamError(tenantId, sessionId, exchangeId, emitter, ErrorCode.MODEL_PROVIDER_ERROR, "Minimal model chain failed");
        } finally {
            streamRegistry.remove(tenantId, sessionId);
        }
    }

    private void emitTraceStage(long exchangeId, long tenantId, SseEmitter emitter, String stageCode, String outputSummary) {
        var stage = conversationRepository.createTraceStage(tenantId, exchangeId, stageCode, "running", null);
        conversationRepository.completeTraceStage(stage.id(), tenantId, "success", outputSummary, null);
        sendEvent(emitter, "trace_stage", new TraceStageEvent(stageCode, "success", 80L));
    }

    private void handleStreamError(long tenantId, long sessionId, long exchangeId, SseEmitter emitter, ErrorCode code, String message) {
        try {
            if (exchangeId > 0) {
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
