package com.example.backend.realtime.application.sync;

import com.example.backend.guestbook.application.port.out.EngagementQueryPort;
import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import com.example.backend.realtime.application.port.out.PresenceTransportPort;
import com.example.backend.realtime.application.result.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;

/** SQL and socket publishing run after commit, outside the small pending-change monitor. */
@Service
public class EngagementSyncService implements EngagementSyncUseCase {
    private static final Logger log = LoggerFactory.getLogger(EngagementSyncService.class);
    private static final int MAX_DIRTY_IDS = 2048;
    private final EngagementQueryPort queries;
    private final PresenceTransportPort transport;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final Set<Long> dirty = new LinkedHashSet<>();
    private final Set<String> syncRequests = new LinkedHashSet<>();
    private boolean fullRefresh;
    private boolean inFlight;
    private boolean failureLogged;
    private long revision;

    public EngagementSyncService(EngagementQueryPort queries, PresenceTransportPort transport, Clock clock, MeterRegistry metrics) {
        this.queries = queries; this.transport = transport; this.clock = clock; this.metrics = metrics;
    }
    public synchronized void changed(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("Invalid changed member ID");
        if (fullRefresh) return;
        if (dirty.size() >= MAX_DIRTY_IDS && !dirty.contains(memberId)) {
            fullRefresh = true; dirty.clear();
        } else dirty.add(memberId);
    }
    public synchronized void requestFullSync(String connectionId) {
        syncRequests.removeIf(id -> !transport.isOpen(id));
        if (transport.isOpen(connectionId)) syncRequests.add(connectionId);
    }
    public void flush() {
        Set<Long> changed;
        Set<String> requested;
        boolean full, broadcast;
        synchronized (this) {
            if (inFlight || !transport.hasSubscribers() || (!fullRefresh && dirty.isEmpty() && syncRequests.isEmpty())) return;
            changed = Set.copyOf(dirty); requested = Set.copyOf(syncRequests);
            broadcast = fullRefresh || !dirty.isEmpty(); full = fullRefresh || !requested.isEmpty();
            dirty.clear(); syncRequests.clear(); fullRefresh = false; inFlight = true;
        }
        try {
            var snapshot = queries.snapshot(changed, full);
            long next;
            synchronized (this) { next = ++revision; failureLogged = false; }
            var event = new PresenceEvent("ENGAGEMENT_STATE", new EngagementState(next, clock.millis(), full,
                    snapshot.counts(), snapshot.rankings()));
            if (broadcast) transport.broadcast(event, null);
            else for (String id : requested) if (transport.isOpen(id)) transport.send(id, event);
            metrics.counter("festival.engagement.flushes", "result", "success").increment();
        } catch (RuntimeException failure) {
            synchronized (this) {
                if (full || fullRefresh) { fullRefresh = true; dirty.clear(); }
                else for (long id : changed) changed(id);
                syncRequests.addAll(requested);
                if (!failureLogged) {
                    log.warn("Engagement sync failed; pending changes retained for retry"); failureLogged = true;
                }
            }
            metrics.counter("festival.engagement.flushes", "result", "failure").increment();
        } finally { synchronized (this) { inFlight = false; } }
    }
}
