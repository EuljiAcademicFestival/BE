package com.example.backend.member.application.result;

public record ProfileResult(long memberId, int characterId, String name, String grade,
                            String guestbookMessage, long likeCount) {}
