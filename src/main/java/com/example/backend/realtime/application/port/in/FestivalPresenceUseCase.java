package com.example.backend.realtime.application.port.in;

import com.example.backend.realtime.domain.PlayerIdentity;

public interface FestivalPresenceUseCase {
    void join(String connectionId, String token);
    void move(String connectionId, long sequence, double x, double y, double z, double yaw);
    void ping(String connectionId);
    void leave(String connectionId);
    void updateProfile(PlayerIdentity identity);
    void tick();
    int playerCount();
}
