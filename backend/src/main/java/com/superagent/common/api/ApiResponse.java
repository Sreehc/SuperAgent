package com.superagent.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.superagent.common.web.TraceIdHolder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.name(), "success", data, TraceIdHolder.getOrCreate());
    }

    public static <T> ApiResponse<T> failure(ErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message, null, TraceIdHolder.getOrCreate());
    }

    public <R> ApiResponse<R> withData(R newData) {
        return new ApiResponse<>(success, code, message, newData, traceId);
    }
}
