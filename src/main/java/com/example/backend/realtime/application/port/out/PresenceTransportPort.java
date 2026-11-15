package com.example.backend.realtime.application.port.out;

import com.example.backend.realtime.application.result.PresenceEvent;

public interface PresenceTransportPort {
    boolean isOpen(String connectionId);
    boolean hasSubscribers();
    void activate(String connectionId);
    void send(String connectionId, PresenceEvent event);
    void broadcast(PresenceEvent event, String excludedConnectionId);
    void close(String connectionId, int status, String reason);
}
