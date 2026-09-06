package io.github.mikestanaszak.goldbag.plugin;

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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.StringPrompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bukkit adapter for the exact core and durable SQLite storage modules. */
public final class GoldBagPlugin extends JavaPlugin implements Listener {
    private final PermissionService permissions = new PermissionService();
    private final InventoryAdapter inventory = new InventoryAdapter();
    private final QuoteBook quotes = new QuoteBook(Clock.systemUTC());
    private final Map<UUID, UUID> guardedPlayers = new HashMap<>();
    private final QuoteRequestBook quoteRequests = new QuoteRequestBook();
    private PluginConfig activeConfig;
    private StorageExecutor storageExecutor;
    private SqliteStore store;
    private ExchangeCoordinator coordinator;
    private MenuService menus;
    private GoldBagCommand commands;
    private NamespacedKey noteKey;
    private boolean healthy;

    @Override public void onEnable() {
        if (storageExecutor != null || store != null) {
            getLogger().severe("GoldBag storage ownership was retained after an incomplete shutdown; a full server restart is required before enabling again.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
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
            healthy = false;
            storageExecutor.submit(() -> store.pending()).whenComplete((pending, error) -> runMain(() -> {
                if (error != null) {
                    getLogger().severe("GoldBag storage health check failed: " + rootMessage(error));
                    Bukkit.getPluginManager().disablePlugin(this);
                    return;
                }
                healthy = true;
                if (!pending.isEmpty()) getLogger().warning("Pending GoldBag operations require operator recovery; mutations for affected accounts remain blocked.");
                for (Player online : Bukkit.getOnlinePlayers()) ensureAccount(online);
            }));
            getLogger().info("GoldBag enabled with SQLite storage and " + activeConfig.catalog().resources().size() + " resources.");
        } catch (Exception error) {
            healthy = false;
            getLogger().severe("GoldBag disabled: " + error.getMessage());
            closeStorage();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() { healthy = false; quotes.invalidateCatalog(quotes.catalogRevision() + 1); closeStorage(); clearGuards(); }

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
        if (storageExecutor != null && !storageExecutor.stop()) {
            getLogger().severe("GoldBag storage writer did not stop before shutdown; database ownership is retained to avoid a close race.");
            return;
        }
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
    /** Acquires the inventory guard for one durable operation token. */
    public boolean guard(UUID player, UUID operation) {
        synchronized (guardedPlayers) {
            if (guardedPlayers.containsKey(player)) return false;
            guardedPlayers.put(player, operation);
            return true;
        }
    }
    /** Releases only the guard owned by this operation; late callbacks cannot release a newer guard. */
    public void unguard(UUID player, UUID operation) {
        releaseGuard(guardedPlayers, player, operation);
    }
    static void releaseGuard(Map<UUID, UUID> guards, UUID player, UUID operation) {
        synchronized (guards) {
            if (operation.equals(guards.get(player))) guards.remove(player);
        }
    }
    /** Clears a player guard when the player leaves; the operation callback remains token-safe. */
    private void unguard(UUID player) { synchronized (guardedPlayers) { guardedPlayers.remove(player); } }
    private void clearGuards() { synchronized (guardedPlayers) { guardedPlayers.clear(); } }
    public boolean guarded(UUID player) { synchronized (guardedPlayers) { return guardedPlayers.containsKey(player); } }

    public void ensureAccount(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        submit(() -> store().ensureAccount(playerId, playerName), result -> { }, player,
                "Could not initialize your GoldBag account.");
    }

    public void reloadConfiguration(CommandSender actor) {
        storageExecutor.submit(() -> PluginConfig.load(getDataFolder().toPath())).whenComplete((next, error) -> runMain(() -> {
            try {
                if (error != null) throw new IllegalArgumentException(rootMessage(error));
                if (!next.settings().databaseFile().equals(config().settings().databaseFile())
                        || next.settings().maxBalance() != config().settings().maxBalance()) {
                    throw new IllegalArgumentException("Database file and maximum balance require a restart");
                }
                activeConfig = next;
                quoteRequests.clear();
                quotes.invalidateCatalog(quotes.catalogRevision() + 1);
                if (actor != null) tell(actor, "GoldBag configuration reloaded; pending quotes expired.");
            } catch (Exception reloadError) { if (actor != null) tell(actor, ChatColor.RED + "Reload failed: " + reloadError.getMessage()); else getLogger().severe("Reload failed: " + reloadError.getMessage()); }
        }));
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
    @EventHandler public void onQuit(PlayerQuitEvent event) { quotes.remove(event.getPlayer().getUniqueId(), quotes.current(event.getPlayer().getUniqueId()).map(QuoteBook.Quote::id).orElse(UUID.randomUUID())); invalidateQuoteRequest(event.getPlayer().getUniqueId()); unguard(event.getPlayer().getUniqueId()); }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) { quotes.remove(event.getPlayer().getUniqueId(), quotes.current(event.getPlayer().getUniqueId()).map(QuoteBook.Quote::id).orElse(UUID.randomUUID())); invalidateQuoteRequest(event.getPlayer().getUniqueId()); }
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
        if ((holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 49)
                || (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 49)
                || (holder.screen() == MenuHolder.Screen.TOP && slot == 22)
                || (holder.screen() == MenuHolder.Screen.PREVIEW && slot == 26)) {
            player.closeInventory();
        } else if (holder.screen() == MenuHolder.Screen.MAIN) {
            if (slot == 12) menus.openDeposit(player); else if (slot == 14) menus.openWithdraw(player, 1);
            else if (slot == 15) { player.closeInventory(); startPayPrompt(player); } else if (slot == 16) { player.closeInventory(); startNotePrompt(player); }
            else if (slot == 22) { if (!permissions.has(player, "goldbag.top", "goldpurse.use")) tell(player, message("permission", "You do not have permission.")); else showTop(player, 1); }
            else if (slot == 10) { if (!permissions.has(player, "goldbag.balance", "goldpurse.use")) tell(player, message("permission", "You do not have permission.")); else showBalance(player, player.getUniqueId()); }
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot < 45) {
            if (holder.catalogRevision() != quotes.catalogRevision()) { tell(player, "This menu expired after a catalog reload."); return; }
            int index = (holder.page() - 1) * 45 + slot;
            if (index < holder.options().size()) menus.openQuantity(player, config().catalog().require(holder.options().get(index)), QuoteBook.Kind.DEPOSIT);
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 45 && holder.page() > 1) {
            menus.openDeposit(player, holder.page() - 1);
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 52) {
            quoteDepositAll(player);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot < 45) {
            if (holder.catalogRevision() != quotes.catalogRevision()) { tell(player, "This menu expired after a catalog reload."); return; }
            int index = (holder.page() - 1) * 45 + slot;
            if (index < holder.options().size()) menus.openQuantity(player, config().catalog().require(holder.options().get(index)), QuoteBook.Kind.WITHDRAW);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 45 && holder.page() > 1) {
            menus.openWithdraw(player, holder.page() - 1);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 53 && holder.hasNext()) {
            menus.openWithdraw(player, holder.page() + 1);
        } else if (holder.screen() == MenuHolder.Screen.TOP && slot == 18 && holder.page() > 1) {
            showTop(player, holder.page() - 1);
        } else if (holder.screen() == MenuHolder.Screen.TOP && slot == 26 && holder.hasNext()) {
            showTop(player, holder.page() + 1);
        } else if (holder.screen() == MenuHolder.Screen.PREVIEW) {
            Resource resource = config().catalog().require(holder.selection());
            if (slot == 10) selectQuantity(player, resource, 1, false, holder.selectionKind());
            else if (slot == 12) selectQuantity(player, resource, 16, false, holder.selectionKind());
            else if (slot == 14) selectQuantity(player, resource, 64, false, holder.selectionKind());
            else if (slot == 16) selectQuantity(player, resource, 0, true, holder.selectionKind());
            else if (slot == 22) startExactWithdrawalPrompt(player, resource, holder.selectionKind());
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
        if (guarded(event.getPlayer().getUniqueId())) { event.setCancelled(true); return; }
        if (!healthy || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (event.getHand() == EquipmentSlot.HAND && config().settings().shortcutEnabled() && player.isSneaking() && item != null && item.getType() == Material.RAW_GOLD) {
            if (permissions.has(player, "goldbag.use", "goldpurse.use")) { event.setCancelled(true); menus.openMain(player); }
            return;
        }
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(noteKey, PersistentDataType.STRING)) return;
        if (!permissions.has(player, "goldbag.note", "goldpurse.use")) return;
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "Banknotes are unavailable in your current game mode."); return; }
        String idText = item.getItemMeta().getPersistentDataContainer().get(noteKey, PersistentDataType.STRING);
        UUID noteId;
        try { noteId = UUID.fromString(idText); } catch (IllegalArgumentException e) { return; }
        event.setCancelled(true);
        redeem(player, event.getHand() == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot(), noteId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && guarded(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    private void redeem(Player player, int slot, UUID noteId) {
        UUID playerId = player.getUniqueId();
        UUID op = UUID.randomUUID();
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = inventory.planSlotRemoval(player.getInventory(), slot); }
        catch (RuntimeException error) { tell(player, "Could not read the banknote inventory state."); return; }
        if (!guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
        String evidence = "right-click|" + physicalPlan.evidence();
        CompletableFuture<SqliteStore.Receipt> operation;
        try {
            operation = coordinator().executeRedemption(op, playerId, noteId, evidence, this::runMain,
                    new ExchangeCoordinator.InventoryPort() {
                        @Override public boolean ready() {
                            return player.isOnline() && !player.isDead()
                                    && gameModeAllowed(player)
                                    && permissions.has(player, "goldbag.note", "goldpurse.use")
                                    && sameNote(slot == 40 ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand(), noteId)
                                    && physicalPlan.ready(player.getInventory());
                        }
                        @Override public void apply() { physicalPlan.apply(player.getInventory()); }
                    });
        } catch (RuntimeException error) { unguard(playerId, op); tell(player, ChatColor.RED + "Could not redeem this banknote."); return; }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> tell(player, "Banknote redeemed."), "Banknote redemption is unresolved or was cancelled: " + op));
    }

    public void quoteDeposit(Player player, Resource resource, int count) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        if (count < 1) { tell(player, "No eligible items found."); return; }
        if (count > config().settings().maxItemsPerTransaction()) { tell(player, "That exceeds the transaction item limit."); return; }
        Material depositMaterial = Material.matchMaterial(resource.material());
        if (depositMaterial == null || inventory.count(player.getInventory(), depositMaterial) < count) { tell(player, "Your inventory does not contain that quantity."); return; }
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        long amount = config().catalog().depositValue(resource.material(), count);
        submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            if (!quoteRequestActive(playerId, request, revision)) return;
            if (amount > config().settings().maxBalance() - account.balance()) { tell(player, "That deposit would exceed the maximum balance."); return; }
            quotes.put(playerId, QuoteBook.Kind.DEPOSIT, resource.material(), count, amount,
                    revision, config().settings().quoteTimeoutSeconds());
            tell(player, "Deposit preview: " + count + " " + resource.id() + " for " + money(amount) + "; balance after: " + money(Math.addExact(account.balance(), amount)) + ". Use /goldbag confirm or /goldbag cancel.");
        }, player, "Could not read your balance.");
    }

    public void quoteDepositAll(Player player) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        List<String> entries = new ArrayList<>();
        long amount = 0; int total = 0;
        for (Resource resource : inventory.eligible(player.getInventory(), config().catalog().resources())) {
            Material material = Material.matchMaterial(resource.material());
            int count = Math.min(inventory.count(player.getInventory(), material), config().settings().maxItemsPerTransaction() - total);
            if (count <= 0) break;
            entries.add(resource.material() + "=" + count);
            amount = Math.addExact(amount, config().catalog().depositValue(resource.material(), count));
            total += count;
        }
        if (entries.isEmpty()) { tell(player, "No eligible items found."); return; }
        String payload = String.join(";", entries);
        final int totalItems = total;
        final long totalAmount = amount;
        submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            if (!quoteRequestActive(playerId, request, revision)) return;
            if (totalAmount > config().settings().maxBalance() - account.balance()) { tell(player, "That deposit would exceed the maximum balance."); return; }
            quotes.put(playerId, QuoteBook.Kind.DEPOSIT, "*", totalItems, totalAmount,
                    revision, config().settings().quoteTimeoutSeconds(), payload);
            tell(player, "Deposit-all preview: " + entries.stream().map(value -> value.replace('=', 'x')).collect(java.util.stream.Collectors.joining(", ")) + " for " + money(totalAmount) + "; balance after: " + money(account.balance() + totalAmount) + ". Use /goldbag confirm or /goldbag cancel.");
        }, player, "Could not read your balance.");
    }

    public void startPayPrompt(Player player) {
        if (!permissions.has(player, "goldbag.pay", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        beginPrompt(player, new StringPrompt() {
            @Override public String getPromptText(ConversationContext context) { return "Enter recipient and amount (for example: Alice 2.50), or cancel:"; }
            @Override public org.bukkit.conversations.Prompt acceptInput(ConversationContext context, String input) {
                if (input == null || input.trim().equalsIgnoreCase("cancel")) { tell(player, "Payment cancelled."); return null; }
                String[] parts = input.trim().split("\\s+", 3);
                if (parts.length != 2) { tell(player, "Use: <player|uuid> <amount>"); return this; }
                try { commands.onCommand(player, getCommand("goldbag"), "goldbag", new String[]{"pay", parts[0], parts[1]}); return null; }
                catch (RuntimeException error) { tell(player, ChatColor.RED + error.getMessage()); return this; }
            }
        });
    }

    public void startNotePrompt(Player player) {
        if (!permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        beginPrompt(player, new StringPrompt() {
            @Override public String getPromptText(ConversationContext context) { return "Enter banknote amount, or cancel:"; }
            @Override public org.bukkit.conversations.Prompt acceptInput(ConversationContext context, String input) {
                if (input == null || input.trim().equalsIgnoreCase("cancel")) { tell(player, "Banknote cancelled."); return null; }
                try { commands.onCommand(player, getCommand("goldbag"), "goldbag", new String[]{"note", input.trim()}); return null; }
                catch (RuntimeException error) { tell(player, ChatColor.RED + error.getMessage()); return this; }
            }
        });
    }

    private void beginPrompt(Player player, StringPrompt prompt) {
        new ConversationFactory(this).withLocalEcho(false).withTimeout(60).withFirstPrompt(prompt).buildConversation(player).begin();
    }

    public void quoteWithdrawal(Player player, Resource resource, int count, boolean max) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.withdraw", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        final boolean chooseMax = max;
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            if (!quoteRequestActive(playerId, request, revision)) return;
            int maximum = config().catalog().maximumWithdrawal(resource.material(), account.balance(), capacity(player, Material.matchMaterial(resource.material())), config().settings().maxItemsPerTransaction());
            if (!chooseMax && count > maximum) { tell(player, "That quantity exceeds your balance, capacity, or transaction limit."); return; }
            int selected = chooseMax ? maximum : count;
            if (selected < 1) { tell(player, "Insufficient balance or inventory capacity."); return; }
            long amount = config().catalog().withdrawalCost(resource.material(), selected);
            quotes.put(playerId, QuoteBook.Kind.WITHDRAW, resource.material(), selected, amount,
                    revision, config().settings().quoteTimeoutSeconds());
            tell(player, "Withdrawal preview: " + selected + " " + resource.id() + " for " + money(amount) + "; balance after: " + money(account.balance() - amount) + ". Use /goldbag confirm or /goldbag cancel.");
        }, player, "Could not read your balance.");
    }

    public void quoteNote(Player player, long amount) {
        if (!gameModeAllowed(player) || !permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "Banknotes are unavailable."); return; }
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            if (!quoteRequestActive(playerId, request, revision)) return;
            if (amount > account.balance()) { tell(player, "Insufficient balance for that banknote."); return; }
            quotes.put(playerId, QuoteBook.Kind.NOTE, null, 0, amount,
                    revision, config().settings().quoteTimeoutSeconds());
            tell(player, "Banknote preview for " + money(amount) + "; balance after: " + money(account.balance() - amount) + ". Use /goldbag confirm or /goldbag cancel.");
        }, player, "Could not read your balance.");
    }

    private void selectQuantity(Player player, Resource resource, int count, boolean max, QuoteBook.Kind kind) {
        player.closeInventory();
        if (kind == QuoteBook.Kind.DEPOSIT) {
            if (max) count = inventory.count(player.getInventory(), Material.matchMaterial(resource.material()));
            quoteDeposit(player, resource, count);
        } else quoteWithdrawal(player, resource, count, max);
    }

    private void startExactWithdrawalPrompt(Player player, Resource resource, QuoteBook.Kind kind) {
        player.closeInventory();
        if (kind == QuoteBook.Kind.DEPOSIT) {
            beginExactPrompt(player, resource, true);
        } else beginExactPrompt(player, resource, false);
    }

    private void beginExactPrompt(Player player, Resource resource, boolean deposit) {
        beginPrompt(player, new StringPrompt() {
            @Override public String getPromptText(ConversationContext context) { return "Enter an exact whole item count for " + resource.id() + ", or cancel:"; }
            @Override public org.bukkit.conversations.Prompt acceptInput(ConversationContext context, String input) {
                if (input == null || input.trim().equalsIgnoreCase("cancel")) { tell(player, "Quantity selection cancelled."); return null; }
                try {
                    int count = Integer.parseInt(input.trim());
                    if (count <= 0) throw new NumberFormatException();
                    if (deposit) quoteDeposit(player, resource, count); else quoteWithdrawal(player, resource, count, false);
                    return null;
                } catch (NumberFormatException error) { tell(player, "Enter a positive whole number."); return this; }
            }
        });
    }

    public void confirm(Player player) {
        QuoteBook.Quote quote = quotes.current(player.getUniqueId()).orElse(null);
        if (quote == null || quote.catalogRevision() != quotes.catalogRevision()) { tell(player, "Your quote expired; request a new preview."); return; }
        if (!gameModeAllowed(player)) { cancel(player); tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (quote.kind() == QuoteBook.Kind.NOTE) {
            if (!permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
            quotes.remove(player.getUniqueId(), quote.id());
            invalidateQuoteRequest(player.getUniqueId());
            issueNote(player, quote.amount()); return;
        }
        if (quote.kind() == QuoteBook.Kind.DEPOSIT && !permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
        if (quote.kind() == QuoteBook.Kind.WITHDRAW && !permissions.has(player, "goldbag.withdraw", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
        quotes.remove(player.getUniqueId(), quote.id());
        invalidateQuoteRequest(player.getUniqueId());
        if (quote.kind() == QuoteBook.Kind.DEPOSIT) executeDeposit(player, quote); else executeWithdrawal(player, quote);
    }

    public void cancel(Player player) {
        QuoteBook.Quote quote = quotes.current(player.getUniqueId()).orElse(null);
        if (quote == null) { invalidateQuoteRequest(player.getUniqueId()); tell(player, "There is no active GoldBag quote."); return; }
        String permission = quote.kind() == QuoteBook.Kind.DEPOSIT ? "goldbag.deposit" : quote.kind() == QuoteBook.Kind.WITHDRAW ? "goldbag.withdraw" : "goldbag.note";
        if (!permissions.has(player, permission, "goldpurse.use")) { tell(player, message("permission", "You do not have permission.")); return; }
        quotes.remove(player.getUniqueId(), quote.id());
        invalidateQuoteRequest(player.getUniqueId());
        tell(player, message("cancelled", "GoldBag operation cancelled."));
    }

    private UUID beginQuoteRequest(UUID player, long revision) { return quoteRequests.begin(player, revision); }

    private void invalidateQuoteRequest(UUID player) { quoteRequests.invalidate(player); }

    private boolean quoteRequestActive(UUID player, UUID request, long revision) { return quoteRequests.active(player, request, revision, quotes.catalogRevision()); }

    public String message(String key, String fallback, Object... replacements) {
        String value = activeConfig == null ? fallback : activeConfig.message(key, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            value = value.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        return value;
    }

    private void executeDeposit(Player player, QuoteBook.Quote quote) {
        List<MaterialCount> materialPlan = depositPlan(quote);
        java.util.Map<Material, Integer> removals = new java.util.LinkedHashMap<>();
        for (MaterialCount entry : materialPlan) removals.merge(entry.material(), entry.count(), Integer::sum);
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = inventory.planRemoval(player.getInventory(), removals); }
        catch (RuntimeException error) { tell(player, "Inventory changed; nothing was deposited."); return; }
        if (!player.isOnline() || player.isDead()) { tell(player, "Inventory changed; nothing was deposited."); return; }
        UUID playerId = player.getUniqueId();
        UUID op = UUID.randomUUID();
        if (!guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
        String payload = (quote.payload() == null ? quote.material() + ":" + quote.count() : quote.payload()) + "|" + physicalPlan.evidence();
        CompletableFuture<SqliteStore.Receipt> operation;
        try {
            operation = coordinator().execute(op, playerId, SqliteStore.Kind.DEPOSIT, quote.amount(), payload, null, this::runMain,
                    new ExchangeCoordinator.InventoryPort() {
                        @Override public boolean ready() {
                            return player.isOnline() && !player.isDead()
                                    && gameModeAllowed(player)
                                    && permissions.has(player, "goldbag.deposit", "goldpurse.use")
                                    && quote.catalogRevision() == quotes.catalogRevision()
                                    && physicalPlan.ready(player.getInventory());
                        }
                        @Override public void apply() { physicalPlan.apply(player.getInventory()); }
                    });
        } catch (RuntimeException error) {
            unguard(playerId, op);
            tell(player, ChatColor.RED + "Deposit is unresolved; no inventory change was applied.");
            return;
        }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> tell(player, "Deposited " + quote.count() + " for " + money(quote.amount()) + "."),
                "Deposit was cancelled or is unresolved; operation " + op + "."));
    }

    private void executeWithdrawal(Player player, QuoteBook.Quote quote) {
        Material material = Material.matchMaterial(quote.material());
        ItemStack item = new ItemStack(material, quote.count());
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = inventory.planAddition(player.getInventory(), item); }
        catch (RuntimeException error) { tell(player, "Inventory changed; nothing was withdrawn."); return; }
        if (!player.isOnline() || player.isDead()) { tell(player, "Inventory changed; nothing was withdrawn."); return; }
        UUID playerId = player.getUniqueId();
        UUID op = UUID.randomUUID();
        if (!guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
        String payload = quote.material() + ":" + quote.count() + "|" + physicalPlan.evidence();
        CompletableFuture<SqliteStore.Receipt> operation;
        try {
            operation = coordinator().execute(op, playerId, SqliteStore.Kind.WITHDRAW, quote.amount(), payload, null, this::runMain,
                    new ExchangeCoordinator.InventoryPort() {
                        @Override public boolean ready() {
                            return player.isOnline() && !player.isDead()
                                    && gameModeAllowed(player)
                                    && permissions.has(player, "goldbag.withdraw", "goldpurse.use")
                                    && quote.catalogRevision() == quotes.catalogRevision()
                                    && physicalPlan.ready(player.getInventory());
                        }
                        @Override public void apply() { physicalPlan.apply(player.getInventory()); }
                    });
        } catch (RuntimeException error) {
            unguard(playerId, op);
            tell(player, ChatColor.RED + "Withdrawal is unresolved; no inventory change was applied.");
            return;
        }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> tell(player, "Withdrew " + quote.count() + " for " + money(quote.amount()) + "."),
                "Withdrawal was cancelled or is unresolved; operation " + op + "."));
    }

    public void issueNote(Player player, long amount) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "Banknotes are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.note", "goldpurse.use") || !config().settings().banknotesEnabled()) { tell(player, ChatColor.RED + "Banknotes are unavailable."); return; }
        UUID note = UUID.randomUUID(), op = UUID.randomUUID();
        ItemStack noteItem = createNote(note, amount);
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = inventory.planAddition(player.getInventory(), noteItem); }
        catch (RuntimeException error) { tell(player, "Inventory changed; no banknote was issued."); return; }
        UUID playerId = player.getUniqueId();
        if (!guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
        String payload = "command|" + physicalPlan.evidence();
        CompletableFuture<SqliteStore.Receipt> operation;
        try {
            operation = coordinator().execute(op, playerId, SqliteStore.Kind.NOTE_ISSUE, amount, payload, note, this::runMain,
                    new ExchangeCoordinator.InventoryPort() {
                        @Override public boolean ready() {
                            return player.isOnline() && !player.isDead()
                                    && gameModeAllowed(player)
                                    && permissions.has(player, "goldbag.note", "goldpurse.use")
                                    && config().settings().banknotesEnabled()
                                    && physicalPlan.ready(player.getInventory());
                        }
                        @Override public void apply() { physicalPlan.apply(player.getInventory()); }
                    });
        } catch (RuntimeException error) {
            unguard(playerId, op);
            tell(player, ChatColor.RED + "Banknote issuance is unresolved; no inventory change was applied.");
            return;
        }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> tell(player, "Banknote issued for " + money(amount) + "."),
                "Banknote issuance was cancelled or is unresolved; operation " + op + "."));
    }

    private int capacity(Player player, Material material) {
        int slots = 0; for (int slot = 0; slot < 36; slot++) { ItemStack item = player.getInventory().getItem(slot); if (item == null || item.getType() == Material.AIR) slots += material.getMaxStackSize(); else if (item.getType() == material && inventory.isPlain(item, material)) slots += material.getMaxStackSize() - item.getAmount(); } return slots;
    }

    private List<MaterialCount> depositPlan(QuoteBook.Quote quote) {
        List<MaterialCount> result = new ArrayList<>();
        if (quote.payload() == null) { result.add(new MaterialCount(Material.matchMaterial(quote.material()), quote.count())); return result; }
        for (String entry : quote.payload().split(";")) { String[] parts = entry.split("=", 2); if (parts.length != 2) throw new IllegalArgumentException("Malformed deposit quote"); result.add(new MaterialCount(Material.matchMaterial(parts[0]), Integer.parseInt(parts[1]))); }
        return result;
    }
    private boolean sameNote(ItemStack item, UUID noteId) {
        return item != null && item.getType() == Material.PAPER && item.getAmount() > 0 && item.hasItemMeta()
                && noteId.toString().equals(item.getItemMeta().getPersistentDataContainer().get(noteKey, PersistentDataType.STRING));
    }
    private boolean gameModeAllowed(Player player) {
        org.bukkit.GameMode mode = player.getGameMode();
        return (mode != org.bukkit.GameMode.CREATIVE || config().settings().allowCreative())
                && (mode != org.bukkit.GameMode.SPECTATOR || config().settings().allowSpectator());
    }
    private record MaterialCount(Material material, int count) { }

    private void cancelPrepared(UUID op, String reason) { if (healthy) coordinator().cancel(op, reason).exceptionally(error -> null); }

    public void showBalance(Player viewer, UUID id) {
        if (id.equals(viewer.getUniqueId()) && !permissions.has(viewer, "goldbag.balance", "goldpurse.use")) { tell(viewer, message("permission", "You do not have permission.")); return; }
        submit(() -> store().account(id).orElse(null), account -> {
            if (account == null) tell(viewer, "No GoldBag account exists.");
            else tell(viewer, message("balance", "Balance: {amount}", "amount", money(account.balance())));
        }, viewer, "Could not read balance.");
    }
    public void showTop(Player player, int page) {
        if (!permissions.has(player, "goldbag.top", "goldpurse.use")) { tell(player, message("permission", "You do not have permission.")); return; }
        submit(() -> store().top(page), accounts -> menus.openTop(player, accounts, page), player, "Could not read leaderboard.");
    }
    public void showRates(org.bukkit.command.CommandSender sender, String material) {
        try {
            if (material == null) { for (Resource resource : config().catalog().resources()) tell(sender, resource.id() + ": deposit " + money(resource.depositPrice()) + ", withdrawal " + money(resource.withdrawPrice())); }
            else { Resource r = config().catalog().require(material); tell(sender, r.id() + ": deposit " + money(r.depositPrice()) + ", withdrawal " + money(r.withdrawPrice())); }
        } catch (RuntimeException e) { tell(sender, ChatColor.RED + e.getMessage()); }
    }

    public <T> void submit(java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> success, Player player, String failure) {
        submit(task, success, (CommandSender) player, failure);
    }
    public <T> void submit(java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> success, CommandSender sender, String failure) {
        String configuredFailure = failure.startsWith("Could not") ? message("storage-unavailable", failure) : failure;
        try { storageExecutor.submit(task).whenComplete((value, error) -> runMain(() -> { if (error != null) { if (sender != null) tell(sender, ChatColor.RED + configuredFailure + " " + rootMessage(error)); } else success.accept(value); })); }
        catch (RuntimeException error) { if (sender != null) tell(sender, ChatColor.RED + configuredFailure); }
    }
    private void finishOperation(UUID playerId, Player player, UUID operation, Throwable error, Runnable success, String failure) {
        Runnable completion = () -> {
            unguard(playerId, operation);
            if (error != null) tell(player, ChatColor.RED + failure);
            else success.run();
        };
        try { runMain(completion); }
        catch (Throwable schedulerError) {
            unguard(playerId, operation);
            getLogger().warning("Could not schedule GoldBag operation completion " + operation + "; durable state remains authoritative.");
        }
    }

    private void runMain(Runnable action) { Bukkit.getScheduler().runTask(this, action); }
    private String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? "" : current.getMessage(); }
    public void tell(org.bukkit.command.CommandSender sender, String message) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message)); }
}
