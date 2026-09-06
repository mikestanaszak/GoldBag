package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    private static final class FakeInventory implements ExchangeCoordinator.InventoryPort {
        private final boolean ready;
        private boolean applied;
        private boolean throwOnApply;
        private FakeInventory(boolean ready) { this.ready = ready; }
        @Override public boolean ready() { return ready; }
        @Override public void apply() { if (throwOnApply) throw new IllegalStateException("physical failure"); applied = true; }
    }
}
