package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Durable operation boundary. Bukkit inventory work stays outside the storage executor. */
public final class ExchangeCoordinator {
    public enum Stage { PREPARED, APPLYING, COMPLETED, CANCELLED }

    public static final class StateMachine {
        private final Map<UUID, Stage> stages = new java.util.HashMap<>();
        public synchronized Stage prepare(UUID operation) {
            if (operation == null) throw new IllegalArgumentException("Operation is required");
            Stage current = stages.get(operation);
            if (current != null) return current;
            stages.put(operation, Stage.PREPARED);
            return Stage.PREPARED;
        }
        public synchronized Stage markApplying(UUID operation) {
            require(operation, Stage.PREPARED);
            stages.put(operation, Stage.APPLYING);
            return Stage.APPLYING;
        }
        public synchronized Stage complete(UUID operation) {
            Stage current = stages.get(operation);
            if (current == Stage.COMPLETED) return current;
            require(operation, Stage.APPLYING);
            stages.put(operation, Stage.COMPLETED);
            return Stage.COMPLETED;
        }
        public synchronized Stage cancel(UUID operation) {
            require(operation, Stage.PREPARED);
            stages.put(operation, Stage.CANCELLED);
            return Stage.CANCELLED;
        }
        public synchronized Stage stage(UUID operation) { return stages.get(operation); }
        private void require(UUID operation, Stage expected) {
            Stage current = stages.get(operation);
            if (current != expected) throw new IllegalStateException("Operation must be " + expected + "; was " + current);
        }
    }

    private final SqliteStore store;
    private final StorageExecutor executor;

    public ExchangeCoordinator(SqliteStore store, StorageExecutor executor) {
        this.store = store;
        this.executor = executor;
    }

    public java.util.concurrent.CompletableFuture<SqliteStore.Pending> prepare(UUID operation, UUID player,
                                                                                  SqliteStore.Kind kind, long amount,
                                                                                  String payload, UUID noteId) {
        return executor.submit(() -> store.prepare(operation, player, kind, amount, payload, noteId));
    }

    public java.util.concurrent.CompletableFuture<SqliteStore.Receipt> complete(UUID operation) {
        return executor.submit(() -> store.complete(operation));
    }

    public java.util.concurrent.CompletableFuture<Void> markApplying(UUID operation) {
        return executor.submit(() -> { store.markApplying(operation); return null; });
    }

    public java.util.concurrent.CompletableFuture<Void> cancel(UUID operation, String reason) {
        return executor.submit(() -> { store.cancelPrepared(operation, reason); return null; });
    }
}
