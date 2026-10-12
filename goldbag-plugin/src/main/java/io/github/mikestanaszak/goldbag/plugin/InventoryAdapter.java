package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Resource;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** All methods are intended for the Bukkit main thread. */
public final class InventoryAdapter {
    public static final int MAIN_INVENTORY_SLOTS = 36;

    /** Immutable before/after plan for a physical main-inventory change. */
    public static final class Plan {
        private final ItemStack[] before;
        private final ItemStack[] after;
        private final int selectedSlot;
        private final ItemStack selectedBefore;
        private final ItemStack selectedAfter;
        private final ItemStack cursorBefore;
        private final ItemStack cursorAfter;
        private final List<Integer> affectedSlots;
        private final String evidence;

        private Plan(ItemStack[] before, ItemStack[] after) {
            this(before, after, -1, null, null, null, null);
        }

        private Plan(ItemStack[] before, ItemStack[] after, int selectedSlot,
                     ItemStack selectedBefore, ItemStack selectedAfter) {
            this(before, after, selectedSlot, selectedBefore, selectedAfter, null, null);
        }

        private Plan(ItemStack[] before, ItemStack[] after, int selectedSlot,
                     ItemStack selectedBefore, ItemStack selectedAfter,
                     ItemStack cursorBefore, ItemStack cursorAfter) {
            this.before = cloneSlots(before);
            this.after = cloneSlots(after);
            this.selectedSlot = selectedSlot;
            this.selectedBefore = copy(selectedBefore);
            this.selectedAfter = copy(selectedAfter);
            this.cursorBefore = copy(cursorBefore);
            this.cursorAfter = copy(cursorAfter);
            List<Integer> changed = new ArrayList<>();
            for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
                if (!same(this.before[slot], this.after[slot])) changed.add(slot);
            }
            if (selectedSlot >= MAIN_INVENTORY_SLOTS
                    && !same(this.selectedBefore, this.selectedAfter)) changed.add(selectedSlot);
            this.affectedSlots = List.copyOf(changed);
            this.evidence = "before=" + snapshot(this.before, selectedSlot, this.selectedBefore)
                    + cursorEvidence("cursorBefore", this.cursorBefore)
                    + ";after=" + snapshot(this.after, selectedSlot, this.selectedAfter)
                    + cursorEvidence("cursorAfter", this.cursorAfter);
        }

        public List<ItemStack> before() { return immutableClones(before); }
        public List<ItemStack> after() { return immutableClones(after); }
        public List<Integer> affectedSlots() { return affectedSlots; }
        public String evidence() { return evidence; }
        public String beforeEvidence() { return "before=" + snapshot(before, selectedSlot, selectedBefore) + cursorEvidence("cursorBefore", cursorBefore); }
        public String afterEvidence() { return "after=" + snapshot(after, selectedSlot, selectedAfter) + cursorEvidence("cursorAfter", cursorAfter); }

        /** Checks all 36 slots so a changed unrelated slot also aborts safely. */
        public boolean ready(PlayerInventory inventory) {
            if (inventory == null) return false;
            if (cursorBefore != null || cursorAfter != null) return false;
            return readySlots(inventory);
        }

        private boolean readySlots(PlayerInventory inventory) {
            try {
                if (selectedSlot >= 0 && selectedSlot < 9
                        && inventory.getHeldItemSlot() != selectedSlot) return false;
                for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
                    if (!same(before[slot], normalize(inventory.getItem(slot)))) return false;
                }
                if (selectedSlot >= MAIN_INVENTORY_SLOTS
                        && !same(selectedBefore, normalize(inventory.getItem(selectedSlot)))) return false;
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        /** Revalidates inventory and the player's cursor for a cursor plan. */
        public boolean ready(Player player) {
            if (player == null || !readySlots(player.getInventory())) return false;
            return cursorBefore == null && cursorAfter == null || same(cursorBefore, normalize(player.getItemOnCursor()));
        }

        /** Revalidates before state, then writes only affected slots. */
        public void apply(PlayerInventory inventory) {
            if (cursorBefore != null || cursorAfter != null) {
                throw new IllegalStateException("This physical plan requires a Player cursor");
            }
            if (!ready(inventory)) throw new IllegalStateException("Inventory changed; physical plan is no longer ready");
            applySlots(inventory);
        }

        /** Revalidates before state, then writes inventory and cursor changes. */
        public void apply(Player player) {
            if (player == null || !ready(player)) throw new IllegalStateException("Inventory or cursor changed; physical plan is no longer ready");
            applySlots(player.getInventory());
            if (cursorBefore != null || cursorAfter != null) player.setItemOnCursor(copy(cursorAfter));
        }

        private void applySlots(PlayerInventory inventory) {
            for (int slot : affectedSlots) {
                if (slot == selectedSlot && slot >= MAIN_INVENTORY_SLOTS) {
                    inventory.setItem(slot, copy(selectedAfter));
                } else {
                    inventory.setItem(slot, copy(after[slot]));
                }
            }
        }

        private static List<ItemStack> immutableClones(ItemStack[] slots) {
            List<ItemStack> result = new ArrayList<>(MAIN_INVENTORY_SLOTS);
            for (ItemStack slot : slots) result.add(copy(slot));
            return Collections.unmodifiableList(result);
        }
    }

    /** Immutable physical plan for consuming eligible stacks from a staged menu chest. */
    public static final class StagedPlan {
        private final ItemStack[] chestBefore;
        private final ItemStack[] chestAfter;
        private final ItemStack[] playerBefore;
        private final ItemStack[] playerAfter;
        private final List<Integer> affectedChestSlots;
        private final String evidence;

        private StagedPlan(ItemStack[] chestBefore, ItemStack[] chestAfter, ItemStack[] playerBefore) {
            this.chestBefore = cloneArray(chestBefore);
            this.chestAfter = cloneArray(chestAfter);
            this.playerBefore = cloneSlots(playerBefore);
            this.playerAfter = cloneSlots(playerBefore);
            List<Integer> changed = new ArrayList<>();
            for (int slot = 0; slot < this.chestBefore.length; slot++) {
                if (!same(this.chestBefore[slot], this.chestAfter[slot])) changed.add(slot);
            }
            this.affectedChestSlots = List.copyOf(changed);
            this.evidence = "chestBefore=" + snapshotArray(this.chestBefore)
                    + ";chestAfter=" + snapshotArray(this.chestAfter)
                    + ";playerBefore=" + snapshot(this.playerBefore)
                    + ";playerAfter=" + snapshot(this.playerAfter);
        }

        public List<Integer> affectedChestSlots() { return affectedChestSlots; }
        public String evidence() { return evidence; }
        public List<ItemStack> chestBefore() { return immutableClones(chestBefore); }
        public List<ItemStack> chestAfter() { return immutableClones(chestAfter); }
        public List<ItemStack> playerBefore() { return immutableClones(playerBefore); }
        public List<ItemStack> playerAfter() { return immutableClones(playerAfter); }

        public boolean ready(Inventory chest, PlayerInventory player) {
            if (chest == null || player == null || chest.getSize() < 54) return false;
            try {
                for (int slot = 0; slot < chestBefore.length; slot++) {
                    if (!same(chestBefore[slot], normalize(chest.getItem(slot)))) return false;
                }
                for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
                    if (!same(playerBefore[slot], normalize(player.getItem(slot)))) return false;
                }
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        public void apply(Inventory chest, PlayerInventory player) {
            if (!ready(chest, player)) throw new IllegalStateException("Staged inventory changed; physical plan is no longer ready");
            for (int slot : affectedChestSlots) chest.setItem(slot, copy(chestAfter[slot]));
        }

        private static List<ItemStack> immutableClones(ItemStack[] slots) {
            List<ItemStack> result = new ArrayList<>(slots.length);
            for (ItemStack slot : slots) result.add(copy(slot));
            return Collections.unmodifiableList(result);
        }
    }

    public int count(PlayerInventory inventory, Material material) {
        if (inventory == null || material == null) return 0;
        int total = 0;
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isPlain(item, material)) total += item.getAmount();
        }
        return total;
    }

    public boolean canRemove(PlayerInventory inventory, Material material, int count) {
        if (count <= 0) return false;
        try {
            planRemoval(inventory, material, count);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return false;
        }
    }

    public void remove(PlayerInventory inventory, Material material, int count) {
        planRemoval(inventory, material, count).apply(inventory);
    }

    /** Captures exact deterministic removal in main-inventory slot order. */
    public Plan planRemoval(PlayerInventory inventory, Material material, int count) {
        if (material == null) throw new IllegalArgumentException("Material is required");
        if (count <= 0) throw new IllegalArgumentException("Count must be positive");
        ItemStack[] before = readSlots(inventory);
        ItemStack[] after = cloneSlots(before);
        int remaining = count;
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS && remaining > 0; slot++) {
            ItemStack item = after[slot];
            if (!isPlain(item, material)) continue;
            int used = Math.min(remaining, item.getAmount());
            int next = item.getAmount() - used;
            after[slot] = next == 0 ? null : withAmount(item, next);
            remaining -= used;
        }
        if (remaining != 0) throw new IllegalStateException("Inventory does not contain the required plain items");
        return new Plan(before, after);
    }

    /** Captures exact multi-resource removal in map iteration order. */
    public Plan planRemoval(PlayerInventory inventory, Map<Material, Integer> removals) {
        if (removals == null || removals.isEmpty()) throw new IllegalArgumentException("Removals are required");
        ItemStack[] before = readSlots(inventory);
        ItemStack[] after = cloneSlots(before);
        for (Map.Entry<Material, Integer> entry : removals.entrySet()) {
            Material material = entry.getKey();
            Integer requested = entry.getValue();
            if (material == null || requested == null || requested <= 0) {
                throw new IllegalArgumentException("Removal counts must be positive");
            }
            int remaining = requested;
            for (int slot = 0; slot < MAIN_INVENTORY_SLOTS && remaining > 0; slot++) {
                ItemStack item = after[slot];
                if (!isPlain(item, material)) continue;
                int used = Math.min(remaining, item.getAmount());
                int next = item.getAmount() - used;
                after[slot] = next == 0 ? null : withAmount(item, next);
                remaining -= used;
            }
            if (remaining != 0) throw new IllegalStateException("Inventory does not contain the required plain items");
        }
        return new Plan(before, after);
    }

    /** Captures removal of exactly one item from the selected main-hand slot. */
    public Plan planHeldRemoval(PlayerInventory inventory) {
        return planSlotRemoval(inventory, inventory == null ? -1 : inventory.getHeldItemSlot());
    }

    /** Captures removal of exactly one item from a selected hotbar or off-hand slot. */
    public Plan planSlotRemoval(PlayerInventory inventory, int slot) {
        return planSlotRemoval(inventory, slot, 1);
    }

    /** Captures exact removal from one hotbar or off-hand slot, preserving any remainder. */
    public Plan planSlotRemoval(PlayerInventory inventory, int slot, int count) {
        if (slot < 0 || slot > 8 && slot != 40) {
            throw new IllegalArgumentException("Selected slot must be a hotbar slot (0-8) or off-hand slot 40");
        }
        if (count <= 0) throw new IllegalArgumentException("Count must be positive");
        ItemStack[] before = readSlots(inventory);
        ItemStack selected = slot == 40 ? normalize(inventory.getItem(40)) : before[slot];
        if (selected == null) throw new IllegalStateException("Selected slot is empty");
        if (selected.getAmount() < count) throw new IllegalStateException("Selected slot does not contain the required items");
        ItemStack selectedAfter = selected.getAmount() == count ? null : withAmount(selected, selected.getAmount() - count);
        ItemStack[] after = cloneSlots(before);
        if (slot < MAIN_INVENTORY_SLOTS) after[slot] = selectedAfter;
        return new Plan(before, after, slot, selected, selectedAfter);
    }

    /** Captures exact removal from any of the player's 36 ordinary inventory slots. */
    public Plan planMainSlotRemoval(PlayerInventory inventory, int slot, int count) {
        if (slot < 0 || slot >= MAIN_INVENTORY_SLOTS) {
            throw new IllegalArgumentException("Main inventory slot must be between 0 and 35");
        }
        if (count <= 0) throw new IllegalArgumentException("Count must be positive");
        ItemStack[] before = readSlots(inventory);
        ItemStack selected = before[slot];
        if (selected == null) throw new IllegalStateException("Selected slot is empty");
        if (selected.getAmount() < count) throw new IllegalStateException("Selected slot does not contain the required items");
        ItemStack selectedAfter = selected.getAmount() == count ? null : withAmount(selected, selected.getAmount() - count);
        ItemStack[] after = cloneSlots(before);
        after[slot] = selectedAfter;
        return new Plan(before, after);
    }

    /** Captures exact removal from the player's cursor, including cursor evidence. */
    public Plan planCursorRemoval(Player player) {
        if (player == null) throw new IllegalArgumentException("Player is required");
        ItemStack cursor = normalize(player.getItemOnCursor());
        if (cursor == null) throw new IllegalStateException("Cursor is empty");
        return planCursorRemoval(player, cursor.getAmount());
    }

    /** Captures exact removal from the player's cursor, preserving any remainder. */
    public Plan planCursorRemoval(Player player, int count) {
        if (player == null) throw new IllegalArgumentException("Player is required");
        if (count <= 0) throw new IllegalArgumentException("Count must be positive");
        ItemStack cursor = normalize(player.getItemOnCursor());
        if (cursor == null) throw new IllegalStateException("Cursor is empty");
        if (cursor.getAmount() < count) throw new IllegalStateException("Cursor does not contain the required items");
        ItemStack afterCursor = cursor.getAmount() == count ? null : withAmount(cursor, cursor.getAmount() - count);
        ItemStack[] before = readSlots(player.getInventory());
        return new Plan(before, before, -1, null, null, cursor, afterCursor);
    }

    /** Captures exact eligible removal from the first 45 slots of a staged chest. */
    public StagedPlan planStagedRemoval(Inventory chest, PlayerInventory player,
                                        Map<Material, Integer> removals) {
        if (chest == null || chest.getSize() < 54) throw new IllegalArgumentException("A 54-slot staging chest is required");
        if (player == null || removals == null || removals.isEmpty()) throw new IllegalArgumentException("Staged removals are required");
        ItemStack[] chestBefore = readStagedSlots(chest);
        ItemStack[] chestAfter = cloneArray(chestBefore);
        for (Map.Entry<Material, Integer> entry : removals.entrySet()) {
            Material material = entry.getKey();
            Integer requested = entry.getValue();
            if (material == null || requested == null || requested <= 0) throw new IllegalArgumentException("Removal counts must be positive");
            int remaining = requested;
            for (int slot = 0; slot < 45 && remaining > 0; slot++) {
                ItemStack item = chestAfter[slot];
                if (!isPlain(item, material)) continue;
                int used = Math.min(remaining, item.getAmount());
                int next = item.getAmount() - used;
                chestAfter[slot] = next == 0 ? null : withAmount(item, next);
                remaining -= used;
            }
            if (remaining != 0) throw new IllegalStateException("Staged chest does not contain the required plain items");
        }
        return new StagedPlan(chestBefore, chestAfter, readSlots(player));
    }

    public boolean canFit(PlayerInventory inventory, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return false;
        try {
            planAddition(inventory, stack);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return false;
        }
    }

    public void add(PlayerInventory inventory, ItemStack stack) {
        planAddition(inventory, stack).apply(inventory);
    }

    /** Captures exact merge/empty-slot choices for adding a cloned stack. */
    public Plan planAddition(PlayerInventory inventory, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            throw new IllegalArgumentException("A non-empty stack is required");
        }
        ItemStack[] before = readSlots(inventory);
        ItemStack[] after = cloneSlots(before);
        int remaining = stack.getAmount();
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS && remaining > 0; slot++) {
            ItemStack existing = after[slot];
            if (existing == null || !sameTypeAndMeta(existing, stack)) continue;
            int room = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            int used = Math.min(remaining, room);
            if (used > 0) {
                after[slot] = withAmount(existing, existing.getAmount() + used);
                remaining -= used;
            }
        }
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS && remaining > 0; slot++) {
            if (after[slot] != null) continue;
            int used = Math.min(remaining, stack.getMaxStackSize());
            after[slot] = withAmount(stack, used);
            remaining -= used;
        }
        if (remaining != 0) throw new IllegalStateException("Inventory does not have capacity");
        return new Plan(before, after);
    }

    public List<Resource> eligible(PlayerInventory inventory, Iterable<Resource> resources) {
        List<Resource> result = new ArrayList<>();
        for (Resource resource : resources) {
            Material material = Material.matchMaterial(resource.material());
            if (resource.depositEnabled() && material != null && count(inventory, material) > 0) result.add(resource);
        }
        return List.copyOf(result);
    }

    public String evidenceAfterRemoval(PlayerInventory inventory, Map<Material, Integer> removals) {
        return planRemoval(inventory, removals).evidence();
    }

    public String evidenceAfterAddition(PlayerInventory inventory, ItemStack addition) {
        return planAddition(inventory, addition).evidence();
    }

    public String evidenceAfterHeldRemoval(PlayerInventory inventory) {
        return planHeldRemoval(inventory).evidence();
    }

    public String evidenceAfterCursorRemoval(Player player) {
        return planCursorRemoval(player).evidence();
    }

    /** A plain item is exactly the same metadata as a fresh vanilla stack. */
    public boolean isPlain(ItemStack item, Material expected) {
        if (item == null || expected == null || item.getType() != expected || item.getAmount() <= 0) return false;
        ItemStack pristine = new ItemStack(expected);
        return Objects.equals(pristine.getItemMeta(), item.getItemMeta());
    }

    private static ItemStack[] readSlots(Inventory inventory) {
        if (inventory == null) throw new IllegalArgumentException("Inventory is required");
        ItemStack[] slots = new ItemStack[MAIN_INVENTORY_SLOTS];
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) slots[slot] = normalize(inventory.getItem(slot));
        return slots;
    }

    private static ItemStack[] cloneSlots(ItemStack[] source) {
        ItemStack[] result = new ItemStack[MAIN_INVENTORY_SLOTS];
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) result[slot] = copy(source[slot]);
        return result;
    }

    private static ItemStack[] readInventory(Inventory inventory) {
        ItemStack[] result = new ItemStack[inventory.getSize()];
        for (int slot = 0; slot < result.length; slot++) result[slot] = normalize(inventory.getItem(slot));
        return result;
    }

    private static ItemStack[] readStagedSlots(Inventory inventory) {
        ItemStack[] result = new ItemStack[45];
        for (int slot = 0; slot < result.length; slot++) result[slot] = normalize(inventory.getItem(slot));
        return result;
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) result[slot] = copy(source[slot]);
        return result;
    }

    private static ItemStack normalize(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0 ? null : item;
    }

    private static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }

    private static ItemStack withAmount(ItemStack source, int amount) {
        ItemStack copy = source.clone();
        copy.setAmount(amount);
        return copy;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        left = normalize(left);
        right = normalize(right);
        if (left == null || right == null) return left == right;
        return left.getType() == right.getType()
                && left.getAmount() == right.getAmount()
                && Objects.equals(left.getItemMeta(), right.getItemMeta());
    }

    private static boolean sameTypeAndMeta(ItemStack left, ItemStack right) {
        return left != null && right != null && left.getType() == right.getType()
                && Objects.equals(left.getItemMeta(), right.getItemMeta());
    }

    private static String snapshot(ItemStack[] slots) {
        return snapshot(slots, -1, null);
    }

    private static String snapshot(ItemStack[] slots, int selectedSlot, ItemStack selected) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
            if (slot > 0) result.append('|');
            appendSlot(result, slot, slots[slot]);
        }
        if (selectedSlot >= MAIN_INVENTORY_SLOTS) {
            result.append('|');
            appendSlot(result, selectedSlot, selected);
        }
        return result.toString();
    }

    private static String snapshotArray(ItemStack[] slots) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < slots.length; slot++) {
            if (slot > 0) result.append('|');
            appendSlot(result, slot, slots[slot]);
        }
        return result.toString();
    }

    private static void appendSlot(StringBuilder result, int slot, ItemStack raw) {
        ItemStack item = normalize(raw);
        result.append("slot=").append(slot);
        if (item == null) {
            result.append(";material=EMPTY;count=0;metadata=-");
        } else {
            result.append(";material=").append(item.getType().name())
                    .append(";count=").append(item.getAmount())
                    .append(";metadata=").append(metadata(item));
        }
    }

    private static String cursorEvidence(String label, ItemStack cursor) {
        StringBuilder result = new StringBuilder(";").append(label).append("=");
        ItemStack item = normalize(cursor);
        if (item == null) return result.append("EMPTY;count=0;metadata=-").toString();
        return result.append(item.getType().name()).append(";count=").append(item.getAmount())
                .append(";metadata=").append(metadata(item)).toString();
    }

    private static String metadata(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "-";
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String keys = pdc.getKeys().stream().map(NamespacedKey::toString).sorted().collect(Collectors.joining(","));
        String noteId = pdc.get(new NamespacedKey("goldbag", "note-id"), PersistentDataType.STRING);
        return meta.getClass().getName() + ":" + canonical(meta.serialize()) + ":pdcKeys=[" + keys + "]"
                + ":noteId=" + (noteId == null ? "-" : noteId);
    }

    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?>) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) result.append(',');
                first = false;
                result.append(entry.getKey()).append('=').append(canonical(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof Iterable<?>) {
            List<String> values = new ArrayList<>();
            for (Object element : (Iterable<?>) value) values.add(canonical(element));
            Collections.sort(values);
            return values.toString();
        }
        return String.valueOf(value);
    }
}
