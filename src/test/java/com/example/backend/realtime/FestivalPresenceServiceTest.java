package com.example.backend.realtime;

import com.example.backend.realtime.application.presence.FestivalPresenceService;
import com.example.backend.realtime.application.port.out.*;
import com.example.backend.realtime.application.result.PresenceEvent;
import com.example.backend.realtime.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FestivalPresenceServiceTest {
    final PresenceIdentityPort identities = mock(PresenceIdentityPort.class);
    final PresenceTransportPort transport = mock(PresenceTransportPort.class);
    final MutableClock clock = new MutableClock();
    final FestivalPresenceService room = new FestivalPresenceService(identities, transport, clock, 200, 1000);

    void prepare(long id) {
        when(identities.resolve("token" + id)).thenReturn(Optional.of(new PlayerIdentity(id, 1, "회원" + id, "게스트")));
        when(identities.valid("token" + id, id)).thenReturn(true);
        when(transport.isOpen("c" + id)).thenReturn(true);
    }
    @Test void joinsWithRosterAndDisconnectDoesNotRemoveAnotherConnection() {
        prepare(1); prepare(2);
        room.join("c1", "token1"); room.join("c2", "token2");
        verify(transport).send(eq("c2"), argThat(e -> e.type().equals("WELCOME")
                && ((PresenceEvent.Welcome)e.data()).players().size() == 2));
        when(transport.isOpen("duplicate")).thenReturn(true);
        assertThatThrownBy(() -> room.join("duplicate", "token1")).hasMessage("DUPLICATE_SESSION");
        room.leave("duplicate");
        room.move("c1", 1, 1, 0, 2, 0);
        room.leave("c1");
        verify(transport).broadcast(new PresenceEvent("PLAYER_LEFT", new PresenceEvent.Left(1)), "c1");
        assertThatThrownBy(() -> room.move("c1", 2, 0, 0, 0, 0)).hasMessage("AUTH_REQUIRED");
    }
    @Test void twoHundredPlayersAndManyMovesUseNoIdentityLookupsUntilExpiryCheck() {
        for (int i=1;i<=200;i++) { prepare(i); room.join("c"+i,"token"+i); }
        clearInvocations(identities, transport);
        for (int sequence=0;sequence<10;sequence++) for (int i=1;i<=200;i++)
            room.move("c"+i, sequence, sequence, 0, i, 0);
        room.tick();
        verifyNoInteractions(identities);
        verify(transport).broadcast(argThat(e -> e.type().equals("STATE")
                && ((PresenceEvent.State)e.data()).players().size()==200), isNull());
        prepare(201);
        assertThatThrownBy(() -> room.join("c201","token201")).hasMessage("SPACE_FULL");
    }
    @Test void rejectsInvalidCoordinatesAndIgnoresOlderSequence() {
        prepare(1); room.join("c1","token1");
        room.move("c1", 2, 3, 0, 4, 0);
        room.move("c1", 1, 999, 0, 999, 0);
        assertThatThrownBy(() -> room.move("c1", 3, Double.NaN, 0, 0, 0)).hasMessage("INVALID_POSITION");
        assertThatThrownBy(() -> room.move("c1", 3, 1001, 0, 0, 0)).hasMessage("INVALID_POSITION");
        room.tick();
        verify(transport).broadcast(argThat(e -> e.type().equals("STATE")
                && ((PresenceEvent.State)e.data()).players().get(0).pose().x()==3), isNull());
    }
    @Test void roomKeepsFullPrecisionForStateAndLaterJoins() {
        prepare(1); prepare(2); room.join("c1", "token1");
        room.move("c1", 1, 12.345678901234, -0.0004, -4.987654321, 0.567891234);
        room.tick();
        verify(transport).broadcast(argThat(e -> e.type().equals("STATE")
                && ((PresenceEvent.State)e.data()).players().get(0).pose().x()==12.345678901234
                && ((PresenceEvent.State)e.data()).players().get(0).pose().y()==-0.0004), isNull());
        room.join("c2", "token2");
        verify(transport).send(eq("c2"), argThat(e -> e.type().equals("WELCOME")
                && ((PresenceEvent.Welcome)e.data()).players().stream().anyMatch(p ->
                p.identity().memberId()==1 && p.pose().x()==12.345678901234 && p.pose().z()==-4.987654321)));
    }
    @Test void profileCommitDuringJoinCannotPublishOldAppearance() {
        prepare(1);
        var updated = new PlayerIdentity(1,6,"새이름","졸업생");
        when(identities.resolve("token1")).thenAnswer(inv -> {
            room.updateProfile(updated);
            return Optional.of(new PlayerIdentity(1,1,"이전이름","게스트"));
        }).thenReturn(Optional.of(updated));
        room.join("c1","token1");
        verify(transport).send(eq("c1"),argThat(e -> e.type().equals("WELCOME")
                && ((PresenceEvent.Welcome)e.data()).players().get(0).identity().equals(updated)));
    }
    @Test void expiresJwtAndIdleConnectionsAndPublishesProfileChanges() {
        prepare(1); prepare(2); room.join("c1","token1"); room.join("c2","token2");
        var updated = new PlayerIdentity(2, 6, "새이름", "졸업생");
        room.updateProfile(updated);
        verify(transport).broadcast(new PresenceEvent("PLAYER_UPDATED", updated), null);
        when(identities.valid("token1",1)).thenReturn(false);
        clock.now = 1000; room.tick();
        verify(transport).close("c1",1008,"AUTH_EXPIRED");
        clock.now = 45_000; room.tick();
        verify(transport).close("c2",1008,"IDLE_TIMEOUT");
    }
    static class MutableClock extends Clock {
        long now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
