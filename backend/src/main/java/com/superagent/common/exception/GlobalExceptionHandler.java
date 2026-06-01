package com.superagent.common.exception;

import com.superagent.common.api.ApiResponse;
import com.superagent.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(failure(exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(failure(ErrorCode.UNAUTHORIZED, exception.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(failure(ErrorCode.FORBIDDEN, exception.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return buildValidationResponse(
                exception.getBindingResult().getFieldErrors(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        return buildValidationResponse(exception.getBindingResult().getFieldErrors(), request.getRequestURI());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", exception.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "parameter", result.getMethodParameter().getParameterName(),
                                "message", error.getDefaultMessage()
                        )))
                .collect(Collectors.toList()));

        return ResponseEntity.unprocessableEntity()
                .body(failure(ErrorCode.VALIDATION_FAILED, "Request validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "parameter", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()
                ))
                .collect(Collectors.toList()));

        return ResponseEntity.unprocessableEntity()
                .body(failure(ErrorCode.VALIDATION_FAILED, "Request validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(failure(ErrorCode.INTERNAL_ERROR, "Unexpected server error", request.getRequestURI(), null));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildValidationResponse(
            Iterable<FieldError> fieldErrors,
            String path
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", toFieldErrorList(fieldErrors));

        return ResponseEntity.unprocessableEntity()
                .body(failure(ErrorCode.VALIDATION_FAILED, "Request validation failed", path, details));
    }

    private List<Map<String, String>> toFieldErrorList(Iterable<FieldError> fieldErrors) {
        List<FieldError> errors = (fieldErrors instanceof List<FieldError> list)
                ? list
                : java.util.stream.StreamSupport.stream(fieldErrors.spliterator(), false).toList();

        return errors.stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage()
                ))
                .collect(Collectors.toList());
    }

    private ApiResponse<Map<String, Object>> failure(
            ErrorCode code,
            String message,
            String path,
            Map<String, Object> details
    ) {
        if (details == null || details.isEmpty()) {
            return ApiResponse.failure(code, message);
        }
        return ApiResponse.failure(code, message).withData(new LinkedHashMap<>(details));
    }
}
