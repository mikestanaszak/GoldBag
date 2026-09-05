package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Resource;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

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

    public boolean isPlain(ItemStack item, Material expected) {
        if (item == null || item.getType() != expected || item.getAmount() <= 0) return false;
        ItemMeta meta = item.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasLore() && !meta.hasEnchants()
                && !meta.hasCustomModelData() && meta.getPersistentDataContainer().isEmpty());
    }
}
