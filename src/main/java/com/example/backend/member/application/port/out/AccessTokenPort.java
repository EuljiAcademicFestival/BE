package com.example.backend.member.application.port.out;

import java.util.Optional;

public interface AccessTokenPort {
    String issue(long memberId);
    Optional<Long> authenticate(String token);
}
