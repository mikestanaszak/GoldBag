package io.github.mikestanaszak.goldbag.plugin;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

/** A bounded, single writer queue so SQLite calls never run on the Bukkit thread. */
public final class StorageExecutor implements AutoCloseable {
    private final ExecutorService executor;

    public StorageExecutor(int queueCapacity) {
        if (queueCapacity < 1) throw new IllegalArgumentException("Queue capacity must be positive");
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "GoldBag-storage");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try { result.complete(task.call()); }
                catch (Throwable error) { result.completeExceptionally(error); }
            });
        } catch (RuntimeException rejected) {
            result.completeExceptionally(new IllegalStateException("GoldBag storage is busy", rejected));
        }
        return result;
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    @Override public void close() { executor.shutdownNow(); }
}
