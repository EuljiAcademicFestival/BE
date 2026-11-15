package com.example.backend.realtime;

import com.example.backend.global.infrastructure.config.SecurityConfig;
import com.example.backend.member.adapter.out.security.AccessTokenAdapter;
import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import com.example.backend.member.application.port.out.*;
import com.example.backend.member.domain.model.Member;
import com.example.backend.realtime.adapter.in.websocket.*;
import com.example.backend.realtime.adapter.out.security.PresenceIdentityAdapter;
import com.example.backend.realtime.application.presence.FestivalPresenceService;
import com.example.backend.realtime.application.sync.EngagementSyncService;
import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import com.example.backend.guestbook.application.port.out.EngagementQueryPort;
import com.example.backend.guestbook.application.result.EngagementSnapshot;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=FestivalWebSocketTests.Config.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class FestivalWebSocketTests {
    @Configuration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,PresenceWebSocketConfig.class,FestivalWebSocketHandler.class,
            PresenceSocketTransport.class,FestivalPresenceService.class,PresenceIdentityAdapter.class,PresenceMetrics.class,
            EngagementSyncService.class,EngagementTicker.class})
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean MemberPort memberPort() { return mock(MemberPort.class); }
        @Bean AccessTokenPort tokens(Clock clock) {
            return new AccessTokenAdapter(clock,Duration.ofHours(1),Base64.getEncoder().encodeToString(new byte[32]),"test");
        }
        @Bean AuthenticateMemberUseCase authentication(AccessTokenPort tokens) { return tokens::authenticate; }
        @Bean EngagementQueryPort engagementQueries() { return mock(EngagementQueryPort.class); }
    }
    @LocalServerPort int port;
    @Autowired AccessTokenPort tokens;
    @Autowired MemberPort members;
    @Autowired EngagementQueryPort engagementQueries;
    @Autowired EngagementSyncUseCase engagement;
    final List<Peer> peers=new ArrayList<>();
    final AtomicLong ids=new AtomicLong(System.nanoTime()%100000+1);
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {
        when(engagementQueries.snapshot(anySet(),anyBoolean())).thenReturn(new EngagementSnapshot(List.of(),List.of()));
        when(members.findById(anyLong())).thenAnswer(inv -> {
            long id=inv.getArgument(0);
            return Optional.of(new Member(id,"test@example.com","hash",1,"회원"+id,"게스트"));
        });
    }
    @AfterEach void disconnect() {
        for(var p:peers) {
            try {
                if(!p.socket.isOutputClosed()) p.socket.sendClose(WebSocket.NORMAL_CLOSURE,"done").get(2,TimeUnit.SECONDS);
                p.inbox.closed.get(3,TimeUnit.SECONDS);
            } catch(Exception ignored) { p.socket.abort(); }
        }
    }
    Peer open() throws Exception {
        var inbox=new Inbox();
        var socket=HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin","http://localhost:3000")
                .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/festival"),inbox).get(5,TimeUnit.SECONDS);
        var peer=new Peer(socket,inbox);peers.add(peer);inbox.await("AUTH_REQUIRED");return peer;
    }
    long auth(Peer peer) throws Exception {
        long id=ids.incrementAndGet();
        peer.socket.sendText(json.writeValueAsString(Map.of("type","AUTH","accessToken",tokens.issue(id))),true).get();
        assertThat(peer.inbox.await("WELCOME").path("data").path("memberId").asLong()).isEqualTo(id);
        return id;
    }
    @Test void actualSocketsShareJoinMoveAndLeaveWithoutMemberReadsOnMovement() throws Exception {
        Peer unauthenticated=open();Peer a=open();long aid=auth(a);Peer b=open();long bid=auth(b);
        assertThat(a.inbox.await("PLAYER_JOINED").at("/data/identity/memberId").asLong()).isEqualTo(bid);
        assertThat(unauthenticated.inbox.messages.poll(200,TimeUnit.MILLISECONDS)).isNull();
        clearInvocations(members);
        a.socket.sendText("{\"type\":\"MOVE\",\"sequence\":1,\"x\":3.123456789,\"y\":-0.0004,\"z\":-4.987654321,\"yaw\":0.567891234}",true).get();
        boolean moved=false;
        for(int i=0;i<10&&!moved;i++) for(JsonNode player:b.inbox.await("STATE").at("/data/players"))
            if(player.path("memberId").asLong()==aid&&player.path("sequence").asLong()==1) {
                assertThat(player.at("/pose/x").asDouble()).isEqualTo(3.123);
                assertThat(player.at("/pose/y").asDouble()).isZero();
                assertThat(player.at("/pose/z").asDouble()).isEqualTo(-4.988);
                assertThat(player.at("/pose/yaw").asDouble()).isEqualTo(0.568);moved=true;
            }
        assertThat(moved).isTrue();verifyNoInteractions(members);
        a.socket.sendClose(WebSocket.NORMAL_CLOSURE,"bye").get();
        assertThat(b.inbox.await("PLAYER_LEFT").at("/data/memberId").asLong()).isEqualTo(aid);
    }
    @Test void rejectsPreAuthenticationMovementInvalidJwtAndIdentitySpoofing() throws Exception {
        Peer first=open();
        first.socket.sendText("{\"type\":\"MOVE\",\"sequence\":1,\"x\":0,\"y\":0,\"z\":0,\"yaw\":0}",true).get();
        assertThat(first.inbox.closed.get(3,TimeUnit.SECONDS)).isEqualTo(1008);
        Peer invalid=open();invalid.socket.sendText("{\"type\":\"AUTH\",\"accessToken\":\"bad\"}",true).get();
        assertThat(invalid.inbox.closed.get(3,TimeUnit.SECONDS)).isEqualTo(1008);
        Peer spoof=open();auth(spoof);
        spoof.socket.sendText("{\"type\":\"MOVE\",\"sequence\":1,\"memberId\":999,\"x\":0,\"y\":0,\"z\":0,\"yaw\":0}",true).get();
        assertThat(spoof.inbox.closed.get(3,TimeUnit.SECONDS)).isEqualTo(1008);
    }
    @Test void duplicateConnectionCannotDisplaceExistingPlayerAndPingWorks() throws Exception {
        Peer a=open();long id=auth(a);Peer duplicate=open();
        duplicate.socket.sendText(json.writeValueAsString(Map.of("type","AUTH","accessToken",tokens.issue(id))),true).get();
        assertThat(duplicate.inbox.closed.get(3,TimeUnit.SECONDS)).isEqualTo(1008);
        a.socket.sendText("{\"type\":\"PING\"}",true).get();assertThat(a.inbox.await("PONG").at("/data/serverTime").asLong()).isPositive();
    }
    @Test void handshakeRejectsUnlistedBrowserOrigin() {
        assertThatThrownBy(() -> HttpClient.newHttpClient().newWebSocketBuilder().header("Origin","https://untrusted.invalid")
                .buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/festival"),new Inbox()).get(3,TimeUnit.SECONDS))
                .isInstanceOfSatisfying(ExecutionException.class,e -> {
                    assertThat(e.getCause()).isInstanceOf(WebSocketHandshakeException.class);
                    assertThat(((WebSocketHandshakeException)e.getCause()).getResponse().statusCode()).isEqualTo(403);
                });
    }
    @Test void socketsReceiveInitialSnapshotAndBatchedCommonLikeAndRankingChanges() throws Exception {
        Peer a=open();auth(a);Peer b=open();auth(b);
        assertThat(a.inbox.await("ENGAGEMENT_STATE").at("/data/full").asBoolean()).isTrue();
        assertThat(b.inbox.await("ENGAGEMENT_STATE").at("/data/full").asBoolean()).isTrue();
        var updated=new EngagementSnapshot(List.of(new EngagementSnapshot.Count(15,9)),
                List.of(new EngagementSnapshot.Ranking(15,"2학년","이름","환영",9,1)));
        when(engagementQueries.snapshot(anySet(),anyBoolean())).thenReturn(updated);
        engagement.changed(15);
        for(Peer peer:List.of(a,b)) {
            JsonNode event;
            do { event=peer.inbox.await("ENGAGEMENT_STATE"); } while(event.at("/data/counts").isEmpty());
            assertThat(event.at("/data/counts/0/likeCount").asLong()).isEqualTo(9);
            assertThat(event.at("/data/rankings/0/rank").asInt()).isEqualTo(1);
            assertThat(event.findValues("likedByMe")).isEmpty();
        }
        a.socket.sendText("{\"type\":\"SYNC_ENGAGEMENT\"}",true).get();
        assertThat(a.inbox.await("ENGAGEMENT_STATE").at("/data/full").asBoolean()).isTrue();
    }
    record Peer(WebSocket socket,Inbox inbox) {}
    static class Inbox implements WebSocket.Listener {
        final BlockingQueue<JsonNode> messages=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final StringBuilder fragments=new StringBuilder();
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last) {
            fragments.append(text);
            if(last) {
                try { messages.add(new ObjectMapper().readTree(fragments.toString())); }
                catch(Exception e) { closed.completeExceptionally(e); }
                fragments.setLength(0);
            }
            socket.request(1);return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<?> onClose(WebSocket socket,int status,String reason) {
            closed.complete(status);return CompletableFuture.completedFuture(null);
        }
        public void onError(WebSocket socket,Throwable error) { closed.completeExceptionally(error); }
        JsonNode await(String type) throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline) {
                JsonNode msg=messages.poll(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                if(msg!=null&&msg.path("type").asText().equals(type)) return msg;
            }
            throw new AssertionError("Missing WebSocket event "+type);
        }
    }
}
