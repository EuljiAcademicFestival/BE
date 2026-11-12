package com.example.backend.guestbook.domain.policy;

import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.global.domain.exception.ErrorCode;
import java.util.regex.Pattern;

public final class GuestbookPolicy {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private GuestbookPolicy() {}

    public static String validateMessage(String value, ErrorCode error) {
        if (value == null) throw new BusinessException(error);
        String message = value.strip();
        if (message.isBlank() || GRAPHEME.matcher(message).results().limit(101).count() > 100) {
            throw new BusinessException(error);
        }
        return message;
    }
}
