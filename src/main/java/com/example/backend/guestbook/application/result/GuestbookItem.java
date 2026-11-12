package com.example.backend.guestbook.application.result;

import java.time.Instant;

public record GuestbookItem(long memberId, int characterId, String grade, String name, Integer rank,
                            String guestbookMessage, Instant createdAt, String elapsedTimeText,
                            long likeCount, boolean likedByMe) {}
