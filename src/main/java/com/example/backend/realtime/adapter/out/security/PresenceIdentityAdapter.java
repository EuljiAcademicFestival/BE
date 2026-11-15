package com.example.backend.realtime.adapter.out.security;

import com.example.backend.member.application.port.out.*;
import com.example.backend.realtime.application.port.out.PresenceIdentityPort;
import com.example.backend.realtime.domain.PlayerIdentity;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PresenceIdentityAdapter implements PresenceIdentityPort {
    private final AccessTokenPort tokens;
    private final MemberPort members;
    public Optional<PlayerIdentity> resolve(String token) {
        return tokens.authenticate(token).flatMap(members::findById)
                .map(m -> new PlayerIdentity(m.id(), m.characterId(), m.name(), m.grade()));
    }
    public boolean valid(String token, long memberId) {
        // Periodic expiry checks remain local. Movement and heartbeats never query the member database.
        return tokens.authenticate(token).filter(id -> id == memberId).isPresent();
    }
}
