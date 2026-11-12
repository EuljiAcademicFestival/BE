package com.example.backend.global.domain.exception;

public enum ErrorCode {
    AUTH_INVALID_INPUT(400, "입력값을 확인해 주세요."),
    AUTH_DUPLICATE_MEMBER(409, "이미 등록된 회원 정보입니다."),
    AUTH_INVALID_CREDENTIALS(401, "이메일 또는 비밀번호를 확인해 주세요."),
    AUTH_401_001(401, "인증이 필요합니다."),
    MEMBER_PROFILE_INVALID_INPUT(400, "입력값을 확인해 주세요."),
    CHARACTER_INVALID_INPUT(400, "회원 ID를 확인해 주세요."),
    CHARACTER_NOT_FOUND(404, "회원을 찾을 수 없습니다."),
    GUESTBOOK_INVALID_QUERY(400, "조회 조건을 확인해 주세요."),
    GUESTBOOK_INVALID_MEMBER_ID(400, "회원 ID를 확인해 주세요."),
    GUESTBOOK_NOT_FOUND(404, "방명록을 찾을 수 없습니다."),
    COMMON_500(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;
    ErrorCode(int status, String message) { this.status = status; this.message = message; }
    public int status() { return status; }
    public String message() { return message; }
}
