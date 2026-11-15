package com.example.backend.guestbook.adapter.out.cache;

import com.example.backend.guestbook.application.result.EntryView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class CaffeineTopRankingCacheTest {
    final AtomicLong ticker = new AtomicLong();
    final CaffeineTopRankingCache cache = new CaffeineTopRankingCache(Duration.ofSeconds(1), new SimpleMeterRegistry(), ticker::get);
    EntryView entry(long id, long likes, int rank) {
        return new EntryView(id,1,"게스트","회원"+id,rank,"방명록",Instant.EPOCH,likes,false);
    }
    @Test void expiresOneSecondAfterWriteRatherThanLastAccess() {
        var old = List.of(entry(1,1,1)); var fresh = List.of(entry(2,2,1));
        assertThat(cache.get(() -> old)).isEqualTo(old);
        ticker.addAndGet(800_000_000);
        assertThat(cache.get(() -> { throw new AssertionError("Hit must not load"); })).isEqualTo(old);
        ticker.addAndGet(200_000_000);
        assertThat(cache.get(() -> fresh)).isEqualTo(fresh);
    }
    @Test void preservesAllTiesAndReturnsAnImmutableSnapshot() {
        var source = new ArrayList<EntryView>();
        for (int i=1;i<=5;i++) source.add(entry(i,10,1));
        var result = cache.get(() -> source);
        source.clear();
        assertThat(result).hasSize(5).extracting(EntryView::rank).containsOnly(1);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(cache.get(List::of)).isEqualTo(result);
    }
    @Test void cachesEmptyRankingsButDoesNotCacheLoaderFailure() {
        assertThatThrownBy(() -> cache.get(() -> { throw new IllegalStateException("load failed"); })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.get(List::of)).isEmpty();
        assertThat(cache.get(() -> { throw new AssertionError("Empty list is a valid hit"); })).isEmpty();
    }
    @Test void concurrentMissesShareOneLoadAndExpiryReload() throws Exception {
        var workers = Executors.newFixedThreadPool(8);
        var loads = new AtomicInteger();
        try {
            for (int round=0;round<2;round++) {
                var start = new CountDownLatch(1); var loaderEntered = new CountDownLatch(1); var release = new CountDownLatch(1);
                List<Future<List<EntryView>>> futures = new ArrayList<>();
                try {
                    for (int i=0;i<8;i++) futures.add(workers.submit(() -> {
                        start.await();
                        return cache.get(() -> {
                            loads.incrementAndGet();loaderEntered.countDown();
                            try { if (!release.await(3,TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
                            catch (InterruptedException ex) { Thread.currentThread().interrupt();throw new RuntimeException(ex); }
                            return List.of(entry(1,10,1));
                        });
                    }));
                    start.countDown(); assertThat(loaderEntered.await(3,TimeUnit.SECONDS)).isTrue();
                    release.countDown();
                    for (var f:futures) assertThat(f.get(3,TimeUnit.SECONDS)).containsExactly(entry(1,10,1));
                    assertThat(loads).hasValue(round+1);
                } finally { release.countDown(); }
                ticker.addAndGet(Duration.ofSeconds(1).toNanos());
            }
        } finally { workers.shutdownNow(); }
    }
    @Test void outerTransactionBypassesEvenWarmCacheAndCannotPublishItsOwnWrites() {
        var old = List.of(entry(1,1,1)); var uncommitted = List.of(entry(2,20,1));
        cache.get(() -> old);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThat(cache.get(() -> uncommitted)).isEqualTo(uncommitted); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(cache.get(() -> { throw new AssertionError("Uncommitted result must not replace cache"); })).isEqualTo(old);
        cache.invalidate();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { cache.get(() -> uncommitted); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(cache.get(() -> old)).isEqualTo(old);
    }
    @Test void rejectsNonPositiveTtl() {
        for (var ttl:List.of(Duration.ZERO,Duration.ofSeconds(-1)))
            assertThatThrownBy(() -> new CaffeineTopRankingCache(ttl,new SimpleMeterRegistry(),ticker::get)).isInstanceOf(IllegalArgumentException.class);
    }
}
