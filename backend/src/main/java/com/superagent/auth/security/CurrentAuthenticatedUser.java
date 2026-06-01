package com.superagent.auth.security;

import com.superagent.common.api.ErrorCode;
import com.superagent.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAuthenticatedUser {

    public AuthenticatedUserPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
