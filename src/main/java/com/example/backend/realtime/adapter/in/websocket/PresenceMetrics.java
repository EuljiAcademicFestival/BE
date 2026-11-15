package com.example.backend.realtime.adapter.in.websocket;

import com.example.backend.realtime.application.port.in.FestivalPresenceUseCase;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

@Component
public class PresenceMetrics {
    private final MeterRegistry metrics;
    public PresenceMetrics(PresenceSocketTransport transport, FestivalPresenceUseCase room, MeterRegistry metrics) {
        this.metrics = metrics;
        Gauge.builder("festival.websocket.connections",transport,PresenceSocketTransport::connectionCount).register(metrics);
        Gauge.builder("festival.websocket.players",room,FestivalPresenceUseCase::playerCount).register(metrics);
    }
    public void accepted(String type) {
        metrics.counter("festival.websocket.messages","result","accepted","type",type).increment();
    }
    public void ignoredClosedConnection() {
        // Not accepted and not a protocol failure: retain visibility of late messages after disconnect.
        metrics.counter("festival.websocket.messages", "result", "ignored", "type", "unknown").increment();
        metrics.counter("festival.websocket.ignored", "reason", "CONNECTION_CLOSED").increment();
    }
    public void rejected(String reason) {
        metrics.counter("festival.websocket.messages","result","rejected","type","invalid").increment();
        // Fixed labels only: no token, member ID, payload, or arbitrary exception text.
        String code = switch (reason) {
            case "CONNECTION_CLOSED", "RATE_LIMIT", "MESSAGE_TOO_LARGE", "INVALID_MESSAGE",
                 "AUTH_REQUIRED", "AUTH_INVALID", "INVALID_SEQUENCE", "INVALID_POSITION",
                 "ALREADY_AUTHENTICATED", "SPACE_FULL", "DUPLICATE_SESSION", "PROFILE_BUSY" -> reason;
            default -> "OTHER";
        };
        metrics.counter("festival.websocket.rejections", "reason", code).increment();
    }
}
