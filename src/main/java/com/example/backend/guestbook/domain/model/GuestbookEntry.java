package com.example.backend.guestbook.domain.model;

import java.time.Instant;

public record GuestbookEntry(Long memberId, String message, long likeCount, Instant createdAt) {
    public GuestbookEntry withMessage(String value) {
        return new GuestbookEntry(memberId, value, likeCount, createdAt);
    }
}
