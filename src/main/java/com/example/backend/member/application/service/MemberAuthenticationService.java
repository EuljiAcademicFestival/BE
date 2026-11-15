package com.example.backend.member.application.service;

import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import com.example.backend.member.application.port.out.AccessTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberAuthenticationService implements AuthenticateMemberUseCase {
    private final AccessTokenPort tokens;

    @Override
    public Optional<Long> authenticate(String accessToken) {
        // JWT signature/claim validation is local and must not open a database transaction.
        return tokens.authenticate(accessToken);
    }
}
