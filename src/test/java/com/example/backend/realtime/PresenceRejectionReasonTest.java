package com.example.backend.realtime;

import com.example.backend.realtime.adapter.in.websocket.*;
import com.example.backend.realtime.application.port.in.*;
import com.example.backend.realtime.application.port.out.PresenceIdentityPort;
import com.example.backend.realtime.application.presence.FestivalPresenceService;
import com.example.backend.realtime.domain.PlayerIdentity;
import com.example.backend.realtime.domain.PresenceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.websocket.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import java.util.Optional;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceRejectionReasonTest {
    final StandardWebSocketSession socket = mock(StandardWebSocketSession.class);
    final FestivalPresenceUseCase room = mock(FestivalPresenceUseCase.class);
    final EngagementSyncUseCase engagement = mock(EngagementSyncUseCase.class);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FestivalPresenceServiceTest.MutableClock clock = new FestivalPresenceServiceTest.MutableClock();
    final PresenceSocketTransport transport = new PresenceSocketTransport(new ObjectMapper(), clock, 200, 30);
    final PresenceMetrics metrics = new PresenceMetrics(transport, room, registry);
    final FestivalWebSocketHandler handler = new FestivalWebSocketHandler(room, transport, new ObjectMapper(), metrics, engagement);
    static final String MOVE = "{\"type\":\"MOVE\",\"sequence\":1,\"x\":1,\"y\":0,\"z\":2,\"yaw\":0}";

    void connect() throws Exception {
        var session = mock(Session.class);
        when(socket.getId()).thenReturn("peer");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getNativeSession()).thenReturn(session);
        when(session.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));
        transport.register(socket);
        transport.activate("peer");
    }
    double rejected(String reason) {
        var c = registry.find("festival.websocket.rejections").tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }
    double ignored() {
        var c = registry.find("festival.websocket.ignored").tag("reason", "CONNECTION_CLOSED").counter();
        return c == null ? 0 : c.count();
    }
    void assertNoRejections() {
        assertThat(registry.find("festival.websocket.messages").tag("result", "rejected").counter()).isNull();
        assertThat(registry.find("festival.websocket.rejections").counters()).isEmpty();
    }

    @Test void finalMoveAfterConnectionCleanupIsClosedConnectionRatherThanRateLimit() throws Exception {
        connect();
        handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
        clearInvocations(room);
        handler.handleMessage(socket, new TextMessage(MOVE));
        assertThat(ignored()).isEqualTo(1);
        assertNoRejections();
        verifyNoInteractions(room, engagement);
        verify(socket, never()).close(any(CloseStatus.class));
    }
    @Test void physicallyClosedSocketStillInMapIsAlsoClosedConnection() throws Exception {
        connect();
        when(socket.isOpen()).thenReturn(false);
        handler.handleMessage(socket, new TextMessage(MOVE));
        assertThat(ignored()).isEqualTo(1);
        assertNoRejections();
        verifyNoInteractions(room);
    }
    @Test void realRateLimitIsStillEnforcedAndSeparateFromClosedConnections() throws Exception {
        connect();
        for (int i=0; i<31; i++) handler.handleMessage(socket, new TextMessage("{\"type\":\"PING\"}"));
        assertThat(rejected("RATE_LIMIT")).isEqualTo(1);
        assertThat(rejected("CONNECTION_CLOSED")).isZero();
        verify(room, times(30)).ping("peer");
        verify(socket).close(argThat(s -> s.getCode()==1008 && s.getReason().equals("RATE_LIMIT")));
    }
    @Test void protocolFailuresAndUnknownReasonsHaveBoundedLabels() throws Exception {
        connect();
        doThrow(new PresenceException("AUTH_REQUIRED")).when(room).move("peer", 1, 1, 0, 2, 0);
        handler.handleMessage(socket, new TextMessage(MOVE));
        assertThat(rejected("AUTH_REQUIRED")).isEqualTo(1);
        metrics.rejected("arbitrary-untrusted-text");
        assertThat(rejected("OTHER")).isEqualTo(1);
        assertThat(registry.find("festival.websocket.rejections").tag("reason", "arbitrary-untrusted-text").counter()).isNull();
    }

    @Test void openConnectionWithMissingPlayerStillFailsAuthentication() throws Exception {
        departureRace(false);
        assertThat(rejected("AUTH_REQUIRED")).isEqualTo(1);
        assertThat(ignored()).isZero();
        verify(socket).close(argThat(s -> s.getCode()==1008 && s.getReason().equals("AUTH_REQUIRED")));
    }

    @Test void moveWaitingForRoomLockIsIgnoredIfConnectionAndPlayerAreRemoved() throws Exception {
        departureRace(true);
        assertThat(ignored()).isEqualTo(1);
        assertNoRejections();
        verify(socket, never()).close(any(CloseStatus.class));
    }

    void departureRace(boolean closed) throws Exception {
        connect();
        var identities = mock(PresenceIdentityPort.class);
        when(identities.resolve("token")).thenReturn(Optional.of(new PlayerIdentity(1, 1, "회원", "게스트")));
        var realRoom = new FestivalPresenceService(identities, transport, clock, 200, 1000);
        realRoom.join("peer", "token");
        var observedTransport = spy(transport);
        var authenticated = new CountDownLatch(1);
        doAnswer(inv -> { authenticated.countDown(); return true; }).when(observedTransport).authenticated("peer");
        var observedHandler = new FestivalWebSocketHandler(realRoom, observedTransport, new ObjectMapper(), metrics, engagement);
        CompletableFuture<Void> pendingMove;
        synchronized (realRoom) {
            pendingMove = CompletableFuture.runAsync(() -> {
                try { observedHandler.handleMessage(socket, new TextMessage(MOVE)); }
                catch (Exception ex) { throw new CompletionException(ex); }
            });
            assertThat(authenticated.await(3, TimeUnit.SECONDS)).isTrue();
            // The MOVE passed transport auth, but cannot enter synchronized room.move yet.
            if (closed) observedHandler.afterConnectionClosed(socket, CloseStatus.NORMAL);
            else realRoom.leave("peer");
        }
        pendingMove.get(3, TimeUnit.SECONDS);
        assertThat(realRoom.playerCount()).isZero();
    }

    @Test void closureBetweenAdmissionAndAuthenticationLookupIsIgnored() throws Exception {
        connect();
        var observed = spy(transport);
        doAnswer(inv -> {
            transport.remove("peer");
            return PresenceSocketTransport.MessageAdmission.ACCEPTED;
        }).when(observed).admitMessage("peer");
        var h = new FestivalWebSocketHandler(room, observed, new ObjectMapper(), metrics, engagement);
        h.handleMessage(socket, new TextMessage(MOVE));
        assertThat(ignored()).isEqualTo(1);
        assertNoRejections();
        verifyNoInteractions(room);
    }

    @Test void invalidCoordinatesOnOpenConnectionRemainRejected() throws Exception {
        connect();
        doThrow(new PresenceException("INVALID_POSITION")).when(room).move("peer", 1, 1, 0, 2, 0);
        handler.handleMessage(socket, new TextMessage(MOVE));
        assertThat(rejected("INVALID_POSITION")).isEqualTo(1);
        assertThat(ignored()).isZero();
        verify(socket).close(argThat(s -> s.getCode()==1008 && s.getReason().equals("INVALID_POSITION")));
    }
}
