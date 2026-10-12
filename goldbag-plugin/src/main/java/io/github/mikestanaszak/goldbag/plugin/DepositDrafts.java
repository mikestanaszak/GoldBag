package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread draft state and durable sidecar evidence for staged deposit chests. */
public final class DepositDrafts {
    public static final int STAGED_SLOTS = 45;

    public static final class Draft {
        private final UUID playerId;
        private final UUID sessionId;
        private final Inventory menu;
        private final ItemStack[] playerBefore;
        private final Path sidecar;
        private boolean snapshotScheduled;
        private boolean closing;
        private boolean inFlight;
        private boolean returned;
        private boolean baselineReady;
        private UUID operation;

        private Draft(UUID playerId, UUID sessionId, Inventory menu, ItemStack[] playerBefore, Path sidecar) {
            this.playerId = playerId;
            this.sessionId = sessionId;
            this.menu = menu;
            this.playerBefore = cloneSlots(playerBefore);
            this.sidecar = sidecar;
        }

        public UUID playerId() { return playerId; }
        public UUID sessionId() { return sessionId; }
        public Inventory menu() { return menu; }
        public Path sidecar() { return sidecar; }
        public List<ItemStack> playerBefore() { return immutableClones(playerBefore); }
        public boolean closing() { return closing; }
        public boolean inFlight() { return inFlight; }
        public boolean returned() { return returned; }
        public UUID operation() { return operation; }
        public boolean baselineReady() { return baselineReady; }
        private void closing(boolean value) { closing = value; }
        private void inFlight(UUID operation) { inFlight = true; this.operation = operation; }
        private void clearInFlight() { inFlight = false; operation = null; }
        private boolean beginReturn() { if (returned) return false; returned = true; return true; }
        private boolean snapshotScheduled() { return snapshotScheduled; }
        private void snapshotScheduled(boolean value) { snapshotScheduled = value; }
        private void baselineReady(boolean value) { baselineReady = value; }
    }

    private final GoldBagPlugin plugin;
    private final StorageExecutor executor;
    private final Path directory;
    private final Map<UUID, Draft> active = new ConcurrentHashMap<>();

    public DepositDrafts(GoldBagPlugin plugin, StorageExecutor executor, Path dataFolder) {
        this.plugin = plugin;
        this.executor = executor;
        this.directory = dataFolder.resolve("deposit-drafts");
    }

    public Draft open(Player player, Inventory menu, UUID sessionId) {
        if (player == null || menu == null || sessionId == null) throw new IllegalArgumentException("Draft identity is required");
        Draft previous = active.remove(player.getUniqueId());
        if (previous != null) previous.closing(true);
        Draft draft = new Draft(player.getUniqueId(), sessionId, menu, readSlots(player.getInventory()),
                directory.resolve("deposit-" + player.getUniqueId() + "-" + sessionId + ".yml"));
        active.put(player.getUniqueId(), draft);
        // Capture the opening state immediately so a crash before the first gesture still
        // leaves durable recovery evidence; subsequent edits are coalesced per tick.
        persistSnapshot(draft, player).whenComplete((ignored, error) ->
                plugin.runMainLater(() -> draft.baselineReady(error == null), 0L));
        return draft;
    }

    public Draft find(Player player, Inventory menu) {
        if (player == null || menu == null) return null;
        Draft draft = active.get(player.getUniqueId());
        return draft != null && draft.menu() == menu ? draft : null;
    }

    public Draft find(UUID playerId) { return playerId == null ? null : active.get(playerId); }

    public void markInFlight(Draft draft, UUID operation) {
        if (draft == null || operation == null) throw new IllegalArgumentException("Draft operation is required");
        draft.inFlight(operation);
    }

    public void clearInFlight(Draft draft) { if (draft != null) draft.clearInFlight(); }

    public void markClosing(Draft draft) { if (draft != null) draft.closing(true); }

    public boolean beginReturn(Draft draft) { return draft != null && draft.beginReturn(); }

    public void remove(Draft draft) {
        if (draft != null) active.remove(draft.playerId(), draft);
    }

    /** Coalesces staging changes and snapshots on the storage executor. */
    public void scheduleSnapshot(Draft draft, Player player) {
        if (draft == null || player == null || draft.snapshotScheduled()) return;
        draft.snapshotScheduled(true);
        plugin.runMainLater(() -> {
            draft.snapshotScheduled(false);
            if (active.get(draft.playerId()) != draft) return;
            persistSnapshot(draft, player);
        }, 1L);
    }

    public CompletableFuture<Void> persistSnapshot(Draft draft, Player player) {
        if (draft == null || player == null) return CompletableFuture.completedFuture(null);
        String yaml = serialize(draft, player);
        return executor.submit(() -> {
            try {
                Files.createDirectories(directory);
                Path temporary = draft.sidecar().resolveSibling(draft.sidecar().getFileName() + ".tmp");
                Files.writeString(temporary, yaml);
                try {
                    Files.move(temporary, draft.sidecar(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(temporary, draft.sidecar(), StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            } catch (IOException error) {
                throw new IllegalStateException("Could not persist deposit draft " + draft.sessionId(), error);
            }
        });
    }

    public CompletableFuture<Void> deleteSidecar(Draft draft) {
        if (draft == null) return CompletableFuture.completedFuture(null);
        return executor.submit(() -> {
            try { Files.deleteIfExists(draft.sidecar()); return null; }
            catch (IOException error) { throw new IllegalStateException("Could not delete deposit draft " + draft.sessionId(), error); }
        });
    }

    private String serialize(Draft draft, Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sessionId", draft.sessionId().toString());
        yaml.set("playerId", draft.playerId().toString());
        yaml.set("capturedAt", Instant.now().toString());
        yaml.set("operationId", draft.operation() == null ? null : draft.operation().toString());
        yaml.set("inFlight", draft.inFlight());
        yaml.set("closing", draft.closing());
        yaml.set("returned", draft.returned());
        for (int slot = 0; slot < draft.playerBefore.length; slot++) yaml.set("playerBefore." + slot, draft.playerBefore[slot]);
        for (int slot = 0; slot < InventoryAdapter.MAIN_INVENTORY_SLOTS; slot++) yaml.set("playerCurrent." + slot, player.getInventory().getItem(slot));
        for (int slot = 0; slot < draft.menu.getSize(); slot++) yaml.set("chest." + slot, draft.menu.getItem(slot));
        return yaml.saveToString();
    }

    private static ItemStack[] readSlots(PlayerInventory inventory) {
        ItemStack[] result = new ItemStack[InventoryAdapter.MAIN_INVENTORY_SLOTS];
        for (int slot = 0; slot < result.length; slot++) result[slot] = copy(inventory.getItem(slot));
        return result;
    }

    private static ItemStack[] cloneSlots(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) result[slot] = copy(source[slot]);
        return result;
    }

    private static List<ItemStack> immutableClones(ItemStack[] source) {
        List<ItemStack> result = new ArrayList<>(source.length);
        for (ItemStack item : source) result.add(copy(item));
        return Collections.unmodifiableList(result);
    }

    private static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
}
