package com.example.backend.realtime.application.port.out;

import com.example.backend.realtime.domain.PlayerIdentity;
import java.util.Optional;

public interface PresenceIdentityPort {
    Optional<PlayerIdentity> resolve(String token);
    boolean valid(String token, long memberId);
}
