package io.github.mikestanaszak.goldbag.plugin;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.time.Duration;

/** A bounded, single writer queue so SQLite calls never run on the Bukkit thread. */
public final class StorageExecutor implements AutoCloseable {
    private final ExecutorService executor;
    private final Duration operationTimeout;

    public StorageExecutor(int queueCapacity) {
        this(queueCapacity, Duration.ofSeconds(10));
    }

    public StorageExecutor(int queueCapacity, Duration operationTimeout) {
        if (queueCapacity < 1) throw new IllegalArgumentException("Queue capacity must be positive");
        if (operationTimeout == null || operationTimeout.isNegative() || operationTimeout.isZero()) throw new IllegalArgumentException("Operation timeout must be positive");
        this.operationTimeout = operationTimeout;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "GoldBag-storage");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        if (task == null) throw new IllegalArgumentException("Storage task is required");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { result.complete(task.call()); }
                catch (Throwable error) { result.completeExceptionally(error); }
            });
        } catch (RuntimeException rejected) {
            result.completeExceptionally(new IllegalStateException("GoldBag storage is busy", rejected));
        }
        return result.orTimeout(operationTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    @Override public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException interrupted) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
