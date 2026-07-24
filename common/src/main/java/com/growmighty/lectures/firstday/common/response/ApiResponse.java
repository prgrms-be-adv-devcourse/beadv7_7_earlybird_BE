package com.growmighty.lectures.firstday.common.response;

import java.util.List;

public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }

    public record ApiError(String message, List<FieldErrorDetail> errors) {

        public record FieldErrorDetail(String field, String message) {
        }
    }
}
