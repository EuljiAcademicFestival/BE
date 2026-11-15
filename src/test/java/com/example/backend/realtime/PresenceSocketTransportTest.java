package com.example.backend.realtime;

import com.example.backend.realtime.adapter.in.websocket.PresenceSocketTransport;
import com.example.backend.realtime.application.result.PresenceEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceSocketTransportTest {
    @Test void closesUnauthenticatedSocketAfterDeadlineAndEnforcesInputRate() throws Exception {
        var clock=new FestivalPresenceServiceTest.MutableClock();
        var socket=mock(StandardWebSocketSession.class);var nativeSession=mock(Session.class);
        when(socket.getId()).thenReturn("waiting");when(socket.isOpen()).thenReturn(true);
        when(socket.getNativeSession()).thenReturn(nativeSession);when(nativeSession.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));
        var transport=new PresenceSocketTransport(new ObjectMapper(),clock,200,30);
        transport.register(socket);
        for(int i=0;i<30;i++) assertThat(transport.acceptMessage("waiting")).isTrue();
        assertThat(transport.acceptMessage("waiting")).isFalse();
        clock.now=1000;assertThat(transport.acceptMessage("waiting")).isTrue();
        clock.now=5000;transport.expireUnauthenticated();
        verify(socket).close(argThat(status -> status.getCode()==1008&&status.getReason().equals("AUTH_TIMEOUT")));
    }

    @Test void unauthenticatedPeerReceivesNoBroadcastAndFramesCoalesceWhileSending() throws Exception {
        var socket=mock(StandardWebSocketSession.class);
        var nativeSession=mock(Session.class);var remote=mock(RemoteEndpoint.Async.class);
        when(socket.getId()).thenReturn("a");when(socket.isOpen()).thenReturn(true);
        when(socket.getNativeSession()).thenReturn(nativeSession);when(nativeSession.getAsyncRemote()).thenReturn(remote);
        var transport=new PresenceSocketTransport(new ObjectMapper(),Clock.systemUTC(),200,30);
        transport.register(socket);
        transport.broadcast(new PresenceEvent("PLAYER_JOINED",Map.of()),null);
        verify(remote,times(1)).sendText(anyString(),any(SendHandler.class));
        var sent=ArgumentCaptor.forClass(String.class);var callbacks=ArgumentCaptor.forClass(SendHandler.class);
        verify(remote).sendText(sent.capture(),callbacks.capture());
        assertThat(sent.getValue()).contains("AUTH_REQUIRED");
        callbacks.getValue().onResult(new SendResult());
        transport.activate("a");
        transport.broadcast(new PresenceEvent("STATE",1),null);
        transport.broadcast(new PresenceEvent("STATE",2),null);
        transport.broadcast(new PresenceEvent("STATE",3),null);
        verify(remote,times(2)).sendText(sent.capture(),callbacks.capture());
        callbacks.getValue().onResult(new SendResult());
        verify(remote,times(3)).sendText(sent.capture(),callbacks.capture());
        assertThat(sent.getValue()).contains("\"data\":3");
    }
    @Test void boundedControlQueueClosesSlowPeer() throws Exception {
        var socket=mock(StandardWebSocketSession.class);var nativeSession=mock(Session.class);
        when(socket.getId()).thenReturn("slow");when(socket.isOpen()).thenReturn(true);
        when(socket.getNativeSession()).thenReturn(nativeSession);when(nativeSession.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));
        var transport=new PresenceSocketTransport(new ObjectMapper(),Clock.systemUTC(),200,30);
        transport.register(socket);
        for(int i=0;i<17;i++) transport.send("slow",new PresenceEvent("PONG",i));
        assertThat(transport.isOpen("slow")).isFalse();
        verify(socket).close(argThat(status -> status.getCode()==1013));
    }
}
