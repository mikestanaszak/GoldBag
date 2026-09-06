package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Catalog;
import io.github.mikestanaszak.goldbag.core.Money;
import io.github.mikestanaszak.goldbag.core.Resource;
import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Main-thread menu renderer. Clicks are handled by the plugin listener via holders. */
public final class MenuService {
    private final GoldBagPlugin plugin;
    private final InventoryAdapter inventory;

    public MenuService(GoldBagPlugin plugin, InventoryAdapter inventory) {
        this.plugin = plugin;
        this.inventory = inventory;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.MAIN);
        Inventory menu = Bukkit.createInventory(holder, 27, ChatColor.GOLD + "GoldBag");
        holder.inventory(menu);
        set(menu, 10, Material.GOLD_INGOT, "Balance", "View your purse balance");
        set(menu, 12, Material.CHEST, "Deposit", "Deposit eligible inventory items");
        set(menu, 14, Material.DIAMOND, "Withdraw Resources", "Withdraw catalog resources");
        set(menu, 15, Material.PAPER, "Pay Player", "Use /goldbag pay");
        set(menu, 16, Material.PAPER, "Create Banknote", "Use /goldbag note");
        set(menu, 22, Material.EMERALD, "Top Balances", "View the leaderboard");
        player.openInventory(menu);
    }

    public void openDeposit(Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.DEPOSIT);
        Inventory menu = Bukkit.createInventory(holder, 54, ChatColor.GREEN + "GoldBag Deposit");
        holder.inventory(menu);
        int slot = 0;
        for (Resource resource : inventory.eligible(player.getInventory(), plugin.config().catalog().resources())) {
            if (slot >= 45) break;
            Material material = Material.matchMaterial(resource.material());
            set(menu, slot++, material, resource.id(), "Available: " + inventory.count(player.getInventory(), material),
                    "Value: " + plugin.money(resource.depositPrice()));
        }
        set(menu, 49, Material.BARRIER, "Close", "Cancel");
        set(menu, 53, Material.GOLD_INGOT, "Deposit all eligible", "Preview every eligible main-inventory stack");
        player.openInventory(menu);
    }

    public void openWithdraw(Player player, int page) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.WITHDRAW, page);
        Inventory menu = Bukkit.createInventory(holder, 54, ChatColor.YELLOW + "GoldBag Withdraw " + page);
        holder.inventory(menu);
        List<Resource> resources = plugin.config().catalog().resources().stream().filter(Resource::withdrawEnabled).collect(java.util.stream.Collectors.toList());
        int from = (page - 1) * 45;
        for (int i = 0; i < 45 && from + i < resources.size(); i++) {
            Resource resource = resources.get(from + i);
            Material material = Material.matchMaterial(resource.material());
            set(menu, i, material, resource.id(), "Price: " + plugin.money(resource.withdrawPrice()), "Use /goldbag withdraw " + resource.id() + " <count|max>");
        }
        if (page > 1) set(menu, 45, Material.ARROW, "Previous page");
        if (from + 45 < resources.size()) set(menu, 53, Material.ARROW, "Next page");
        set(menu, 49, Material.BARRIER, "Close", "Cancel");
        player.openInventory(menu);
    }

    public void openTop(Player player, List<SqliteStore.Account> accounts, int page) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.TOP);
        Inventory menu = Bukkit.createInventory(holder, 27, ChatColor.AQUA + "GoldBag Top " + page);
        holder.inventory(menu);
        int slot = 0;
        for (SqliteStore.Account account : accounts) {
            if (slot >= 18) break;
            set(menu, slot++, Material.GOLD_NUGGET, account.name(), "Balance: " + plugin.money(account.balance()));
        }
        if (page > 1) set(menu, 18, Material.ARROW, "Previous page");
        if (accounts.size() == 10) set(menu, 26, Material.ARROW, "Next page");
        set(menu, 22, Material.BARRIER, "Close", "Cancel");
        player.openInventory(menu);
    }

    public void openQuantity(Player player, Resource resource, QuoteBook.Kind kind) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.PREVIEW, resource.material(), kind);
        Inventory menu = Bukkit.createInventory(holder, 27, ChatColor.YELLOW + "Choose " + resource.id() + " quantity");
        holder.inventory(menu);
        set(menu, 10, Material.IRON_NUGGET, "1", "Preview one item");
        set(menu, 12, Material.IRON_INGOT, "16", "Preview a stack");
        set(menu, 14, Material.IRON_BLOCK, "64", "Preview a full stack");
        set(menu, 16, Material.GOLD_BLOCK, "Max", "Preview the maximum allowed");
        set(menu, 22, Material.NAME_TAG, "Exact count", "Enter a whole item count privately");
        set(menu, 26, Material.BARRIER, "Close", "Cancel");
        player.openInventory(menu);
    }

    public boolean belongs(Inventory inventory) { return inventory != null && inventory.getHolder() instanceof MenuHolder; }

    private static void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        if (material == null) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
}
