package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeCoordinatorExecutionTest {
    @Test
    void changedInventoryCancelsBeforeApplying() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            FakeInventory inventory = new FakeInventory(false);
            assertThrows(Exception.class, () -> coordinator.execute(UUID.randomUUID(), player, SqliteStore.Kind.DEPOSIT,
                    100, "COAL:5", null, Runnable::run, inventory).get(3, TimeUnit.SECONDS));
            assertTrue(store.pending().isEmpty());
            assertFalse(inventory.applied);
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void physicalFailureLeavesApplyingOperationForRecovery() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            FakeInventory inventory = new FakeInventory(true);
            inventory.throwOnApply = true;
            assertThrows(Exception.class, () -> coordinator.execute(UUID.randomUUID(), player, SqliteStore.Kind.DEPOSIT,
                    100, "COAL:5", null, Runnable::run, inventory).get(3, TimeUnit.SECONDS));
            assertEquals(1, store.pending().size());
            assertEquals("APPLYING", store.pending().get(0).state());
            assertTrue(store.isBlocked(player));
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void schedulerFailureCancelsPreparedOperation() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            assertThrows(Exception.class, () -> coordinator.execute(UUID.randomUUID(), player, SqliteStore.Kind.DEPOSIT,
                    100, "COAL:5", null, action -> { throw new IllegalStateException("scheduler failed"); }, new FakeInventory(true)).get(3, TimeUnit.SECONDS));
            assertTrue(store.pending().isEmpty());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void readyFailureBeforeApplyingCancelsPreparedOperation() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            ExchangeCoordinator.InventoryPort inventory = new ExchangeCoordinator.InventoryPort() {
                @Override public boolean ready() { throw new IllegalStateException("inventory read failed"); }
                @Override public void apply() { fail("must not apply"); }
            };
            assertThrows(Exception.class, () -> coordinator.execute(UUID.randomUUID(), player, SqliteStore.Kind.DEPOSIT,
                    100, "COAL:5", null, Runnable::run, inventory).get(3, TimeUnit.SECONDS));
            assertTrue(store.pending().isEmpty());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void inventoryChangeBetweenJournalAndApplyLeavesApplyingOperation() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            ControlledMainThread main = new ControlledMainThread();
            AtomicInteger reads = new AtomicInteger();
            ExchangeCoordinator.InventoryPort inventory = new ExchangeCoordinator.InventoryPort() {
                @Override public boolean ready() { return reads.getAndIncrement() == 0; }
                @Override public void apply() { fail("must not apply"); }
            };
            java.util.concurrent.CompletableFuture<SqliteStore.Receipt> result = coordinator.execute(UUID.randomUUID(), player,
                    SqliteStore.Kind.DEPOSIT, 100, "COAL:5", null, main, inventory);
            assertTrue(main.awaitTask(1, TimeUnit.SECONDS));
            main.runNext();
            assertTrue(main.awaitTask(1, TimeUnit.SECONDS));
            main.runNext();
            assertThrows(Exception.class, () -> result.get(3, TimeUnit.SECONDS));
            assertEquals("APPLYING", store.pending().get(0).state());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void schedulerFailureAfterApplyingLeavesOperationForRecovery() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            AtomicInteger calls = new AtomicInteger();
            ExchangeCoordinator.MainThread scheduler = action -> {
                if (calls.getAndIncrement() == 0) action.run();
                else throw new IllegalStateException("late scheduler failure");
            };
            assertThrows(Exception.class, () -> coordinator.execute(UUID.randomUUID(), player, SqliteStore.Kind.DEPOSIT,
                    100, "COAL:5", null, scheduler, new FakeInventory(true)).get(3, TimeUnit.SECONDS));
            assertEquals("APPLYING", store.pending().get(0).state());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void timeoutBeforePrepareQuarantinesLateDatabaseCommitWithoutApplyingInventory() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        CountDownLatch release = new CountDownLatch(1);
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8, java.time.Duration.ofMillis(40))) {
            UUID player = UUID.randomUUID(); store.ensureAccount(player, "Alice");
            executor.submit(() -> { release.await(2, TimeUnit.SECONDS); return null; });
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            FakeInventory inventory = new FakeInventory(true);
            java.util.concurrent.CompletableFuture<SqliteStore.Receipt> result = coordinator.execute(UUID.randomUUID(), player,
                    SqliteStore.Kind.DEPOSIT, 100, "COAL:5", null, Runnable::run, inventory);
            assertThrows(Exception.class, () -> result.get(1, TimeUnit.SECONDS));
            assertFalse(inventory.applied);
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (store.pending().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, store.pending().size());
            assertEquals("PREPARED", store.pending().get(0).state());
        } finally { release.countDown(); Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    @Test
    void duplicateBanknoteRedemptionIsRejectedByDurableStore() throws Exception {
        java.nio.file.Path file = Files.createTempFile("goldbag-coordinator", ".db");
        try (SqliteStore store = new SqliteStore(file, 1_000_000); StorageExecutor executor = new StorageExecutor(8)) {
            UUID issuer = UUID.randomUUID(), redeemer = UUID.randomUUID(), note = UUID.randomUUID();
            store.ensureAccount(issuer, "Issuer"); store.ensureAccount(redeemer, "Redeemer");
            store.adjust(UUID.randomUUID(), null, issuer, 1_000, "seed");
            UUID issue = UUID.randomUUID(); store.prepare(issue, issuer, SqliteStore.Kind.NOTE_ISSUE, 250, "paper", note);
            store.markApplying(issue); store.complete(issue);
            ExchangeCoordinator coordinator = new ExchangeCoordinator(store, executor);
            FakeInventory inventory = new FakeInventory(true);
            UUID first = UUID.randomUUID();
            assertNotNull(coordinator.executeRedemption(first, redeemer, note, "held", Runnable::run, inventory).get(3, TimeUnit.SECONDS));
            assertThrows(Exception.class, () -> coordinator.executeRedemption(UUID.randomUUID(), redeemer, note, "held", Runnable::run, new FakeInventory(true)).get(3, TimeUnit.SECONDS));
            assertEquals("REDEEMED", store.note(note).orElseThrow().status());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock")); }
    }

    private static final class ControlledMainThread implements ExchangeCoordinator.MainThread {
        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        @Override public void execute(Runnable action) { tasks.add(action); }
        private boolean awaitTask(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (tasks.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
            return !tasks.isEmpty();
        }
        private void runNext() { Runnable action = tasks.poll(); assertNotNull(action); action.run(); }
    }

    private static final class FakeInventory implements ExchangeCoordinator.InventoryPort {
        private final boolean ready;
        private boolean applied;
        private boolean throwOnApply;
        private FakeInventory(boolean ready) { this.ready = ready; }
        @Override public boolean ready() { return ready; }
        @Override public void apply() { if (throwOnApply) throw new IllegalStateException("physical failure"); applied = true; }
    }
}
