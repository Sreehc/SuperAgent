package com.superagent.feedback.service;

import com.superagent.auth.domain.TenantRole;
import com.superagent.auth.security.AuthenticatedUserPrincipal;
import com.superagent.auth.security.CurrentAuthenticatedUser;
import com.superagent.auth.security.TenantContext;
import com.superagent.auth.security.TenantContextHolder;
import com.superagent.chat.service.ConversationService;
import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import com.superagent.feedback.domain.ConversationFeedback;
import com.superagent.feedback.repository.ConversationFeedbackRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationFeedbackService {

    private final CurrentAuthenticatedUser currentAuthenticatedUser;
    private final ConversationFeedbackRepository repository;

    public ConversationFeedbackService(CurrentAuthenticatedUser currentAuthenticatedUser, ConversationFeedbackRepository repository) {
        this.currentAuthenticatedUser = currentAuthenticatedUser;
        this.repository = repository;
    }

    @Transactional
    public ConversationFeedback upsert(long messageId, String rating, String comment, String correction) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        String normalizedRating = normalizeRating(rating);
        ConversationFeedbackRepository.MessageFeedbackTarget target = repository.findFeedbackTarget(tenantContext.tenantId(), messageId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Assistant message not found"));
        requireMessageAccess(principal, target);
        return repository.upsert(
                tenantContext.tenantId(),
                target,
                principal.userId(),
                normalizedRating,
                normalizeOptional(comment),
                normalizeOptional(correction)
        );
    }

    @Transactional
    public boolean delete(long messageId) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        ConversationFeedbackRepository.MessageFeedbackTarget target = repository.findFeedbackTarget(tenantContext.tenantId(), messageId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Assistant message not found"));
        requireMessageAccess(principal, target);
        return repository.delete(tenantContext.tenantId(), messageId, principal.userId());
    }

    public List<ConversationFeedback> listMineForSession(long sessionId) {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        TenantContext tenantContext = requireTenantContext();
        return repository.listForSession(tenantContext.tenantId(), sessionId, principal.userId());
    }

    public ConversationService.PagedResult<ConversationFeedback> listAdmin(Integer page, Integer pageSize, String rating) {
        requireAdminRole();
        TenantContext tenantContext = requireTenantContext();
        int resolvedPage = page == null || page < 1 ? 1 : page;
        int resolvedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        String normalizedRating = rating == null || rating.isBlank() ? null : normalizeRating(rating);
        long total = repository.countAdmin(tenantContext.tenantId(), normalizedRating);
        return new ConversationService.PagedResult<>(
                repository.listAdmin(tenantContext.tenantId(), normalizedRating, resolvedPage, resolvedPageSize),
                resolvedPage,
                resolvedPageSize,
                total
        );
    }

    private void requireMessageAccess(AuthenticatedUserPrincipal principal, ConversationFeedbackRepository.MessageFeedbackTarget target) {
        if (isAdmin(principal) || target.ownerId() == principal.userId()) {
            return;
        }
        throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Feedback permission required");
    }

    private void requireAdminRole() {
        AuthenticatedUserPrincipal principal = currentAuthenticatedUser.get();
        if (!isAdmin(principal)) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Feedback admin permission required");
        }
    }

    private boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return principal.currentRole() == TenantRole.OWNER || principal.currentRole() == TenantRole.ADMIN;
    }

    private TenantContext requireTenantContext() {
        TenantContext tenantContext = TenantContextHolder.get();
        if (tenantContext == null) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Tenant context required");
        }
        return tenantContext;
    }

    private String normalizeRating(String rating) {
        String normalized = rating == null ? "" : rating.trim();
        if (!List.of("up", "down", "correction").contains(normalized)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "rating must be up, down or correction");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
