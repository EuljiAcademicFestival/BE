package com.example.backend.realtime;

import com.example.backend.guestbook.application.port.out.EngagementQueryPort;
import com.example.backend.guestbook.application.result.EngagementSnapshot;
import com.example.backend.realtime.application.sync.EngagementSyncService;
import com.example.backend.realtime.application.port.out.PresenceTransportPort;
import com.example.backend.realtime.application.result.EngagementState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngagementSyncServiceTest {
    final EngagementQueryPort queries=mock(EngagementQueryPort.class);
    final PresenceTransportPort transport=mock(PresenceTransportPort.class);
    final SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    final EngagementSyncService sync=new EngagementSyncService(queries,transport,Clock.systemUTC(),metrics);
    final EngagementSnapshot snapshot=new EngagementSnapshot(List.of(new EngagementSnapshot.Count(1,7)),
            List.of(new EngagementSnapshot.Ranking(1,"게스트","이름","환영",7,1)));

    @Test void batchesManyChangesAndDoesNotQueryWhenIdleOrWithoutSubscribers() {
        when(queries.snapshot(anySet(),anyBoolean())).thenReturn(snapshot);
        for(int i=0;i<100;i++) sync.changed(1);
        sync.flush();verifyNoInteractions(queries);
        when(transport.hasSubscribers()).thenReturn(true);
        sync.flush();sync.flush();
        verify(queries,times(1)).snapshot(Set.of(1L),false);
        verify(transport).broadcast(argThat(event -> event.type().equals("ENGAGEMENT_STATE")
                && ((EngagementState)event.data()).counts().equals(snapshot.counts())),isNull());
    }
    @Test void failureRetainsChangesAndNextAttemptRecovers() {
        when(transport.hasSubscribers()).thenReturn(true);
        when(queries.snapshot(Set.of(1L),false)).thenThrow(new IllegalStateException("offline")).thenReturn(snapshot);
        sync.changed(1);sync.flush();verify(transport,never()).broadcast(any(),any());
        sync.flush();
        verify(queries,times(2)).snapshot(Set.of(1L),false);
        verify(transport).broadcast(any(),isNull());
        assertThat(metrics.get("festival.engagement.flushes").tag("result","failure").counter().count()).isEqualTo(1);
    }
    @Test void fullResyncIsUnicastAndUsesOneStatementSnapshot() {
        when(transport.hasSubscribers()).thenReturn(true);when(transport.isOpen("new")).thenReturn(true);
        when(queries.snapshot(Set.of(),true)).thenReturn(snapshot);
        sync.requestFullSync("new");sync.flush();
        verify(queries).snapshot(Set.of(),true);
        verify(transport).send(eq("new"),argThat(event -> ((EngagementState)event.data()).full()));
        verify(transport,never()).broadcast(any(),any());
    }
    @Test void commitsArrivingDuringSlowQueryAreKeptForNextBatch() throws Exception {
        when(transport.hasSubscribers()).thenReturn(true);
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(queries.snapshot(anySet(),anyBoolean())).thenAnswer(inv -> {
            started.countDown();if(!release.await(3,TimeUnit.SECONDS)) throw new AssertionError("Release query");
            return snapshot;
        });
        sync.changed(1);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var running=executor.submit(sync::flush);
            assertThat(started.await(3,TimeUnit.SECONDS)).isTrue();
            sync.changed(2);sync.flush();
            verify(queries,times(1)).snapshot(anySet(),anyBoolean());
            release.countDown();running.get(3,TimeUnit.SECONDS);sync.flush();
            verify(queries).snapshot(Set.of(1L),false);
            verify(queries).snapshot(Set.of(2L),false);
        } finally { release.countDown();executor.shutdownNow(); }
    }
    @Test void dirtyIdBufferFallsBackToFullSnapshotInsteadOfGrowingWithoutLimit() {
        when(transport.hasSubscribers()).thenReturn(true);
        when(queries.snapshot(anySet(),anyBoolean())).thenReturn(snapshot);
        for(long id=1;id<=3000;id++) sync.changed(id);
        sync.flush();verify(queries).snapshot(Set.of(),true);
    }
}
