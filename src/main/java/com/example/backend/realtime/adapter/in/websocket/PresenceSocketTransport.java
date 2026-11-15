package com.example.backend.realtime.adapter.in.websocket;

import com.example.backend.realtime.application.port.out.PresenceTransportPort;
import com.example.backend.realtime.application.result.PresenceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.example.backend.realtime.domain.PlayerPose;
import jakarta.websocket.RemoteEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** One asynchronous write per peer, bounded controls, and only the latest pending full position frame. */
@Component
public class PresenceSocketTransport implements PresenceTransportPort {
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final ObjectWriter json;
    private final Clock clock;
    private final int maxConnections;
    private final int maxMessages;

    public PresenceSocketTransport(ObjectMapper json, Clock clock,
            @Value("${realtime.max-players:200}") int capacity,
            @Value("${realtime.max-messages-per-second:30}") int maxMessages) {
        if (maxMessages < 10 || maxMessages > 1000) throw new IllegalArgumentException("Invalid message rate");
        var module = new SimpleModule();
        module.addSerializer(PlayerPose.class, new PlayerPoseJsonSerializer());
        // Scope rounding to outgoing WebSocket frames; keep room state and the shared mapper unchanged.
        this.json = json.copy().registerModule(module).writer();
        this.clock = clock; this.maxConnections = capacity + 32; this.maxMessages = maxMessages;
    }

    public synchronized boolean register(WebSocketSession session) throws IOException {
        if (channels.size() >= maxConnections || !(session instanceof StandardWebSocketSession standard)) {
            session.close(new CloseStatus(1013, "CONNECTION_LIMIT")); return false;
        }
        session.setTextMessageSizeLimit(8192);
        RemoteEndpoint.Async remote = standard.getNativeSession().getAsyncRemote();
        remote.setSendTimeout(1000);
        channels.put(session.getId(), new Channel(session, remote, clock.millis()));
        send(session.getId(), new PresenceEvent("AUTH_REQUIRED", Map.of("timeoutMs", 5000)));
        return true;
    }

    public void remove(String id) { channels.remove(id); }
    public int connectionCount() { return channels.size(); }
    public boolean authenticated(String id) { var c = channels.get(id); return c != null && c.active; }
    public boolean isOpen(String id) { var c = channels.get(id); return c != null && c.session.isOpen(); }
    public boolean hasSubscribers() { return channels.values().stream().anyMatch(c -> c.active && c.session.isOpen()); }
    public void activate(String id) { var c = channels.get(id); if (c != null) c.active = true; }

    public enum MessageAdmission { ACCEPTED, CONNECTION_CLOSED, RATE_LIMIT }

    public boolean acceptMessage(String id) { return admitMessage(id) == MessageAdmission.ACCEPTED; }

    public MessageAdmission admitMessage(String id) {
        var c = channels.get(id);
        if (c == null) return MessageAdmission.CONNECTION_CLOSED;
        synchronized (c) {
            if (channels.get(id) != c || !c.session.isOpen()) return MessageAdmission.CONNECTION_CLOSED;
            long window = clock.millis() / 1000;
            if (window != c.window) { c.window = window; c.received = 0; }
            return ++c.received <= maxMessages ? MessageAdmission.ACCEPTED : MessageAdmission.RATE_LIMIT;
        }
    }

    public void expireUnauthenticated() {
        long now = clock.millis();
        for (var c : channels.values()) if (!c.active && now - c.opened >= 5000)
            close(c.session.getId(), 1008, "AUTH_TIMEOUT");
    }

    public void send(String id, PresenceEvent event) { enqueue(id, encode(event), event.type().equals("STATE")); }
    public void broadcast(PresenceEvent event, String excluded) {
        String payload = encode(event);
        for (var entry : channels.entrySet()) if (entry.getValue().active && !entry.getKey().equals(excluded))
            enqueue(entry.getKey(), payload, event.type().equals("STATE"));
    }
    private String encode(PresenceEvent event) {
        try { return json.writeValueAsString(event); }
        catch (IOException ex) { throw new IllegalStateException("Cannot encode presence event", ex); }
    }
    private void enqueue(String id, String payload, boolean state) {
        var c = channels.get(id);
        if (c == null || !c.session.isOpen()) return;
        boolean overflow = false;
        synchronized (c) {
            if (state) c.latestState = payload;
            else if (c.controls.size() < 16) c.controls.add(payload);
            else overflow = true;
        }
        if (overflow) close(id, 1013, "SLOW_CONSUMER");
        else drain(c);
    }
    private void drain(Channel c) {
        if (!c.writing.compareAndSet(false, true)) return;
        String next;
        synchronized (c) {
            next = c.controls.poll();
            if (next == null) { next = c.latestState; c.latestState = null; }
            if (next == null) { c.writing.set(false); return; }
        }
        try {
            c.remote.sendText(next, result -> {
                c.writing.set(false);
                if (!result.isOK()) close(c.session.getId(), 1013, "SEND_FAILED");
                else if (channels.get(c.session.getId()) == c) drain(c);
            });
        } catch (RuntimeException failed) {
            c.writing.set(false); close(c.session.getId(), 1013, "SEND_FAILED");
        }
    }
    public void close(String id, int status, String reason) {
        var c = channels.remove(id);
        if (c == null) return;
        synchronized (c) { c.controls.clear(); c.latestState = null; }
        try { c.session.close(new CloseStatus(status, reason)); }
        catch (IOException ignored) { /* Transport already disconnected. Room cleanup also runs on tick. */ }
    }
    private static final class Channel {
        final WebSocketSession session;
        final RemoteEndpoint.Async remote;
        final long opened;
        final ArrayDeque<String> controls = new ArrayDeque<>();
        final AtomicBoolean writing = new AtomicBoolean();
        volatile boolean active;
        String latestState;
        long window;
        int received;
        Channel(WebSocketSession session, RemoteEndpoint.Async remote, long now) {
            this.session = session; this.remote = remote; this.opened = now;
        }
    }
}
