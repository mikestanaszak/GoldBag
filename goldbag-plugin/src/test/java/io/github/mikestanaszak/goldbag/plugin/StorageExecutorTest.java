package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StorageExecutorTest {
    @Test
    void stoppingRejectsQueuedWorkAndWaitsForActiveWriter() throws Exception {
        StorageExecutor executor = new StorageExecutor(4, Duration.ofSeconds(2));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> active = executor.submit(() -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        });
        CompletableFuture<Void> queued = executor.submit(() -> null);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        CompletableFuture<Boolean> stopped = CompletableFuture.supplyAsync(executor::stop);
        assertThrows(Exception.class, () -> queued.get(1, TimeUnit.SECONDS));
        release.countDown();
        assertTrue(stopped.get(2, TimeUnit.SECONDS));
        assertTrue(active.isDone());
        assertFalse(executor.isAccepting());
        assertThrows(Exception.class, () -> executor.submit(() -> null).get(1, TimeUnit.SECONDS));
    }

    @Test
    void timeoutDoesNotCancelTheUnderlyingWriter() throws Exception {
        StorageExecutor executor = new StorageExecutor(2, Duration.ofMillis(25));
        CountDownLatch finished = new CountDownLatch(1);
        CompletableFuture<String> result = executor.submit(() -> {
            Thread.sleep(100);
            finished.countDown();
            return "late";
        });
        assertThrows(Exception.class, () -> result.get(1, TimeUnit.SECONDS));
        assertTrue(finished.await(1, TimeUnit.SECONDS));
        assertTrue(executor.stop());
    }
}
