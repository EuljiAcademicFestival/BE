package com.example.backend.guestbook.adapter.out.cache;

import com.example.backend.guestbook.application.port.out.CharacterLabelCachePort;
import com.example.backend.guestbook.application.result.CharacterLabel;
import com.github.benmanes.caffeine.cache.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Component
public class CaffeineCharacterLabelCache implements CharacterLabelCachePort {
    private static final int STRIPES = 256;
    private final Cache<Long, CharacterLabel> cache;
    private final ReentrantLock[] loads = new ReentrantLock[STRIPES];
    private final Object[] publications = new Object[STRIPES];
    private final AtomicLongArray versions = new AtomicLongArray(STRIPES);

    @Autowired
    public CaffeineCharacterLabelCache(@Value("${cache.character-labels.ttl:PT5M}") Duration ttl,
                                       @Value("${cache.character-labels.maximum-size:10000}") long maximumSize,
                                       MeterRegistry metrics) {
        this(ttl, maximumSize, metrics, Ticker.systemTicker());
    }
    CaffeineCharacterLabelCache(Duration ttl, long maximumSize, MeterRegistry metrics, Ticker ticker) {
        if (ttl.isNegative() || ttl.isZero() || maximumSize <= 0) throw new IllegalArgumentException("Cache TTL and maximum size must be positive");
        cache = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).ticker(ticker).recordStats().build();
        CaffeineCacheMetrics.monitor(metrics, cache, "characterLabels");
        for (int i = 0; i < STRIPES; i++) { loads[i] = new ReentrantLock(); publications[i] = new Object(); }
    }
    public List<CharacterLabel> getAll(List<Long> ids, Function<List<Long>, List<CharacterLabel>> loadMissing) {
        // Never publish data read from an uncommitted transaction or mask its own writes with cached data.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return loadMissing.apply(ids);
        Map<Long, CharacterLabel> found = new HashMap<>(cache.getAllPresent(ids));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (missing.isEmpty()) return ordered(ids, found);
        int[] stripes = missing.stream().mapToInt(this::stripe).distinct().sorted().toArray();
        for (int stripe : stripes) loads[stripe].lock();
        try {
            List<Long> toLoad = new ArrayList<>();
            Map<Long, Long> observedVersions = new HashMap<>();
            for (long id : missing) {
                CharacterLabel existing = cache.asMap().get(id);
                if (existing != null) found.put(id, existing);
                else { toLoad.add(id); observedVersions.put(id, versions.get(stripe(id))); }
            }
            if (!toLoad.isEmpty()) for (CharacterLabel label : loadMissing.apply(List.copyOf(toLoad))) {
                long id = label.memberId();
                Long version = observedVersions.get(id);
                if (version == null) continue;
                found.put(id, label);
                int stripe = stripe(id);
                synchronized (publications[stripe]) {
                    // A concurrent profile commit must prevent an older in-flight load from repopulating the cache.
                    if (versions.get(stripe) == version) cache.put(id, label);
                }
            }
            // Missing members are not cached: a later signup must be visible without waiting for TTL.
            return ordered(ids, found);
        } finally {
            for (int i = stripes.length - 1; i >= 0; i--) loads[stripes[i]].unlock();
        }
    }
    public void evictAfterCommit(long memberId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { evict(memberId); return; }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Cache invalidation requires transaction synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { evict(memberId); }
        });
    }
    private void evict(long id) {
        int stripe = stripe(id);
        // Do not wait for a SQL loader here: afterCommit may still hold a pool connection.
        synchronized (publications[stripe]) { versions.incrementAndGet(stripe); cache.invalidate(id); }
    }
    private int stripe(long id) { return Long.hashCode(id) & (STRIPES - 1); }
    private List<CharacterLabel> ordered(List<Long> ids, Map<Long, CharacterLabel> found) {
        return ids.stream().map(found::get).filter(Objects::nonNull).toList();
    }
}
