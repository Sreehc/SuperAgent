package com.superagent.chat.web;

import com.superagent.chat.domain.ConversationMessage;
import com.superagent.chat.domain.ConversationSession;
import com.superagent.chat.domain.ConversationStatus;
import com.superagent.chat.domain.MemoryStrategy;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("${super-agent.app.api-base-path}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationSummary> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        ConversationSession session = conversationService.createConversation(
                request.title(),
                request.knowledgeBaseId(),
                request.memoryStrategy()
        );
        return ApiResponse.success(toSummary(session));
    }

    @GetMapping
    public ApiResponse<PagedResponse<ConversationListItem>> listConversations(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        var result = conversationService.listConversations(page, pageSize, status, keyword);
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toListItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<ConversationDetail> getConversation(@PathVariable long sessionId) {
        return ApiResponse.success(toDetail(conversationService.getConversation(sessionId)));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<ConversationPatchResponse> updateConversation(
            @PathVariable long sessionId,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        ConversationSession session = conversationService.updateConversation(
                sessionId,
                request.title(),
                request.knowledgeBaseId(),
                request.memoryStrategy(),
                request.status()
        );
        return ApiResponse.success(new ConversationPatchResponse(session.id(), session.title(), session.status().name()));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<DeleteConversationResponse> deleteConversation(@PathVariable long sessionId) {
        return ApiResponse.success(new DeleteConversationResponse(conversationService.deleteConversation(sessionId)));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<PagedResponse<MessageItem>> listMessages(
            @PathVariable long sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        var result = conversationService.listMessages(sessionId, page, pageSize);
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toMessageItem).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    @PostMapping(
            value = "/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamMessage(
            @PathVariable long sessionId,
            @Valid @RequestBody StreamMessageRequest request
    ) {
        return conversationService.streamMessage(
                sessionId,
                new ConversationService.StreamRequest(
                        request.message(),
                        request.knowledgeBaseId(),
                        request.memoryStrategy(),
                        request.ragOptions()
                )
        );
    }

    @PostMapping("/{sessionId}/stop")
    public ApiResponse<ConversationService.StopResult> stopConversation(@PathVariable long sessionId) {
        return ApiResponse.success(conversationService.stopConversation(sessionId));
    }

    private ConversationSummary toSummary(ConversationSession session) {
        return new ConversationSummary(
                session.id(),
                session.title(),
                session.knowledgeBaseId(),
                session.memoryStrategy().name(),
                session.status().name(),
                session.createdAt()
        );
    }

    private ConversationListItem toListItem(ConversationSession session) {
        return new ConversationListItem(
                session.id(),
                session.title(),
                session.status().name(),
                session.lastMessageAt()
        );
    }

    private ConversationDetail toDetail(ConversationSession session) {
        return new ConversationDetail(
                session.id(),
                session.title(),
                session.knowledgeBaseId(),
                session.memoryStrategy().name(),
                session.status().name(),
                session.createdAt(),
                session.updatedAt()
        );
    }

    private MessageItem toMessageItem(ConversationMessage message) {
        return new MessageItem(
                message.id(),
                message.role().name(),
                message.content(),
                message.status(),
                message.createdAt()
        );
    }

    public record CreateConversationRequest(
            String title,
            Long knowledgeBaseId,
            MemoryStrategy memoryStrategy
    ) {
    }

    public record UpdateConversationRequest(
            String title,
            Long knowledgeBaseId,
            MemoryStrategy memoryStrategy,
            ConversationStatus status
    ) {
    }

    public record StreamMessageRequest(
            @NotBlank(message = "message is required") String message,
            Long knowledgeBaseId,
            MemoryStrategy memoryStrategy,
            ConversationService.RagOptions ragOptions
    ) {
    }

    public record ConversationSummary(
            long id,
            String title,
            Long knowledgeBaseId,
            String memoryStrategy,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record ConversationListItem(
            long id,
            String title,
            String status,
            OffsetDateTime lastMessageAt
    ) {
    }

    public record ConversationDetail(
            long id,
            String title,
            Long knowledgeBaseId,
            String memoryStrategy,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ConversationPatchResponse(long id, String title, String status) {
    }

    public record DeleteConversationResponse(boolean deleted) {
    }

    public record MessageItem(
            long id,
            String role,
            String content,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record PagedResponse<T>(
            List<T> items,
            int page,
            int pageSize,
            long total
    ) {
    }
}
