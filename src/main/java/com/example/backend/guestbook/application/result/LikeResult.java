package com.example.backend.guestbook.application.result;

public record LikeResult(long memberId, boolean likedByMe, long likeCount) {}
