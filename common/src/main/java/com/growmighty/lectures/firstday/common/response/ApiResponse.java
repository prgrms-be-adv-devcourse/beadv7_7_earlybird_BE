package com.growmighty.lectures.firstday.common.response;

public record ApiResponse<T>(boolean success, T data, Object error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }
}
