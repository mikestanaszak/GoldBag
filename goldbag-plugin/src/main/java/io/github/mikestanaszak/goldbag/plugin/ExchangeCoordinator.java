package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable operation boundary. Bukkit inventory work stays outside the storage executor. */
public final class ExchangeCoordinator {
    public enum Stage { PREPARED, APPLYING, COMPLETED, CANCELLED }
    public interface MainThread { void execute(Runnable action); }
    public interface InventoryPort { boolean ready(); void apply(); }

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

    /**
     * Executes a physical exchange while making the journal boundary explicit. The supplied
     * scheduler is the Bukkit main-thread bridge; inventory callbacks are never run on the
     * storage executor. A physical exception leaves APPLYING pending for operator recovery.
     */
    public CompletableFuture<SqliteStore.Receipt> execute(UUID operation, UUID player, SqliteStore.Kind kind,
                                                            long amount, String payload, UUID noteId,
                                                            MainThread mainThread, InventoryPort inventory) {
        return executePrepared(prepare(operation, player, kind, amount, payload, noteId), operation, player, mainThread, inventory);
    }

    public CompletableFuture<SqliteStore.Receipt> executeRedemption(UUID operation, UUID player, UUID noteId,
                                                                      String payload, MainThread mainThread,
                                                                      InventoryPort inventory) {
        return executePrepared(executor.submit(() -> store.prepareRedemption(operation, player, noteId, payload)),
                operation, player, mainThread, inventory);
    }

    private CompletableFuture<SqliteStore.Receipt> executePrepared(CompletableFuture<SqliteStore.Pending> prepared,
                                                                    UUID operation, UUID player,
                                                                    MainThread mainThread, InventoryPort inventory) {
        Objects.requireNonNull(mainThread, "Main-thread scheduler is required");
        Objects.requireNonNull(inventory, "Inventory port is required");
        CompletableFuture<SqliteStore.Receipt> outcome = new CompletableFuture<>();
        prepared.whenComplete((pending, prepareError) -> {
            if (prepareError != null) { outcome.completeExceptionally(prepareError); return; }
            try {
                mainThread.execute(() -> beforeApplying(operation, outcome, inventory, mainThread));
            } catch (Throwable schedulerError) {
                cancelAndFail(operation, schedulerError, outcome);
            }
        });
        return outcome;
    }

    private void beforeApplying(UUID operation, CompletableFuture<SqliteStore.Receipt> outcome,
                                InventoryPort inventory, MainThread mainThread) {
        try {
            if (!inventory.ready()) {
                cancelAndFail(operation, new IllegalStateException("Inventory changed before APPLYING"), outcome);
                return;
            }
        } catch (Throwable readyError) {
            cancelAndFail(operation, readyError, outcome);
            return;
        }
        markApplying(operation).whenComplete((ignored, applyingError) -> {
            if (applyingError != null) { outcome.completeExceptionally(applyingError); return; }
            schedule(mainThread, () -> applyAfterJournal(operation, outcome, inventory), outcome);
        });
    }

    private void applyAfterJournal(UUID operation, CompletableFuture<SqliteStore.Receipt> outcome,
                                   InventoryPort inventory) {
        try {
            if (!inventory.ready()) {
                outcome.completeExceptionally(new IllegalStateException("Inventory changed after APPLYING"));
                return;
            }
            inventory.apply();
        } catch (Throwable physicalError) {
            outcome.completeExceptionally(physicalError);
            return;
        }
        complete(operation).whenComplete((receipt, completeError) -> {
            if (completeError != null) outcome.completeExceptionally(completeError);
            else outcome.complete(receipt);
        });
    }

    private void cancelAndFail(UUID operation, Throwable failure, CompletableFuture<SqliteStore.Receipt> outcome) {
        try {
            cancel(operation, "inventory or player state changed before APPLYING").whenComplete((ignored, cancelError) ->
                    outcome.completeExceptionally(cancelError == null ? failure : cancelError));
        } catch (Throwable cancelError) {
            outcome.completeExceptionally(cancelError);
        }
    }

    private void schedule(MainThread mainThread, Runnable action, CompletableFuture<SqliteStore.Receipt> outcome) {
        try {
            mainThread.execute(action);
        } catch (Throwable schedulerError) {
            outcome.completeExceptionally(schedulerError);
        }
    }
}
