package io.github.mikestanaszak.goldbag.plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A bounded, single writer queue so SQLite calls never run on the Bukkit thread. */
public final class StorageExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Duration operationTimeout;
    private final Object lifecycle = new Object();
    private final Set<CompletableFuture<?>> outstanding =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean accepting = true;

    public StorageExecutor(int queueCapacity) {
        this(queueCapacity, Duration.ofSeconds(10));
    }

    public StorageExecutor(int queueCapacity, Duration operationTimeout) {
        if (queueCapacity < 1) throw new IllegalArgumentException("Queue capacity must be positive");
        if (operationTimeout == null || operationTimeout.isNegative() || operationTimeout.isZero()) {
            throw new IllegalArgumentException("Operation timeout must be positive");
        }
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
        synchronized (lifecycle) {
            if (!accepting) {
                result.completeExceptionally(new IllegalStateException("GoldBag storage is stopping"));
                return result;
            }
            outstanding.add(result);
            try {
                executor.execute(new Work<>(task, result));
            } catch (RuntimeException rejected) {
                outstanding.remove(result);
                result.completeExceptionally(new IllegalStateException("GoldBag storage is busy", rejected));
            }
        }
        return result.orTimeout(operationTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    public boolean isAccepting() { synchronized (lifecycle) { return accepting; } }

    /**
     * Stops accepting work, rejects every queued task, and waits for the active writer to
     * finish. A false result means an active task outlived the bounded wait; its database
     * owner must remain open because closing it would race that task.
     */
    public boolean stop() {
        synchronized (lifecycle) {
            if (!accepting && executor.isTerminated()) return true;
            accepting = false;
            executor.shutdown();
            rejectQueued(new IllegalStateException("GoldBag storage is stopping"));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean terminated = awaitUntil(deadline);
        if (!terminated) {
            executor.shutdownNow();
            terminated = awaitUntil(deadline);
        }
        if (!terminated) {
            rejectOutstanding(new IllegalStateException("GoldBag storage writer did not stop; operation is unresolved"));
        }
        return terminated;
    }

    @Override public void close() { stop(); }

    private boolean awaitUntil(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return executor.isTerminated();
        try {
            return executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void rejectQueued(Throwable reason) {
        List<Runnable> queued = new ArrayList<>();
        executor.getQueue().drainTo(queued);
        for (Runnable runnable : queued) {
            if (runnable instanceof Work<?>) ((Work<?>) runnable).reject(reason);
        }
    }

    private void rejectOutstanding(Throwable reason) {
        for (CompletableFuture<?> future : outstanding) future.completeExceptionally(reason);
    }

    private final class Work<T> implements Runnable {
        private final Callable<T> task;
        private final CompletableFuture<T> result;

        private Work(Callable<T> task, CompletableFuture<T> result) {
            this.task = task;
            this.result = result;
        }

        @Override public void run() {
            try {
                result.complete(task.call());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            } finally {
                outstanding.remove(result);
            }
        }

        private void reject(Throwable reason) {
            outstanding.remove(result);
            result.completeExceptionally(reason);
        }
    }
}
