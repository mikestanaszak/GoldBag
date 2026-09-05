package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Catalog;
import io.github.mikestanaszak.goldbag.core.Money;
import io.github.mikestanaszak.goldbag.core.Resource;
import io.github.mikestanaszak.goldbag.core.Settings;
import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bukkit adapter for the exact core and durable SQLite storage modules. */
public final class GoldBagPlugin extends JavaPlugin implements Listener {
    private final PermissionService permissions = new PermissionService();
    private final InventoryAdapter inventory = new InventoryAdapter();
    private final QuoteBook quotes = new QuoteBook(Clock.systemUTC());
    private final Set<UUID> guardedPlayers = new HashSet<>();
    private PluginConfig activeConfig;
    private StorageExecutor storageExecutor;
    private SqliteStore store;
    private ExchangeCoordinator coordinator;
    private MenuService menus;
    private GoldBagCommand commands;
    private NamespacedKey noteKey;
    private boolean healthy;

    @Override public void onEnable() {
        try {
            saveDefaults();
            activeConfig = PluginConfig.load(getDataFolder().toPath());
            Settings settings = activeConfig.settings();
            storageExecutor = new StorageExecutor(64);
            store = new SqliteStore(getDataFolder().toPath().resolve(settings.databaseFile()), settings.maxBalance());
            coordinator = new ExchangeCoordinator(store, storageExecutor);
            menus = new MenuService(this, inventory);
            commands = new GoldBagCommand(this);
            registerCommands();
            noteKey = new NamespacedKey(this, "note-id");
            Bukkit.getPluginManager().registerEvents(this, this);
            healthy = true;
            storageExecutor.submit(() -> store.pending()).whenComplete((pending, error) -> {
                if (error == null && !pending.isEmpty()) getLogger().warning("Pending GoldBag operations require operator recovery; mutations for affected accounts remain blocked.");
            });
            getLogger().info("GoldBag enabled with SQLite storage and " + activeConfig.catalog().resources().size() + " resources.");
        } catch (Exception error) {
            healthy = false;
            getLogger().severe("GoldBag disabled: " + error.getMessage());
            closeStorage();
        }
    }

    @Override public void onDisable() { healthy = false; quotes.invalidateCatalog(quotes.catalogRevision() + 1); closeStorage(); }

    private void saveDefaults() throws IOException {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw new IOException("Unable to create plugin data folder");
        copyDefault("config.yml"); copyDefault("resources.yml"); copyDefault("messages.yml");
    }

    private void copyDefault(String name) throws IOException {
        Path target = getDataFolder().toPath().resolve(name);
        if (Files.exists(target)) return;
        try (java.io.InputStream stream = getResource(name)) {
            if (stream == null) throw new IOException("Missing packaged resource " + name);
            Files.copy(stream, target);
        }
    }

    private void registerCommands() {
        org.bukkit.command.PluginCommand command = getCommand("goldbag");
        if (command == null) throw new IllegalStateException("plugin.yml did not register goldbag command");
        command.setExecutor(commands); command.setTabCompleter(commands);
        for (String alias : List.of("gb", "purse", "balance", "money", "pursetop", "withdraw")) {
            org.bukkit.command.PluginCommand aliasCommand = getCommand(alias);
            if (aliasCommand != null) { aliasCommand.setExecutor(commands); aliasCommand.setTabCompleter(commands); }
        }
    }

    private void closeStorage() {
        if (storageExecutor != null) storageExecutor.close();
        if (store != null) { try { store.close(); } catch (Exception error) { getLogger().warning("Error closing GoldBag storage: " + error.getMessage()); } }
        storageExecutor = null; store = null; coordinator = null;
    }

    public PluginConfig config() { if (activeConfig == null) throw new IllegalStateException("GoldBag is unavailable"); return activeConfig; }
    public SqliteStore store() { if (!healthy || store == null) throw new IllegalStateException("GoldBag storage is unavailable"); return store; }
    public ExchangeCoordinator coordinator() { if (!healthy || coordinator == null) throw new IllegalStateException("GoldBag storage is unavailable"); return coordinator; }
    public StorageExecutor executor() { return storageExecutor; }
    public PermissionService permissions() { return permissions; }
    public InventoryAdapter inventory() { return inventory; }
    public MenuService menus() { return menus; }
    public QuoteBook quotes() { return quotes; }
    public NamespacedKey noteKey() { return noteKey; }
    public boolean healthy() { return healthy; }
    public String money(long cents) { return config().settings().currencySymbol() + Money.format(cents); }
    public void guard(UUID player) { synchronized (guardedPlayers) { guardedPlayers.add(player); } }
    public void unguard(UUID player) { synchronized (guardedPlayers) { guardedPlayers.remove(player); } }
    public boolean guarded(UUID player) { synchronized (guardedPlayers) { return guardedPlayers.contains(player); } }

    public void ensureAccount(Player player) {
        submit(() -> store().ensureAccount(player.getUniqueId(), player.getName()), result -> { }, player,
                "Could not initialize your GoldBag account.");
    }

    public void reloadConfiguration(CommandSender actor) {
        try {
            PluginConfig next = PluginConfig.load(getDataFolder().toPath());
            if (!next.settings().databaseFile().equals(config().settings().databaseFile())
                    || next.settings().maxBalance() != config().settings().maxBalance()) {
                throw new IllegalArgumentException("Database file and maximum balance require a restart");
            }
            activeConfig = next;
            quotes.invalidateCatalog(quotes.catalogRevision() + 1);
            if (actor != null) tell(actor, "GoldBag configuration reloaded; pending quotes expired.");
        } catch (Exception error) { if (actor != null) tell(actor, ChatColor.RED + "Reload failed: " + error.getMessage()); else getLogger().severe("Reload failed: " + error.getMessage()); }
    }

    public ItemStack createNote(UUID id, long amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + config().settings().currencyName() + " banknote");
        meta.setLore(List.of(ChatColor.GRAY + "Value: " + money(amount), ChatColor.DARK_GRAY + "Trusted GoldBag note"));
        meta.getPersistentDataContainer().set(noteKey, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { if (healthy) ensureAccount(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { quotes.remove(event.getPlayer().getUniqueId(), quotes.current(event.getPlayer().getUniqueId()).map(QuoteBook.Quote::id).orElse(UUID.randomUUID())); unguard(event.getPlayer().getUniqueId()); }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) quotes.remove(event.getPlayer().getUniqueId(), quotes.current(event.getPlayer().getUniqueId()).map(QuoteBook.Quote::id).orElse(UUID.randomUUID()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && guarded(((Player) event.getWhoClicked()).getUniqueId())) { event.setCancelled(true); return; }
        if (!(event.getInventory().getHolder() instanceof MenuHolder) || !(event.getWhoClicked() instanceof Player)) return;
        event.setCancelled(true);
        MenuHolder holder = (MenuHolder) event.getInventory().getHolder();
        if (!holder.playerId().equals(event.getWhoClicked().getUniqueId()) || event.getRawSlot() < 0) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (holder.screen() == MenuHolder.Screen.MAIN) {
            if (slot == 12) menus.openDeposit(player); else if (slot == 14) menus.openWithdraw(player, 1);
            else if (slot == 22) showTop(player, 1); else if (slot == 10) showBalance(player, player.getUniqueId());
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot < 45) {
            List<Resource> eligible = inventory.eligible(player.getInventory(), config().catalog().resources());
            if (slot < eligible.size()) quoteDeposit(player, eligible.get(slot), inventory.count(player.getInventory(), Material.matchMaterial(eligible.get(slot).material())));
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot < 45) {
            List<Resource> resources = config().catalog().resources().stream().filter(Resource::withdrawEnabled).collect(java.util.stream.Collectors.toList());
            int index = (holder.page() - 1) * 45 + slot;
            if (index < resources.size()) { player.closeInventory(); quoteWithdrawal(player, resources.get(index), 1, false); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player && (guarded(((Player) event.getWhoClicked()).getUniqueId()) || menus.belongs(event.getInventory()))) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (guarded(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (guarded(event.getPlayer().getUniqueId())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!healthy || event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(noteKey, PersistentDataType.STRING)) return;
        if (!permissions.has(player, "goldbag.note", "goldpurse.use")) return;
        String idText = item.getItemMeta().getPersistentDataContainer().get(noteKey, PersistentDataType.STRING);
        UUID noteId;
        try { noteId = UUID.fromString(idText); } catch (IllegalArgumentException e) { return; }
        event.setCancelled(true);
        redeem(player, item, noteId);
    }

    private void redeem(Player player, ItemStack item, UUID noteId) {
        UUID op = UUID.randomUUID();
        submit(() -> store().prepareRedemption(op, player.getUniqueId(), noteId, "right-click"), pending -> {
            if (!player.isOnline() || player.isDead() || player.getInventory().getItemInMainHand() != item) { cancelPrepared(op, "player state changed before note redemption"); return; }
            coordinator().markApplying(op).whenComplete((ignored, error) -> runMain(() -> {
                if (error != null) { tell(player, ChatColor.RED + "Redemption is pending recovery: " + op); return; }
                ItemStack held = player.getInventory().getItemInMainHand(); held.setAmount(held.getAmount() - 1); player.getInventory().setItemInMainHand(held.getAmount() == 0 ? null : held);
                coordinator().complete(op).whenComplete((receipt, completeError) -> runMain(() -> { if (completeError != null) tell(player, ChatColor.RED + "Redemption is unresolved; quote operation " + op); else tell(player, "Banknote redeemed for " + money(pending.amount()) + "."); }));
            }));
        }, player, "Could not redeem this banknote.");
    }

    public void quoteDeposit(Player player, Resource resource, int count) {
        if (!permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        if (count < 1) { tell(player, "No eligible items found."); return; }
        count = Math.min(count, config().settings().maxItemsPerTransaction());
        long amount = config().catalog().depositValue(resource.material(), count);
        QuoteBook.Quote quote = quotes.put(player.getUniqueId(), QuoteBook.Kind.DEPOSIT, resource.material(), count, amount,
                quotes.catalogRevision() + 1, config().settings().quoteTimeoutSeconds());
        tell(player, "Deposit preview: " + count + " " + resource.id() + " for " + money(amount) + ". Use /goldbag confirm or /goldbag cancel.");
    }

    public void quoteWithdrawal(Player player, Resource resource, int count, boolean max) {
        if (!permissions.has(player, "goldbag.withdraw", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        final boolean chooseMax = max;
        submit(() -> store().account(player.getUniqueId()).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            int selected = chooseMax ? config().catalog().maximumWithdrawal(resource.material(), account.balance(), capacity(player, Material.matchMaterial(resource.material())), config().settings().maxItemsPerTransaction()) : count;
            if (selected < 1) { tell(player, "Insufficient balance or inventory capacity."); return; }
            long amount = config().catalog().withdrawalCost(resource.material(), selected);
            quotes.put(player.getUniqueId(), QuoteBook.Kind.WITHDRAW, resource.material(), selected, amount,
                    quotes.catalogRevision() + 1, config().settings().quoteTimeoutSeconds());
            tell(player, "Withdrawal preview: " + selected + " " + resource.id() + " for " + money(amount) + ". Use /goldbag confirm or /goldbag cancel.");
        }, player, "Could not read your balance.");
    }

    public void confirm(Player player) {
        QuoteBook.Quote quote = quotes.current(player.getUniqueId()).orElse(null);
        if (quote == null || quote.catalogRevision() != quotes.catalogRevision()) { tell(player, "Your quote expired; request a new preview."); return; }
        if (quote.kind() == QuoteBook.Kind.NOTE) { issueNote(player, quote.amount()); return; }
        if (quote.kind() == QuoteBook.Kind.DEPOSIT) executeDeposit(player, quote); else executeWithdrawal(player, quote);
    }

    public void cancel(Player player) { if (quotes.current(player.getUniqueId()).isPresent()) { quotes.current(player.getUniqueId()).ifPresent(q -> quotes.remove(player.getUniqueId(), q.id())); tell(player, "GoldBag operation cancelled."); } else tell(player, "There is no active GoldBag quote."); }

    private void executeDeposit(Player player, QuoteBook.Quote quote) {
        Material material = Material.matchMaterial(quote.material());
        if (!player.isOnline() || player.isDead() || !inventory.canRemove(player.getInventory(), material, quote.count())) { cancel(player); tell(player, "Inventory changed; nothing was deposited."); return; }
        UUID op = UUID.randomUUID(); guard(player.getUniqueId());
        submit(() -> store().prepare(op, player.getUniqueId(), SqliteStore.Kind.DEPOSIT, quote.amount(), quote.material() + ":" + quote.count(), null), pending -> {
            if (!player.isOnline() || player.isDead() || !inventory.canRemove(player.getInventory(), material, quote.count())) { cancelPrepared(op, "inventory changed before deposit"); unguard(player.getUniqueId()); return; }
            coordinator().markApplying(op).whenComplete((ignored, error) -> runMain(() -> {
                if (error != null) { unguard(player.getUniqueId()); tell(player, ChatColor.RED + "Deposit is pending recovery: " + op); return; }
                try { inventory.remove(player.getInventory(), material, quote.count()); } catch (RuntimeException changed) { tell(player, ChatColor.RED + "Deposit could not be applied; operation " + op + " requires recovery."); unguard(player.getUniqueId()); return; }
                coordinator().complete(op).whenComplete((receipt, completeError) -> runMain(() -> { unguard(player.getUniqueId()); if (completeError != null) tell(player, ChatColor.RED + "Deposit is unresolved; operation " + op); else { quotes.remove(player.getUniqueId(), quote.id()); tell(player, "Deposited " + quote.count() + " for " + money(quote.amount()) + "."); } }));
            }));
        }, player, "Could not prepare deposit.");
    }

    private void executeWithdrawal(Player player, QuoteBook.Quote quote) {
        Material material = Material.matchMaterial(quote.material());
        ItemStack item = new ItemStack(material, quote.count());
        if (!player.isOnline() || player.isDead() || !inventory.canFit(player.getInventory(), item)) { cancel(player); tell(player, "Inventory changed; nothing was withdrawn."); return; }
        UUID op = UUID.randomUUID(); guard(player.getUniqueId());
        submit(() -> store().prepare(op, player.getUniqueId(), SqliteStore.Kind.WITHDRAW, quote.amount(), quote.material() + ":" + quote.count(), null), pending -> {
            if (!player.isOnline() || player.isDead() || !inventory.canFit(player.getInventory(), item)) { cancelPrepared(op, "inventory changed before withdrawal"); unguard(player.getUniqueId()); return; }
            coordinator().markApplying(op).whenComplete((ignored, error) -> runMain(() -> {
                if (error != null) { unguard(player.getUniqueId()); tell(player, ChatColor.RED + "Withdrawal is pending recovery: " + op); return; }
                try { inventory.add(player.getInventory(), item); } catch (RuntimeException changed) { tell(player, ChatColor.RED + "Withdrawal could not be applied; operation " + op + " requires recovery."); unguard(player.getUniqueId()); return; }
                coordinator().complete(op).whenComplete((receipt, completeError) -> runMain(() -> { unguard(player.getUniqueId()); if (completeError != null) tell(player, ChatColor.RED + "Withdrawal is unresolved; operation " + op); else { quotes.remove(player.getUniqueId(), quote.id()); tell(player, "Withdrew " + quote.count() + " for " + money(quote.amount()) + "."); } }));
            }));
        }, player, "Could not prepare withdrawal.");
    }

    public void issueNote(Player player, long amount) {
        if (!permissions.has(player, "goldbag.note", "goldpurse.use") || !config().settings().banknotesEnabled()) { tell(player, ChatColor.RED + "Banknotes are unavailable."); return; }
        if (!inventory.canFit(player.getInventory(), new ItemStack(Material.PAPER))) { tell(player, "Inventory has no capacity for a banknote."); return; }
        UUID note = UUID.randomUUID(), op = UUID.randomUUID(); guard(player.getUniqueId());
        submit(() -> store().prepare(op, player.getUniqueId(), SqliteStore.Kind.NOTE_ISSUE, amount, "command", note), pending -> coordinator().markApplying(op).whenComplete((ignored, error) -> runMain(() -> {
            if (error != null) { unguard(player.getUniqueId()); tell(player, ChatColor.RED + "Banknote issuance is pending recovery: " + op); return; }
            try { inventory.add(player.getInventory(), createNote(note, amount)); } catch (RuntimeException changed) { unguard(player.getUniqueId()); tell(player, ChatColor.RED + "Banknote issuance requires recovery: " + op); return; }
            coordinator().complete(op).whenComplete((receipt, completeError) -> runMain(() -> { unguard(player.getUniqueId()); if (completeError != null) tell(player, ChatColor.RED + "Banknote issuance is unresolved: " + op); else { quotes.remove(player.getUniqueId(), quotes.current(player.getUniqueId()).map(QuoteBook.Quote::id).orElse(UUID.randomUUID())); tell(player, "Banknote issued for " + money(amount) + "."); } }));
        })), player, "Could not prepare banknote.");
    }

    private int capacity(Player player, Material material) {
        int slots = 0; for (int slot = 0; slot < 36; slot++) { ItemStack item = player.getInventory().getItem(slot); if (item == null || item.getType() == Material.AIR) slots += material.getMaxStackSize(); else if (item.getType() == material && inventory.isPlain(item, material)) slots += material.getMaxStackSize() - item.getAmount(); } return slots;
    }

    private void cancelPrepared(UUID op, String reason) { if (healthy) coordinator().cancel(op, reason).exceptionally(error -> null); }

    public void showBalance(Player viewer, UUID id) { submit(() -> store().account(id).orElse(null), account -> { if (account == null) tell(viewer, "No GoldBag account exists."); else tell(viewer, account.name() + " balance: " + money(account.balance())); }, viewer, "Could not read balance."); }
    public void showTop(Player player, int page) { submit(() -> store().top(page), accounts -> menus.openTop(player, accounts, page), player, "Could not read leaderboard."); }
    public void showRates(org.bukkit.command.CommandSender sender, String material) { try { Resource r = config().catalog().require(material); tell(sender, r.id() + ": deposit " + money(r.depositPrice()) + ", withdrawal " + money(r.withdrawPrice())); } catch (RuntimeException e) { tell(sender, ChatColor.RED + e.getMessage()); } }

    public <T> void submit(java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> success, Player player, String failure) {
        submit(task, success, (CommandSender) player, failure);
    }
    public <T> void submit(java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> success, CommandSender sender, String failure) {
        try { storageExecutor.submit(task).whenComplete((value, error) -> runMain(() -> { if (error != null) { if (sender != null) tell(sender, ChatColor.RED + failure + " " + rootMessage(error)); } else success.accept(value); })); }
        catch (RuntimeException error) { if (sender != null) tell(sender, ChatColor.RED + failure); }
    }
    private void runMain(Runnable action) { Bukkit.getScheduler().runTask(this, action); }
    private String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? "" : current.getMessage(); }
    public void tell(org.bukkit.command.CommandSender sender, String message) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message)); }
}
