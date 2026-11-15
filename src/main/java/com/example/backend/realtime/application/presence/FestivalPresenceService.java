package com.example.backend.realtime.application.presence;

import com.example.backend.realtime.application.port.in.FestivalPresenceUseCase;
import com.example.backend.realtime.application.port.out.*;
import com.example.backend.realtime.application.result.PresenceEvent;
import com.example.backend.realtime.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;

/** High-frequency processing deliberately lives outside the per-method performance-log pointcut. */
@Service
public class FestivalPresenceService implements FestivalPresenceUseCase {
    private final PresenceIdentityPort identities;
    private final PresenceTransportPort transport;
    private final Clock clock;
    private final int capacity;
    private final double coordinateLimit;
    private final Map<String, ConnectedPlayer> players = new LinkedHashMap<>();
    private long lastValidation;
    private long revision;
    private long profileRevision;
    private boolean dirty;

    public FestivalPresenceService(PresenceIdentityPort identities, PresenceTransportPort transport, Clock clock,
            @Value("${realtime.max-players:200}") int capacity,
            @Value("${realtime.coordinate-limit:1000}") double coordinateLimit) {
        if (capacity < 1 || capacity > 1000 || !Double.isFinite(coordinateLimit) || coordinateLimit <= 0)
            throw new IllegalArgumentException("Invalid realtime capacity or coordinate limit");
        this.identities = identities; this.transport = transport; this.clock = clock;
        this.capacity = capacity; this.coordinateLimit = coordinateLimit;
    }

    public void join(String connectionId, String token) {
        // JWT verification and the normal single profile lookup happen before acquiring the room monitor.
        // Retry if a profile committed between that lookup and admission, without holding the room lock over SQL.
        for (int attempt = 0; attempt < 3; attempt++) {
            long observedProfileRevision;
            synchronized (this) { observedProfileRevision = profileRevision; }
            PlayerIdentity identity = identities.resolve(token).orElseThrow(() -> new PresenceException("AUTH_INVALID"));
            synchronized (this) {
                if (observedProfileRevision != profileRevision) continue;
                if (!transport.isOpen(connectionId)) throw new PresenceException("CONNECTION_CLOSED");
                if (players.containsKey(connectionId)) throw new PresenceException("ALREADY_AUTHENTICATED");
                if (players.size() >= capacity) throw new PresenceException("SPACE_FULL");
                if (players.values().stream().anyMatch(p -> p.identity.memberId() == identity.memberId()))
                    throw new PresenceException("DUPLICATE_SESSION");
                var player = new ConnectedPlayer(identity, token, clock.millis());
                players.put(connectionId, player);
                transport.activate(connectionId);
                transport.send(connectionId, new PresenceEvent("WELCOME", new PresenceEvent.Welcome(identity.memberId(),
                        "festival", clock.millis(), players.values().stream().map(ConnectedPlayer::view).toList())));
                transport.broadcast(new PresenceEvent("PLAYER_JOINED", player.view()), connectionId);
                dirty = true;
                return;
            }
        }
        throw new PresenceException("PROFILE_BUSY");
    }

    public synchronized void move(String connectionId, long sequence, double x, double y, double z, double yaw) {
        var player = requirePlayer(connectionId);
        if (sequence < 0) throw new PresenceException("INVALID_SEQUENCE");
        var pose = PlayerPose.validated(x, y, z, yaw, coordinateLimit);
        if (sequence <= player.sequence) return;
        player.sequence = sequence; player.pose = pose; player.lastSeen = clock.millis(); dirty = true;
    }

    public synchronized void ping(String connectionId) {
        var player = requirePlayer(connectionId);
        player.lastSeen = clock.millis();
        transport.send(connectionId, new PresenceEvent("PONG", Map.of("serverTime", clock.millis())));
    }

    public synchronized void leave(String connectionId) {
        var player = players.remove(connectionId);
        if (player != null) {
            transport.broadcast(new PresenceEvent("PLAYER_LEFT", new PresenceEvent.Left(player.identity.memberId())), connectionId);
            dirty = true;
        }
    }

    public synchronized void updateProfile(PlayerIdentity identity) {
        profileRevision++;
        players.values().stream().filter(p -> p.identity.memberId() == identity.memberId()).findFirst().ifPresent(p -> {
            p.identity = identity;
            transport.broadcast(new PresenceEvent("PLAYER_UPDATED", identity), null);
        });
    }

    public synchronized void tick() {
        long now = clock.millis();
        if (now - lastValidation >= 1000) {
            lastValidation = now;
            for (var entry : new ArrayList<>(players.entrySet())) {
                var player = entry.getValue();
                String reason = !transport.isOpen(entry.getKey()) ? "CONNECTION_CLOSED"
                        : now - player.lastSeen >= 45_000 ? "IDLE_TIMEOUT"
                        : !identities.valid(player.token, player.identity.memberId()) ? "AUTH_EXPIRED" : null;
                if (reason != null) {
                    leave(entry.getKey()); transport.close(entry.getKey(), 1008, reason);
                }
            }
        }
        if (dirty && !players.isEmpty()) {
            dirty = false;
            // A complete position frame permits safe replacement of an unsent older frame for slow consumers.
            var positions = players.values().stream()
                    .map(p -> new PresenceEvent.Position(p.identity.memberId(), p.sequence, p.pose)).toList();
            transport.broadcast(new PresenceEvent("STATE", new PresenceEvent.State(++revision, now, positions)), null);
        }
    }

    private ConnectedPlayer requirePlayer(String id) {
        var player = players.get(id);
        if (player == null) {
            // A MOVE/PING can pass transport admission before concurrent departure takes the room lock.
            if (!transport.isOpen(id)) throw new PresenceException("CONNECTION_CLOSED");
            throw new PresenceException("AUTH_REQUIRED");
        }
        return player;
    }

    public synchronized int playerCount() { return players.size(); }

    private static final class ConnectedPlayer {
        PlayerIdentity identity;
        final String token;
        long sequence = -1;
        PlayerPose pose = new PlayerPose(0, 0, 0, 0);
        long lastSeen;
        ConnectedPlayer(PlayerIdentity identity, String token, long now) {
            this.identity = identity; this.token = token; this.lastSeen = now;
        }
        PresenceEvent.Player view() { return new PresenceEvent.Player(identity, sequence, pose); }
    }
}
