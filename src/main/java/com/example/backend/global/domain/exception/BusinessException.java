package com.example.backend.global.domain.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode error;
    public BusinessException(ErrorCode error) { super(error.message()); this.error = error; }
    public ErrorCode error() { return error; }
}
