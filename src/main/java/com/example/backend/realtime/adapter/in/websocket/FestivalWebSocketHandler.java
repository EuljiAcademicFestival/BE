package com.example.backend.realtime.adapter.in.websocket;

import com.example.backend.realtime.application.port.in.FestivalPresenceUseCase;
import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import com.example.backend.realtime.domain.PresenceException;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FestivalWebSocketHandler extends TextWebSocketHandler {
    private final FestivalPresenceUseCase presence;
    private final PresenceSocketTransport transport;
    private final ObjectMapper json;
    private final PresenceMetrics metrics;
    private final EngagementSyncUseCase engagement;

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception { transport.register(session); }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String id = session.getId();
        var admission = transport.admitMessage(id);
        if (admission != PresenceSocketTransport.MessageAdmission.ACCEPTED) {
            if (admission == PresenceSocketTransport.MessageAdmission.CONNECTION_CLOSED) {
                metrics.ignoredClosedConnection();
                return;
            }
            metrics.rejected(admission.name());
            if (admission == PresenceSocketTransport.MessageAdmission.RATE_LIMIT)
                transport.close(id, 1008, "RATE_LIMIT");
            return;
        }
        try {
            if (message.asBytes().length > 8192) throw new PresenceException("MESSAGE_TOO_LARGE");
            JsonNode body = json.readTree(message.getPayload());
            if (body == null || !body.isObject() || !body.path("type").isTextual())
                throw new PresenceException("INVALID_MESSAGE");
            String type = body.path("type").asText();
            if (!transport.authenticated(id)) {
                // The channel may have closed after admission, before the auth-state lookup.
                if (!transport.isOpen(id)) { metrics.ignoredClosedConnection(); return; }
                requireFields(body, Set.of("type", "accessToken"));
                if (!type.equals("AUTH") || !body.path("accessToken").isTextual()) throw new PresenceException("AUTH_REQUIRED");
                String token = body.path("accessToken").asText();
                if (token.isBlank() || token.length() > 4096) throw new PresenceException("AUTH_INVALID");
                presence.join(id, token);
                engagement.requestFullSync(id);
            } else if (type.equals("MOVE")) {
                requireFields(body, Set.of("type", "sequence", "x", "y", "z", "yaw"));
                if (!body.path("sequence").isIntegralNumber() || !body.path("sequence").canConvertToLong())
                    throw new PresenceException("INVALID_SEQUENCE");
                presence.move(id, body.get("sequence").longValue(), number(body, "x"), number(body, "y"),
                        number(body, "z"), number(body, "yaw"));
            } else if (type.equals("PING")) {
                requireFields(body, Set.of("type")); presence.ping(id);
            } else if (type.equals("SYNC_ENGAGEMENT")) {
                requireFields(body, Set.of("type"));
                engagement.requestFullSync(id);
            } else throw new PresenceException("INVALID_MESSAGE");
            metrics.accepted(type);
        } catch (PresenceException ex) {
            if (ex.getMessage().equals("CONNECTION_CLOSED") && !transport.isOpen(id)) {
                metrics.ignoredClosedConnection();
                return;
            }
            metrics.rejected(ex.getMessage());
            // Close reasons are fixed protocol codes; credentials and message bodies are never logged.
            transport.close(id, ex.getMessage().equals("SPACE_FULL") ? 1013 : 1008, ex.getMessage());
            presence.leave(id);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            metrics.rejected("INVALID_MESSAGE");
            transport.close(id, 1007, "INVALID_MESSAGE"); presence.leave(id);
        }
    }
    private static double number(JsonNode body, String field) {
        if (!body.path(field).isNumber()) throw new PresenceException("INVALID_POSITION");
        return body.get(field).doubleValue();
    }
    private static void requireFields(JsonNode body, Set<String> fields) {
        var names = body.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next())) throw new PresenceException("INVALID_MESSAGE");
        if (body.size() != fields.size()) throw new PresenceException("INVALID_MESSAGE");
    }
    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        transport.close(session.getId(), 1003, "TEXT_ONLY"); presence.leave(session.getId());
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        transport.remove(session.getId()); presence.leave(session.getId());
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) {
        transport.close(session.getId(), 1011, "TRANSPORT_ERROR"); presence.leave(session.getId());
    }
    @Scheduled(fixedDelay = 100)
    public void tick() { transport.expireUnauthenticated(); presence.tick(); }
}
