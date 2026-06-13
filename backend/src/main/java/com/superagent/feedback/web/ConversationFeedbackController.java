package com.superagent.feedback.web;

import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ApiResponse;
import com.superagent.feedback.domain.ConversationFeedback;
import com.superagent.feedback.service.ConversationFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${super-agent.app.api-base-path}")
public class ConversationFeedbackController {

    private final ConversationFeedbackService feedbackService;

    public ConversationFeedbackController(ConversationFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PutMapping("/messages/{messageId}/feedback")
    public ApiResponse<FeedbackResponse> upsert(
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackRequest request
    ) {
        return ApiResponse.success(toResponse(feedbackService.upsert(messageId, request.rating(), request.comment(), request.correction())));
    }

    @DeleteMapping("/messages/{messageId}/feedback")
    public ApiResponse<DeleteFeedbackResponse> delete(@PathVariable long messageId) {
        return ApiResponse.success(new DeleteFeedbackResponse(feedbackService.delete(messageId), messageId));
    }

    @GetMapping("/conversations/{sessionId}/feedbacks")
    public ApiResponse<List<FeedbackResponse>> listMineForSession(@PathVariable long sessionId) {
        return ApiResponse.success(feedbackService.listMineForSession(sessionId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/admin/feedbacks")
    public ApiResponse<PagedResponse<FeedbackResponse>> listAdmin(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String rating
    ) {
        ConversationService.PagedResult<ConversationFeedback> result = feedbackService.listAdmin(page, pageSize, rating);
        return ApiResponse.success(new PagedResponse<>(
                result.items().stream().map(this::toResponse).toList(),
                result.page(),
                result.pageSize(),
                result.total()
        ));
    }

    private FeedbackResponse toResponse(ConversationFeedback feedback) {
        return new FeedbackResponse(
                feedback.id(),
                feedback.sessionId(),
                feedback.exchangeId(),
                feedback.messageId(),
                feedback.actorUserId(),
                feedback.rating(),
                feedback.comment(),
                feedback.correction(),
                feedback.metadata(),
                feedback.createdAt(),
                feedback.updatedAt()
        );
    }

    public record FeedbackRequest(
            @Pattern(regexp = "up|down|correction", message = "rating must be up, down or correction")
            String rating,
            @Size(max = 2000, message = "comment is too long")
            String comment,
            @Size(max = 4000, message = "correction is too long")
            String correction
    ) {
    }

    public record FeedbackResponse(
            long id,
            long sessionId,
            Long exchangeId,
            long messageId,
            long actorUserId,
            String rating,
            String comment,
            String correction,
            Map<String, Object> metadata,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DeleteFeedbackResponse(boolean deleted, long messageId) {
    }

    public record PagedResponse<T>(List<T> items, int page, int pageSize, long total) {
    }
}
