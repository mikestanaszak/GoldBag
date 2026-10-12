package io.github.mikestanaszak.goldbag.plugin;

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
        set(menu, 12, Material.CHEST, "Deposit", "Shift-click or place resources to deposit");
        set(menu, 14, Material.DIAMOND, "Withdraw Resources", "Click resources to withdraw immediately");
        set(menu, 15, Material.PAPER, "Pay Player", "Click to enter a payment");
        set(menu, 16, Material.PAPER, "Create Banknote", "Click to enter a banknote amount");
        set(menu, 22, Material.EMERALD, "Top Balances", "View the leaderboard");
        display(player, menu);
    }

    public void openDeposit(Player player) {
        openDeposit(player, 1);
    }

    public void openDeposit(Player player, int page) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.DEPOSIT, 1,
                plugin.quotes().catalogRevision(), List.of(), false);
        Inventory menu = Bukkit.createInventory(holder, 54, ChatColor.GREEN + "GoldBag Deposit");
        holder.inventory(menu);
        for (int slot = 45; slot < 54; slot++) set(menu, slot, Material.GRAY_STAINED_GLASS_PANE, " ");
        set(menu, 45, Material.ARROW, "Back", "Return to GoldBag");
        set(menu, 48, Material.DIAMOND, "Withdraw Resources", "Switch to withdrawals");
        set(menu, 49, Material.BARRIER, "Close");
        set(menu, 50, Material.PAPER, "Deposit resources here",
                "Shift-click a stack from your inventory",
                "Or place items in any empty chest slot",
                "Items convert to Gold immediately",
                "Only enabled, ordinary resources are accepted");
        set(menu, 52, Material.GOLD_INGOT, "Deposit all eligible", "Deposit eligible inventory items immediately");
        display(player, menu);
    }

    public void openWithdraw(Player player, int page) {
        List<Resource> resources = plugin.config().catalog().resources().stream().filter(Resource::withdrawEnabled).collect(java.util.stream.Collectors.toList());
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.WITHDRAW, page, plugin.quotes().catalogRevision(), resources.stream().map(Resource::id).collect(java.util.stream.Collectors.toList()), hasNextResourcePage(page, resources.size()));
        Inventory menu = Bukkit.createInventory(holder, 54, ChatColor.YELLOW + "GoldBag Withdraw " + page);
        holder.inventory(menu);
        int from = (page - 1) * 45;
        for (int i = 0; i < 45 && from + i < resources.size(); i++) {
            Resource resource = resources.get(from + i);
            Material material = Material.matchMaterial(resource.material());
            set(menu, i, material, resource.id(), "Price: " + plugin.money(resource.withdrawPrice()), "Left-click: 1", "Right-click: up to 16", "Shift-click: up to 64", "Items go straight to your inventory");
        }
        if (page > 1) set(menu, 45, Material.ARROW, "Previous page");
        if (hasNextResourcePage(page, resources.size())) set(menu, 53, Material.ARROW, "Next page");
        set(menu, 48, Material.CHEST, "Deposit", "Switch to deposits");
        set(menu, 49, Material.BARRIER, "Close");
        set(menu, 50, Material.PAPER, "Withdraw immediately",
                "Left-click: 1 / Right-click: up to 16",
                "Shift-click: up to 64", "Limited by balance and inventory space");
        set(menu, 51, Material.ARROW, "Back", "Return to GoldBag");
        display(player, menu);
    }

    public void openTop(Player player, List<SqliteStore.Account> accounts, int page) {
        plugin.submit(() -> plugin.store().top(page + 1), nextPage -> renderTop(player, accounts, page, hasNextTopPage(nextPage)), player,
                "Could not read leaderboard.");
    }

    private void renderTop(Player player, List<SqliteStore.Account> accounts, int page, boolean hasNext) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuHolder.Screen.TOP, page, 0,
                accounts.stream().map(SqliteStore.Account::name).collect(java.util.stream.Collectors.toList()), hasNext);
        Inventory menu = Bukkit.createInventory(holder, 27, ChatColor.AQUA + "GoldBag Top " + page);
        holder.inventory(menu);
        int slot = 0;
        for (SqliteStore.Account account : accounts) {
            if (slot >= 18) break;
            set(menu, slot++, Material.GOLD_NUGGET, account.name(), "Balance: " + plugin.money(account.balance()));
        }
        if (page > 1) set(menu, 18, Material.ARROW, "Previous page");
        if (hasNext) set(menu, 26, Material.ARROW, "Next page");
        set(menu, 22, Material.BARRIER, "Close", "Cancel");
        display(player, menu);
    }

    public boolean belongs(Inventory inventory) { return inventory != null && inventory.getHolder() instanceof MenuHolder; }

    /** Opens the transaction confirmation after the quote has been stored. */
    public void openConfirmation(Player player, QuoteBook.Quote quote, long balanceBefore) {
        MenuHolder holder = MenuHolder.confirmation(player.getUniqueId(), quote, balanceBefore);
        Inventory menu = Bukkit.createInventory(holder, 27, ChatColor.GOLD + "GoldBag Confirm");
        holder.inventory(menu);
        set(menu, 11, Material.LIME_WOOL, "Confirm", "Complete this transaction");
        long balanceAfter = quote.kind() == QuoteBook.Kind.DEPOSIT
                ? Math.addExact(balanceBefore, quote.amount()) : Math.subtractExact(balanceBefore, quote.amount());
        set(menu, 13, Material.PAPER, "Transaction summary",
                confirmationSummary(quote, balanceBefore, plugin.money(quote.amount()),
                        plugin.money(balanceBefore), plugin.money(balanceAfter)).split("\\n"));
        set(menu, 15, Material.RED_WOOL, "Cancel", "Cancel this transaction");
        display(player, menu);
    }

    static boolean hasNextResourcePage(int page, int total) { return page > 0 && page * 45 < total; }

    static int depositMaxCount(int available, int transactionLimit) {
        return Math.max(0, Math.min(available, transactionLimit));
    }

    static boolean hasNextTopPage(List<SqliteStore.Account> nextPage) { return nextPage != null && !nextPage.isEmpty(); }

    static String confirmationSummary(QuoteBook.Quote quote, long balanceBefore) {
        long balanceAfter = quote.kind() == QuoteBook.Kind.DEPOSIT
                ? Math.addExact(balanceBefore, quote.amount()) : Math.subtractExact(balanceBefore, quote.amount());
        return confirmationSummary(quote, balanceBefore, Money.format(quote.amount()),
                Money.format(balanceBefore), Money.format(balanceAfter));
    }

    static String confirmationSummary(QuoteBook.Quote quote, long balanceBefore,
                                      String amountText, String balanceBeforeText, String balanceAfterText) {
        String operation = quote.kind() == QuoteBook.Kind.DEPOSIT ? "Deposit"
                : quote.kind() == QuoteBook.Kind.WITHDRAW ? "Withdrawal" : "Banknote";
        String count = quote.kind() == QuoteBook.Kind.NOTE ? "1 banknote"
                : quote.payload() == null ? quote.count() + " x " + quote.material() : payloadSummary(quote.payload());
        return "Operation: " + operation + "\n"
                + "Count: " + count + "\n"
                + "Amount: " + amountText + "\n"
                + "Balance before: " + balanceBeforeText + "\n"
                + "Balance after: " + balanceAfterText;
    }

    private static String payloadSummary(String payload) {
        List<String> entries = new ArrayList<>();
        for (String entry : payload.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) entries.add(parts[1] + " x " + parts[0]);
        }
        return entries.isEmpty() ? "items" : String.join(", ", entries);
    }

    private void display(Player player, Inventory menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(menu);
                refreshBalance(player);
            }
        });
    }

    public void refreshBalance(Player player) {
        Inventory menu = player.getOpenInventory().getTopInventory();
        if (!(menu.getHolder() instanceof MenuHolder)) return;
        MenuHolder holder = (MenuHolder) menu.getHolder();
        if (holder.screen() != MenuHolder.Screen.DEPOSIT && holder.screen() != MenuHolder.Screen.WITHDRAW) return;
        java.util.UUID playerId = player.getUniqueId();
        plugin.submit(() -> plugin.store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")),
                account -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu) return;
                    ItemStack info = menu.getItem(50);
                    if (info == null) return;
                    ItemMeta meta = info.getItemMeta();
                    meta.setDisplayName(ChatColor.GOLD + "Balance: " + plugin.money(account.balance()));
                    info.setItemMeta(meta);
                    menu.setItem(50, info);
                }, player, "Could not read your balance.");
    }

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
