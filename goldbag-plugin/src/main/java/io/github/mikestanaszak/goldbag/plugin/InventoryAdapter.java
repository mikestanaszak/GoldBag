package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Resource;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** All methods are intended for the Bukkit main thread. */
public final class InventoryAdapter {
    public int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isPlain(item, material)) total += item.getAmount();
        }
        return total;
    }

    public boolean canRemove(PlayerInventory inventory, Material material, int count) { return count(inventory, material) >= count; }

    public void remove(PlayerInventory inventory, Material material, int count) {
        if (!canRemove(inventory, material, count)) throw new IllegalStateException("Inventory changed; required items are missing");
        int remaining = count;
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isPlain(item, material)) continue;
            int used = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - used);
            if (item.getAmount() == 0) inventory.setItem(slot, null);
            remaining -= used;
        }
    }

    public boolean canFit(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) remaining -= stack.getMaxStackSize();
            else if (existing.isSimilar(stack)) remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
        }
        return remaining <= 0;
    }

    public void add(PlayerInventory inventory, ItemStack stack) {
        if (!canFit(inventory, stack)) throw new IllegalStateException("Inventory does not have capacity");
        inventory.addItem(stack);
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
        Material[] materials = new Material[36]; int[] amounts = new int[36];
        for (int slot = 0; slot < 36; slot++) { ItemStack item = inventory.getItem(slot); if (item != null && item.getType() != Material.AIR) { materials[slot] = item.getType(); amounts[slot] = item.getAmount(); } }
        String before = snapshot(materials, amounts);
        for (Map.Entry<Material, Integer> removal : removals.entrySet()) {
            int remaining = removal.getValue();
            for (int slot = 0; slot < 36 && remaining > 0; slot++) if (materials[slot] == removal.getKey()) { int used = Math.min(remaining, amounts[slot]); amounts[slot] -= used; remaining -= used; }
        }
        return "before=" + before + ";after=" + snapshot(materials, amounts);
    }

    public String evidenceAfterAddition(PlayerInventory inventory, ItemStack addition) {
        Material[] materials = new Material[36]; int[] amounts = new int[36];
        for (int slot = 0; slot < 36; slot++) { ItemStack item = inventory.getItem(slot); if (item != null && item.getType() != Material.AIR) { materials[slot] = item.getType(); amounts[slot] = item.getAmount(); } }
        String before = snapshot(materials, amounts);
        int remaining = addition.getAmount();
        for (int slot = 0; slot < 36 && remaining > 0; slot++) if (materials[slot] == addition.getType() && amounts[slot] < addition.getMaxStackSize()) { int used = Math.min(remaining, addition.getMaxStackSize() - amounts[slot]); amounts[slot] += used; remaining -= used; }
        for (int slot = 0; slot < 36 && remaining > 0; slot++) if (materials[slot] == null) { materials[slot] = addition.getType(); int used = Math.min(remaining, addition.getMaxStackSize()); amounts[slot] = used; remaining -= used; }
        return "before=" + before + ";after=" + snapshot(materials, amounts);
    }

    public String evidenceAfterHeldRemoval(PlayerInventory inventory) {
        Material[] materials = new Material[36]; int[] amounts = new int[36];
        for (int slot = 0; slot < 36; slot++) { ItemStack item = inventory.getItem(slot); if (item != null && item.getType() != Material.AIR) { materials[slot] = item.getType(); amounts[slot] = item.getAmount(); } }
        String before = snapshot(materials, amounts);
        int held = inventory.getHeldItemSlot(); if (held >= 0 && held < 36 && amounts[held] > 0) amounts[held]--;
        return "before=" + before + ";after=" + snapshot(materials, amounts);
    }

    private String snapshot(Material[] materials, int[] amounts) {
        StringBuilder result = new StringBuilder();
        for (int slot = 0; slot < 36; slot++) { if (slot > 0) result.append('|'); result.append(slot).append('=').append(materials[slot] == null ? "EMPTY" : materials[slot].name()).append(':').append(amounts[slot]); }
        return result.toString();
    }

    public boolean isPlain(ItemStack item, Material expected) {
        if (item == null || item.getType() != expected || item.getAmount() <= 0) return false;
        ItemMeta meta = item.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasLore() && !meta.hasEnchants()
                && !meta.hasCustomModelData() && !meta.hasLocalizedName() && !meta.hasAttributeModifiers()
                && !meta.isUnbreakable() && meta.getItemFlags().isEmpty() && meta.getPersistentDataContainer().isEmpty());
    }
}
