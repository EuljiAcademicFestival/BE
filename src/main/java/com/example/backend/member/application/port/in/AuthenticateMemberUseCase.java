package com.example.backend.member.application.port.in;

import java.util.Optional;

public interface AuthenticateMemberUseCase {
    Optional<Long> authenticate(String accessToken);
}
