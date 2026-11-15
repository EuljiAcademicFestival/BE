package com.example.backend.guestbook.domain.model;

import com.example.backend.global.domain.exception.*;

public enum GuestbookSort {
    POPULAR, LATEST;
    public static GuestbookSort parse(String value) {
        return switch (value) {
            case "popular" -> POPULAR;
            case "latest" -> LATEST;
            default -> throw new BusinessException(ErrorCode.GUESTBOOK_INVALID_QUERY);
        };
    }
}
