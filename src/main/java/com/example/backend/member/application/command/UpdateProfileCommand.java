package com.example.backend.member.application.command;

public record UpdateProfileCommand(long memberId, Integer characterId, String name,
                                   String grade, String guestbookMessage) {}
