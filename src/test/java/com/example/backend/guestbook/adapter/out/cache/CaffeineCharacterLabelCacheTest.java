package com.example.backend.guestbook.adapter.out.cache;

import com.example.backend.guestbook.application.result.CharacterLabel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class CaffeineCharacterLabelCacheTest {
    private final AtomicLong ticker = new AtomicLong();
    private final CaffeineCharacterLabelCache cache = new CaffeineCharacterLabelCache(
            Duration.ofMinutes(5), 100, new SimpleMeterRegistry(), ticker::get);
    private CharacterLabel label(long id, String name) { return new CharacterLabel(id, "게스트", name); }

    @Test
    void batchesOnlyMissingMembersAndExpiresEntries() {
        assertThat(cache.getAll(List.of(1L), ids -> List.of(label(1, "이름1")))).containsExactly(label(1, "이름1"));
        List<List<Long>> loaded = new ArrayList<>();
        assertThat(cache.getAll(List.of(1L, 2L, 3L), ids -> {
            loaded.add(ids); return List.of(label(2, "이름2"), label(3, "이름3"));
        })).containsExactly(label(1, "이름1"), label(2, "이름2"), label(3, "이름3"));
        assertThat(loaded).containsExactly(List.of(2L, 3L));
        cache.getAll(List.of(1L, 2L), ids -> { throw new AssertionError("Warm entries must not load"); });
        ticker.addAndGet(Duration.ofMinutes(5).toNanos());
        assertThat(cache.getAll(List.of(1L), ids -> List.of(label(1, "새 이름")))).containsExactly(label(1, "새 이름"));
    }

    @Test
    void doesNotCacheAbsentMembersOrLoaderFailures() {
        assertThat(cache.getAll(List.of(1L), ids -> List.of())).isEmpty();
        assertThatThrownBy(() -> cache.getAll(List.of(1L), ids -> { throw new IllegalStateException("load failed"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.getAll(List.of(1L), ids -> List.of(label(1, "신규 회원")))).containsExactly(label(1, "신규 회원"));
    }

    @Test
    void committedInvalidationDoesNotWaitForLoaderOrAllowStaleRepopulation() throws Exception {
        var loadedOldValue = new CountDownLatch(1);
        var finishLoad = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<List<CharacterLabel>> oldRequest = workers.submit(() -> cache.getAll(List.of(1L), ids -> {
                loadedOldValue.countDown();
                try { if (!finishLoad.await(5, TimeUnit.SECONDS)) throw new AssertionError("Load timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return List.of(label(1, "이전 이름"));
            }));
            assertThat(loadedOldValue.await(5, TimeUnit.SECONDS)).isTrue();
            // afterCommit can still own a DB connection. It must not wait for a loader that needs the pool.
            workers.submit(() -> cache.evictAfterCommit(1)).get(2, TimeUnit.SECONDS);
            finishLoad.countDown();
            assertThat(oldRequest.get(5, TimeUnit.SECONDS)).containsExactly(label(1, "이전 이름"));
            assertThat(cache.getAll(List.of(1L), ids -> List.of(label(1, "수정 이름")))).containsExactly(label(1, "수정 이름"));
            cache.getAll(List.of(1L), ids -> { throw new AssertionError("The fresh entry should be cached"); });
        } finally { finishLoad.countDown(); workers.shutdownNow(); }
    }

    @Test
    void concurrentMissesShareTheLoadedBatch() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        try {
            List<Future<List<CharacterLabel>>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(workers.submit(() -> {
                start.await();
                return cache.getAll(List.of(1L, 2L), ids -> {
                    loads.incrementAndGet(); return List.of(label(1, "이름1"), label(2, "이름2"));
                });
            }));
            start.countDown();
            for (var result : results) assertThat(result.get(5, TimeUnit.SECONDS)).hasSize(2);
            assertThat(loads).hasValue(1);
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
