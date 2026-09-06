package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Resource;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All methods are intended for the Bukkit main thread. */
public final class InventoryAdapter {
    public static final int MAIN_INVENTORY_SLOTS = 36;

    /** Immutable before/after plan for a physical main-inventory change. */
    public static final class Plan {
        private final ItemStack[] before;
        private final ItemStack[] after;
        private final List<Integer> affectedSlots;
        private final String evidence;

        private Plan(ItemStack[] before, ItemStack[] after) {
            this.before = cloneSlots(before);
            this.after = cloneSlots(after);
            List<Integer> changed = new ArrayList<>();
            for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
                if (!same(this.before[slot], this.after[slot])) changed.add(slot);
            }
            this.affectedSlots = List.copyOf(changed);
            this.evidence = "before=" + snapshot(this.before) + ";after=" + snapshot(this.after);
        }

        public List<ItemStack> before() { return immutableClones(before); }
        public List<ItemStack> after() { return immutableClones(after); }
        public List<Integer> affectedSlots() { return affectedSlots; }
        public String evidence() { return evidence; }
        public String beforeEvidence() { return "before=" + snapshot(before); }
        public String afterEvidence() { return "after=" + snapshot(after); }

        /** Checks all 36 slots so a changed unrelated slot also aborts safely. */
        public boolean ready(PlayerInventory inventory) {
            if (inventory == null) return false;
            try {
                for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
                    if (!same(before[slot], normalize(inventory.getItem(slot)))) return false;
                }
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        /** Revalidates before state, then writes only affected slots. */
        public void apply(PlayerInventory inventory) {
            if (!ready(inventory)) throw new IllegalStateException("Inventory changed; physical plan is no longer ready");
            for (int slot : affectedSlots) inventory.setItem(slot, copy(after[slot]));
        }

        private static List<ItemStack> immutableClones(ItemStack[] slots) {
            List<ItemStack> result = new ArrayList<>(MAIN_INVENTORY_SLOTS);
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
        ItemStack[] before = readSlots(inventory);
        int held = inventory.getHeldItemSlot();
        if (held < 0 || held >= MAIN_INVENTORY_SLOTS || before[held] == null) {
            throw new IllegalStateException("Main hand is empty");
        }
        ItemStack[] after = cloneSlots(before);
        after[held] = before[held].getAmount() == 1 ? null : withAmount(before[held], before[held].getAmount() - 1);
        return new Plan(before, after);
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
            if (existing == null || !existing.isSimilar(stack)) continue;
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

    /** A plain item is exactly the same metadata as a fresh vanilla stack. */
    public boolean isPlain(ItemStack item, Material expected) {
        if (item == null || expected == null || item.getType() != expected || item.getAmount() <= 0) return false;
        return new ItemStack(expected).isSimilar(item);
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

    private static String snapshot(ItemStack[] slots) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
            if (slot > 0) result.append('|');
            ItemStack item = normalize(slots[slot]);
            result.append("slot=").append(slot);
            if (item == null) {
                result.append(";material=EMPTY;count=0;metadata=-");
            } else {
                result.append(";material=").append(item.getType().name())
                        .append(";count=").append(item.getAmount())
                        .append(";metadata=").append(metadata(item));
            }
        }
        return result.toString();
    }

    private static String metadata(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "-";
        return meta.getClass().getName() + ":" + meta.serialize() + ":pdc="
                + meta.getPersistentDataContainer();
    }
}
