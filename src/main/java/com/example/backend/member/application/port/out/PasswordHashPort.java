package com.example.backend.member.application.port.out;

public interface PasswordHashPort {
    String encode(String password);
    boolean matches(String password, String hash);
}
