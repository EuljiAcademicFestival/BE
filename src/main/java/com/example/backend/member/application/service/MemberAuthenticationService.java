package com.example.backend.member.application.service;

import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import com.example.backend.member.application.port.out.AccessTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberAuthenticationService implements AuthenticateMemberUseCase {
    private final AccessTokenPort tokens;

    @Override
    public Optional<Long> authenticate(String accessToken) {
        return tokens.authenticate(accessToken);
    }
}
