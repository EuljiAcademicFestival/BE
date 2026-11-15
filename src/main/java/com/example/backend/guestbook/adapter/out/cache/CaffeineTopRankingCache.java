package com.example.backend.guestbook.adapter.out.cache;

import com.example.backend.guestbook.application.port.out.TopRankingCachePort;
import com.example.backend.guestbook.application.result.EntryView;
import com.github.benmanes.caffeine.cache.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/** One shared snapshot, loaded once per expiry outside the caller's transaction. */
@Component
public class CaffeineTopRankingCache implements TopRankingCachePort {
    private static final String KEY = "top";
    private final Cache<String, List<EntryView>> cache;

    @Autowired
    public CaffeineTopRankingCache(@Value("${cache.top-rankings.ttl:PT1S}") Duration ttl, MeterRegistry metrics) {
        this(ttl, metrics, Ticker.systemTicker());
    }
    CaffeineTopRankingCache(Duration ttl, MeterRegistry metrics, Ticker ticker) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Ranking cache TTL must be positive");
        cache = Caffeine.newBuilder().maximumSize(1).expireAfterWrite(ttl).ticker(ticker).recordStats().build();
        CaffeineCacheMetrics.monitor(metrics, cache, "topRankings");
    }
    public List<EntryView> get(Supplier<List<EntryView>> load) {
        // A caller holding a DB connection must never wait for a loader needing the same pool.
        // Also prevent publishing uncommitted data or hiding the transaction's own writes.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return load.get();
        return cache.get(KEY, ignored -> List.copyOf(load.get()));
    }
    /** Explicit reset for administrative/fixture changes; normal likes rely on the short TTL. */
    public void invalidate() { cache.invalidateAll(); }
}
