package com.aloute.common.api;

public record ApiResponse<T>(boolean success, T data, Object meta) {
    public T getData() {
        return data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>(true, data, meta);
    }

    public static <T> ApiResponse<T> page(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta);
    }
}
