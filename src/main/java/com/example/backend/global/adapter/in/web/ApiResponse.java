package com.example.backend.global.adapter.in.web;

import com.example.backend.global.domain.exception.ErrorCode;

public record ApiResponse<T>(int status, String code, String message, T data) {
    public static <T> ApiResponse<T> ok(String code, String message, T data) {
        return new ApiResponse<>(200, code, message, data);
    }
    public static ApiResponse<Void> error(ErrorCode error) {
        return new ApiResponse<>(error.status(), error.name(), error.message(), null);
    }
}
