package com.superagent.common.exception;

import com.superagent.common.api.ApiError;
import com.superagent.common.api.ApiResponse;
import com.superagent.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(ApiResponse.failure(new ApiError(
                        exception.getErrorCode().name(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        null
                )));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return buildValidationResponse(
                exception.getBindingResult().getFieldErrors(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException exception, HttpServletRequest request) {
        return buildValidationResponse(exception.getBindingResult().getFieldErrors(), request.getRequestURI());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
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

        return ResponseEntity.badRequest().body(ApiResponse.failure(new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed",
                request.getRequestURI(),
                details
        )));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
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

        return ResponseEntity.badRequest().body(ApiResponse.failure(new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed",
                request.getRequestURI(),
                details
        )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(new ApiError(
                        ErrorCode.INTERNAL_ERROR.name(),
                        "Unexpected server error",
                        request.getRequestURI(),
                        null
                )));
    }

    private ResponseEntity<ApiResponse<Void>> buildValidationResponse(
            Iterable<FieldError> fieldErrors,
            String path
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", toFieldErrorList(fieldErrors));

        return ResponseEntity.badRequest().body(ApiResponse.failure(new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed",
                path,
                details
        )));
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
}
