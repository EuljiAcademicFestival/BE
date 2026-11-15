package com.example.backend.realtime;

import com.example.backend.realtime.adapter.in.websocket.PresenceSocketTransport;
import com.example.backend.realtime.application.result.PresenceEvent;
import com.example.backend.realtime.domain.PlayerIdentity;
import com.example.backend.realtime.domain.PlayerPose;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceWirePrecisionTest {
    final ObjectMapper mapper = new ObjectMapper();
    final List<String> frames = new ArrayList<>();

    PresenceSocketTransport connectedTransport() throws Exception {
        var socket = mock(StandardWebSocketSession.class);
        var session = mock(Session.class);
        var remote = mock(RemoteEndpoint.Async.class);
        when(socket.getId()).thenReturn("peer");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getNativeSession()).thenReturn(session);
        when(session.getAsyncRemote()).thenReturn(remote);
        doAnswer(inv -> {
            frames.add(inv.getArgument(0));
            inv.<SendHandler>getArgument(1).onResult(new SendResult());
            return null;
        }).when(remote).sendText(anyString(), any(SendHandler.class));
        var transport = new PresenceSocketTransport(mapper, Clock.systemUTC(), 200, 30);
        transport.register(socket);
        transport.activate("peer");
        frames.clear();
        return transport;
    }

    @Test void roundsAllPositionFramesButKeepsOriginalPoseAndSharedMapperUnchanged() throws Exception {
        var transport = connectedTransport();
        var pose = new PlayerPose(12.345678901234, -12.345678901234, -0.0004, 1.23456789);
        String original = mapper.writeValueAsString(pose);
        var player = new PresenceEvent.Player(new PlayerIdentity(1, 2, "회원", "게스트"), 123456789L, pose);
        transport.send("peer", new PresenceEvent("WELCOME", new PresenceEvent.Welcome(1, "festival", 123456789L, List.of(player))));
        transport.broadcast(new PresenceEvent("PLAYER_JOINED", player), null);
        transport.broadcast(new PresenceEvent("STATE", new PresenceEvent.State(123456789L, 123456789L,
                List.of(new PresenceEvent.Position(1, 123456789L, pose)))), null);
        assertThat(frames).hasSize(3);
        for (int i = 0; i < frames.size(); i++) {
            JsonNode data = mapper.readTree(frames.get(i)).path("data");
            JsonNode p = i == 1 ? data : data.path("players").get(0);
            assertThat(p.path("sequence").asLong()).isEqualTo(123456789L);
            assertThat(p.path("pose").path("x").isNumber()).isTrue();
            assertThat(p.at("/pose/x").asDouble()).isEqualTo(12.346);
            assertThat(p.at("/pose/y").asDouble()).isEqualTo(-12.346);
            assertThat(p.at("/pose/z").asDouble()).isZero();
            assertThat(p.at("/pose/yaw").asDouble()).isEqualTo(1.235);
            assertThat(frames.get(i)).contains("\"z\":0").doesNotContain("-0.0");
        }
        assertThat(mapper.writeValueAsString(pose)).isEqualTo(original);
        assertThat(pose.x()).isEqualTo(12.345678901234);
        assertThat(pose.yaw()).isEqualTo(1.23456789);
    }

    @Test void roundsPositiveAndNegativeTiesAndKeepsLimitsAndNumericTypes() throws Exception {
        var transport = connectedTransport();
        for (double value : new double[]{0, -0.0, 0.0005, -0.0005, 999.9995, -999.9995, 1000, -1000}) {
            var pose = new PlayerPose(value, value, value, value);
            transport.send("peer", new PresenceEvent("PLAYER_JOINED", new PresenceEvent.Player(
                    new PlayerIdentity(1, 1, "회원", "게스트"), 0, pose)));
            JsonNode wire = mapper.readTree(frames.get(frames.size() - 1)).at("/data/pose");
            for (String field : List.of("x", "y", "z", "yaw")) {
                assertThat(wire.path(field).isNumber()).isTrue();
                assertThat(Math.abs(wire.path(field).asDouble() - value)).isLessThanOrEqualTo(0.000500000001);
            }
        }
        assertThat(frames.get(2)).contains("\"x\":0.001");
        assertThat(frames.get(3)).contains("\"x\":-0.001");
        assertThat(frames.get(4)).contains("\"x\":1000");
        assertThat(frames.get(5)).contains("\"x\":-1000");
    }

    @Test void measuresRepresentativeTwoHundredPlayerFrameWithoutDroppingPlayers() throws Exception {
        var transport = connectedTransport();
        var players = new ArrayList<PresenceEvent.Position>();
        for (int i = 1; i <= 200; i++) {
            double angle = i * 0.1;
            players.add(new PresenceEvent.Position(i, 1234,
                    PlayerPose.validated(Math.cos(angle) * 10, 0, Math.sin(angle) * 10, angle, 1000)));
        }
        var event = new PresenceEvent("STATE", new PresenceEvent.State(1234, 1794747240000L, players));
        int before = mapper.writeValueAsBytes(event).length;
        transport.broadcast(event, null);
        int after = frames.get(0).getBytes(StandardCharsets.UTF_8).length;
        JsonNode wire = mapper.readTree(frames.get(0));
        assertThat(wire.at("/data/revision").asLong()).isEqualTo(1234);
        assertThat(wire.at("/data/serverTime").asLong()).isEqualTo(1794747240000L);
        assertThat(wire.at("/data/players").size()).isEqualTo(200);
        for (int i = 0; i < players.size(); i++) {
            JsonNode position = wire.at("/data/players").get(i);
            assertThat(position.path("memberId").asLong()).isEqualTo(players.get(i).memberId());
            assertThat(position.path("sequence").asLong()).isEqualTo(1234);
            double[] original = {players.get(i).pose().x(), players.get(i).pose().y(), players.get(i).pose().z(), players.get(i).pose().yaw()};
            int field = 0;
            for (String name : List.of("x", "y", "z", "yaw"))
                assertThat(Math.abs(position.path("pose").path(name).asDouble() - original[field++])).isLessThanOrEqualTo(0.000500000001);
        }
        assertThat(after).isLessThan(before);
        System.out.printf("200-player STATE fixture: %d -> %d bytes (%.2f%% smaller)%n", before, after, 100.0 * (before - after) / before);
    }
}
