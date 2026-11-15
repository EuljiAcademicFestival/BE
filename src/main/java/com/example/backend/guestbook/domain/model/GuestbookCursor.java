package com.example.backend.guestbook.domain.model;

import com.example.backend.global.domain.exception.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record GuestbookCursor(GuestbookSort sort, long likeCount, Instant createdAt, long memberId) {
    public String encode() {
        String value = "1|" + sort.name() + "|" + likeCount + "|" + createdAt + "|" + memberId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    public static GuestbookCursor decode(String token, GuestbookSort expectedSort) {
        if (token == null) return null;
        try {
            if (token.length() > 512 || !token.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            String[] fields = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 5 || !fields[0].equals("1")) throw new IllegalArgumentException();
            GuestbookCursor result = new GuestbookCursor(GuestbookSort.valueOf(fields[1]),
                    Long.parseLong(fields[2]), Instant.parse(fields[3]), Long.parseLong(fields[4]));
            if (result.sort() != expectedSort || result.likeCount() < 0 || result.likeCount() > 4294967295L
                    || result.memberId() <= 0 || result.createdAt().getNano() % 1000 != 0
                    || result.createdAt().isBefore(Instant.parse("1000-01-01T00:00:00Z"))
                    || result.createdAt().isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException ex) { throw new BusinessException(ErrorCode.GUESTBOOK_INVALID_QUERY); }
    }
}
