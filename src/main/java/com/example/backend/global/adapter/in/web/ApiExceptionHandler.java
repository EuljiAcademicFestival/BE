package com.example.backend.global.adapter.in.web;

import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.global.domain.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        return ResponseEntity.status(ex.error().status()).body(ApiResponse.error(ex.error()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        ErrorCode error = path.startsWith("/api/auth/") ? ErrorCode.AUTH_INVALID_INPUT
                : path.equals("/api/members/me/profile") ? ErrorCode.MEMBER_PROFILE_INVALID_INPUT
                : path.equals("/api/guestbook") ? ErrorCode.GUESTBOOK_INVALID_QUERY
                : path.startsWith("/api/guestbook/") ? ErrorCode.GUESTBOOK_INVALID_MEMBER_ID
                : ErrorCode.CHARACTER_INVALID_INPUT;
        if (path.equals("/api/characters")) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, error.name(),
                    "회원 ID 목록을 확인해 주세요.", null));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Unhandled API failure", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error(ErrorCode.COMMON_500));
    }
}
