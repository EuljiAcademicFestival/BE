package com.example.backend.member.application.command;

public record SignupCommand(String email, String password, Integer characterId, String name,
                            String grade, String guestbookMessage) {}
