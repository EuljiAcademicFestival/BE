package com.example.backend.member.adapter.out.security;

import com.example.backend.member.application.port.out.PasswordHashPort;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHashAdapter implements PasswordHashPort {
    private final Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    public String encode(String password) { return encoder.encode(password); }
    public boolean matches(String password, String hash) { return encoder.matches(password, hash); }
}
