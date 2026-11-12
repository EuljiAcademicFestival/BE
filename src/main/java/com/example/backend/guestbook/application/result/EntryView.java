package com.example.backend.guestbook.application.result;

import java.time.Instant;

public record EntryView(long memberId, int characterId, String grade, String name, Integer rank,
                        String guestbookMessage, Instant createdAt, long likeCount, boolean likedByMe) {}
