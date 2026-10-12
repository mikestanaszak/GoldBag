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
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final Map<UUID, Deque<WithdrawalRequest>> withdrawalQueues = new HashMap<>();
    private PluginConfig activeConfig;
    private StorageExecutor storageExecutor;
    private SqliteStore store;
    private ExchangeCoordinator coordinator;
    private MenuService menus;
    private DepositDrafts drafts;
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
            drafts = new DepositDrafts(this, storageExecutor, getDataFolder().toPath());
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

    @Override public void onDisable() {
        healthy = false;
        quotes.invalidateCatalog(quotes.catalogRevision() + 1);
        withdrawalQueues.clear();
        closeDraftsOnDisable();
        closeStorage();
        clearGuards();
    }

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
    static boolean confirmationMatches(MenuHolder holder, QuoteBook.Quote quote, long currentRevision) {
        return holder != null && holder.screen() == MenuHolder.Screen.CONFIRM
                && quote != null && holder.quoteId() != null
                && holder.playerId().equals(quote.player())
                && holder.quoteId().equals(quote.id())
                && holder.catalogRevision() == currentRevision
                && quote.catalogRevision() == currentRevision;
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
                withdrawalQueues.clear();
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
    @EventHandler public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)
                || holder.screen() != MenuHolder.Screen.DEPOSIT
                || !holder.playerId().equals(event.getPlayer().getUniqueId()) || drafts == null) return;
        drafts.open((Player) event.getPlayer(), event.getInventory(), holder.sessionId());
        menus.refreshDepositSummary((Player) event.getPlayer());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        quotes.remove(playerId, quotes.current(playerId).map(QuoteBook.Quote::id).orElse(UUID.randomUUID()));
        invalidateQuoteRequest(playerId);
        withdrawalQueues.remove(playerId);
        DepositDrafts.Draft draft = drafts == null ? null : drafts.find(playerId);
        if (draft != null) {
            drafts.markClosing(draft);
            if (!draft.inFlight()) returnDraft(player, draft);
            else getLogger().warning("GoldBag deposit draft " + draft.sessionId() + " is abandoned while operation " + draft.operation() + " is in flight; retain sidecar for recovery.");
        }
        unguard(playerId);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
        if (player == null) return;
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            if (holder.screen() == MenuHolder.Screen.WITHDRAW) {
                withdrawalQueues.remove(player.getUniqueId());
                invalidateQuoteRequest(player.getUniqueId());
            }
            if (holder.screen() == MenuHolder.Screen.DEPOSIT && drafts != null) {
                DepositDrafts.Draft draft = drafts.find(player, event.getInventory());
                if (draft != null) {
                    drafts.markClosing(draft);
                    if (!draft.inFlight()) returnDraft(player, draft);
                }
            }
            if (holder.screen() == MenuHolder.Screen.CONFIRM && holder.quoteId() != null) {
                quotes.remove(player.getUniqueId(), holder.quoteId());
            }
        }
    }

    private static final class WithdrawalRequest {
        private final UUID session;
        private final Resource resource;
        private int count;
        private final boolean clamp;

        private WithdrawalRequest(UUID session, Resource resource, int count, boolean clamp) {
            this.session = session; this.resource = resource; this.count = count; this.clamp = clamp;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && guarded(((Player) event.getWhoClicked()).getUniqueId())) {
            if (event.getInventory().getHolder() instanceof MenuHolder guardedHolder
                    && guardedHolder.screen() == MenuHolder.Screen.WITHDRAW
                    && event.getRawSlot() >= 0 && event.getRawSlot() < 45) {
                event.setCancelled(true);
                handleDirectWithdrawal(event, (Player) event.getWhoClicked(), guardedHolder);
            } else event.setCancelled(true);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder) || !(event.getWhoClicked() instanceof Player)) return;
        MenuHolder holder = (MenuHolder) event.getInventory().getHolder();
        if (!holder.playerId().equals(event.getWhoClicked().getUniqueId()) || event.getRawSlot() < 0) { event.setCancelled(true); return; }
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (collectsAcrossMenu(event.getClick(), event.getAction())) { event.setCancelled(true); return; }
        if ((holder.screen() == MenuHolder.Screen.DEPOSIT || holder.screen() == MenuHolder.Screen.WITHDRAW)
                && holder.catalogRevision() != quotes.catalogRevision()) {
            event.setCancelled(true);
            tell(player, "This menu expired after a catalog reload.");
            closeNextTick(player);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (holder.screen() == MenuHolder.Screen.DEPOSIT) {
            DepositDrafts.Draft draft = drafts == null ? null : drafts.find(player, event.getView().getTopInventory());
            if (draft == null && topSize == 54 && drafts != null) draft = drafts.open(player, event.getView().getTopInventory(), holder.sessionId());
            if (draft != null && draft.inFlight()) { event.setCancelled(true); return; }
            if (draft != null && !draft.baselineReady()) { event.setCancelled(true); return; }
            if (slot < 45) {
                event.setCancelled(false);
                if (draft != null) drafts.scheduleSnapshot(draft, player);
                menus.refreshDepositSummary(player);
                return;
            }
            if (slot >= topSize) {
                event.setCancelled(false);
                if (draft != null) drafts.scheduleSnapshot(draft, player);
                menus.refreshDepositSummary(player);
                return;
            }
            event.setCancelled(true);
        } else {
            event.setCancelled(true);
            if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot >= topSize
                    && allowsWithdrawalInventoryClick(event.getClick(), event.getAction())) {
                event.setCancelled(false);
                return;
            }
        }
        if (holder.screen() == MenuHolder.Screen.WITHDRAW && handleDirectWithdrawal(event, player, holder)) return;
        if ((holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 49)
                || (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 49)
                || (holder.screen() == MenuHolder.Screen.TOP && slot == 22)) {
            closeNextTick(player);
        } else if (holder.screen() == MenuHolder.Screen.MAIN) {
            if (slot == 12) {
                if (!permissions.has(player, "goldbag.deposit", "goldpurse.use")) tell(player, message("permission", "You do not have permission."));
                else menus.openDeposit(player);
            } else if (slot == 14) {
                if (!permissions.has(player, "goldbag.withdraw", "goldpurse.use")) tell(player, message("permission", "You do not have permission."));
                else menus.openWithdraw(player, 1);
            }
            else if (slot == 15) { closeNextTick(player); startPayPrompt(player); } else if (slot == 16) { closeNextTick(player); startNotePrompt(player); }
            else if (slot == 22) { if (!permissions.has(player, "goldbag.top", "goldpurse.use")) tell(player, message("permission", "You do not have permission.")); else showTop(player, 1); }
            else if (slot == 10) { if (!permissions.has(player, "goldbag.balance", "goldpurse.use")) tell(player, message("permission", "You do not have permission.")); else showBalance(player, player.getUniqueId()); }
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot < 45) {
            // Native staging chest slots are editable; only the Deposit control is virtual.
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 45) {
            menus.openMain(player);
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 48) {
            menus.openWithdraw(player, 1);
        } else if (holder.screen() == MenuHolder.Screen.DEPOSIT && slot == 52) {
            startStagedDeposit(player, event.getView().getTopInventory(), holder);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot < 45) {
            // Resource icons are handled directly by handleDirectWithdrawal above.
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 48) {
            menus.openDeposit(player);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 51) {
            menus.openMain(player);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 45 && holder.page() > 1) {
            menus.openWithdraw(player, holder.page() - 1);
        } else if (holder.screen() == MenuHolder.Screen.WITHDRAW && slot == 53 && holder.hasNext()) {
            menus.openWithdraw(player, holder.page() + 1);
        } else if (holder.screen() == MenuHolder.Screen.TOP && slot == 18 && holder.page() > 1) {
            showTop(player, holder.page() - 1);
        } else if (holder.screen() == MenuHolder.Screen.TOP && slot == 26 && holder.hasNext()) {
            showTop(player, holder.page() + 1);
        } else if (holder.screen() == MenuHolder.Screen.CONFIRM) {
            QuoteBook.Quote current = quotes.current(player.getUniqueId()).orElse(null);
            if (!confirmationMatches(holder, current, quotes.catalogRevision())) {
                tell(player, "This confirmation expired; request a new preview.");
                closeNextTick(player);
            } else if (slot == 11) { confirm(player, holder.quoteId()); closeNextTick(player); }
            else if (slot == 15) { cancel(player, holder.quoteId()); closeNextTick(player); }
        }
    }

    static boolean allowsWithdrawalInventoryClick(ClickType click, InventoryAction action) {
        return (click == ClickType.LEFT || click == ClickType.RIGHT)
                && action != InventoryAction.MOVE_TO_OTHER_INVENTORY
                && action != InventoryAction.COLLECT_TO_CURSOR;
    }

    static boolean collectsAcrossMenu(ClickType click, InventoryAction action) {
        return click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    /** Handles resource-icon clicks as immediate withdrawals. */
    private boolean handleDirectWithdrawal(InventoryClickEvent event, Player player, MenuHolder holder) {
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) return false;
        int index = (holder.page() - 1) * 45 + event.getRawSlot();
        if (index < 0 || index >= holder.options().size()) return true;
        Resource resource;
        try { resource = config().catalog().require(holder.options().get(index)); }
        catch (RuntimeException error) { tell(player, "This menu expired after a catalog reload."); return true; }
        int count;
        if (event.getClick() == ClickType.LEFT) count = 1;
        else if (event.getClick() == ClickType.RIGHT) count = 16;
        else if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) count = 64;
        else return true;
        if (guarded(player.getUniqueId())) queueWithdrawal(player, holder, resource, count, count > 1);
        else quoteWithdrawal(player, resource, count, false, count > 1, holder.sessionId());
        return true;
    }

    private void queueWithdrawal(Player player, MenuHolder holder, Resource resource, int count, boolean clamp) {
        if (resource == null || count < 1) return;
        synchronized (withdrawalQueues) {
            Deque<WithdrawalRequest> queue = withdrawalQueues.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            WithdrawalRequest last = queue.peekLast();
            if (last != null && last.session.equals(holder.sessionId()) && last.resource.material().equals(resource.material()) && last.clamp == clamp
                    && last.count + count <= config().settings().maxItemsPerTransaction()) {
                last.count += count;
            } else if (queue.size() < 64) {
                queue.addLast(new WithdrawalRequest(holder.sessionId(), resource, Math.min(count, config().settings().maxItemsPerTransaction()), clamp));
            }
        }
    }

    private void drainWithdrawalQueue(Player player) {
        if (player == null || guarded(player.getUniqueId()) || !player.isOnline()) return;
        WithdrawalRequest request;
        synchronized (withdrawalQueues) {
            Deque<WithdrawalRequest> queue = withdrawalQueues.get(player.getUniqueId());
            if (queue == null || queue.isEmpty()) return;
            MenuHolder holder = player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder
                    ? (MenuHolder) player.getOpenInventory().getTopInventory().getHolder() : null;
            if (holder == null || holder.screen() != MenuHolder.Screen.WITHDRAW || !holder.sessionId().equals(queue.peekFirst().session)) {
                withdrawalQueues.remove(player.getUniqueId());
                return;
            }
            request = queue.removeFirst();
            if (queue.isEmpty()) withdrawalQueues.remove(player.getUniqueId());
        }
        quoteWithdrawal(player, request.resource, request.count, false, request.clamp || request.count > 1, request.session);
    }

    private void startStagedDeposit(Player player, Inventory chest, MenuHolder holder) {
        if (!permissions.has(player, "goldbag.deposit", "goldpurse.use") || !gameModeAllowed(player)) {
            tell(player, ChatColor.RED + "GoldBag deposits are unavailable.");
            return;
        }
        DepositDrafts.Draft draft = drafts == null ? null : drafts.find(player, chest);
        if (draft == null || !draft.sessionId().equals(holder.sessionId())) return;
        if (draft.inFlight()) return;
        int limit = config().settings().maxItemsPerTransaction();
        Map<Material, Integer> removals = new LinkedHashMap<>();
        Map<Material, Resource> eligible = new HashMap<>();
        for (Resource resource : config().catalog().resources()) {
            Material material = Material.matchMaterial(resource.material());
            if (resource.depositEnabled() && material != null) eligible.put(material, resource);
        }
        long amount = 0;
        int total = 0;
        try {
            for (int slot = 0; slot < DepositDrafts.STAGED_SLOTS && total < limit; slot++) {
                ItemStack item = chest.getItem(slot);
                Resource resource = item == null ? null : eligible.get(item.getType());
                if (resource == null || !inventory.isPlain(item, item.getType())) continue;
                int selected = Math.min(item.getAmount(), limit - total);
                removals.merge(item.getType(), selected, Integer::sum);
                amount = Math.addExact(amount, config().catalog().depositValue(resource.material(), selected));
                total += selected;
            }
        } catch (ArithmeticException error) {
            tell(player, "The staged deposit is too large to quote safely.");
            return;
        }
        if (removals.isEmpty()) {
            tell(player, "There are no eligible ordinary resources staged for deposit.");
            return;
        }
        InventoryAdapter.StagedPlan physicalPlan;
        try { physicalPlan = inventory.planStagedRemoval(chest, player.getInventory(), removals); }
        catch (RuntimeException error) { tell(player, "The staged chest changed; nothing was deposited."); return; }
        final DepositDrafts.Draft stagedDraft = draft;
        final InventoryAdapter.StagedPlan stagedPlan = physicalPlan;
        final long depositAmount = amount;
        final int depositedCount = total;
        UUID playerId = player.getUniqueId();
        UUID operation = UUID.randomUUID();
        if (!guard(playerId, operation)) return;
        drafts.markInFlight(stagedDraft, operation);
        drafts.persistSnapshot(stagedDraft, player).whenComplete((ignored, snapshotError) -> runMain(() -> {
            if (snapshotError != null) { failStagedDeposit(player, stagedDraft, operation, "Deposit draft could not be saved; no items were consumed."); return; }
            storageExecutor.submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")))
                    .whenComplete((account, accountError) -> runMain(() -> {
                        if (accountError != null) { failStagedDeposit(player, stagedDraft, operation, "Could not read your balance. " + rootMessage(accountError)); return; }
                        if (depositAmount > config().settings().maxBalance() - account.balance()) { failStagedDeposit(player, stagedDraft, operation, "That deposit would exceed the maximum balance."); return; }
                        String payload = "session=" + holder.sessionId() + "|op=" + operation + "|staged|" + stagedPlan.evidence();
                        CompletableFuture<SqliteStore.Receipt> operationFuture;
                        try {
                            operationFuture = coordinator().execute(operation, playerId, SqliteStore.Kind.DEPOSIT, depositAmount, payload, null, this::runMain,
                                    new ExchangeCoordinator.InventoryPort() {
                                        @Override public boolean ready() {
                                            Inventory current = player.getOpenInventory().getTopInventory();
                                            return player.isOnline() && !player.isDead() && current == chest
                                                    && current.getHolder() instanceof MenuHolder currentHolder
                                                    && holder.sessionId().equals(currentHolder.sessionId())
                                                    && holder.catalogRevision() == quotes.catalogRevision()
                                                    && gameModeAllowed(player)
                                                    && permissions.has(player, "goldbag.deposit", "goldpurse.use")
                                                    && stagedPlan.ready(chest, player.getInventory());
                                        }
                                        @Override public void apply() { stagedPlan.apply(chest, player.getInventory()); player.saveData(); }
                                    });
                        } catch (RuntimeException error) { failStagedDeposit(player, stagedDraft, operation, "Deposit is unresolved; no inventory change was applied."); return; }
                        operationFuture.whenComplete((receipt, error) -> runMain(() -> finishStagedDeposit(player, stagedDraft, operation, depositAmount, depositedCount, error)));
                    }));
        }));
    }

    private void failStagedDeposit(Player player, DepositDrafts.Draft draft, UUID operation, String message) {
        drafts.clearInFlight(draft);
        unguard(player.getUniqueId(), operation);
        tell(player, message);
        if (draft.closing()) returnDraft(player, draft);
    }

    private void finishStagedDeposit(Player player, DepositDrafts.Draft draft, UUID operation,
                                     long amount, int count, Throwable error) {
        if (error != null) {
            storageExecutor.submit(() -> store().pending().stream().anyMatch(pending -> pending.id().equals(operation)))
                    .whenComplete((pending, lookupError) -> runMain(() -> {
                        unguard(player.getUniqueId(), operation);
                        if (lookupError != null || Boolean.TRUE.equals(pending)) {
                            getLogger().warning("Staged deposit " + operation + " is unresolved; draft sidecar retained.");
                            tell(player, ChatColor.RED + "Deposit was cancelled or is unresolved; operation " + operation + ".");
                        } else {
                            drafts.clearInFlight(draft);
                            tell(player, ChatColor.RED + "Deposit was cancelled; no staged items were consumed.");
                            if (draft.closing()) returnDraft(player, draft);
                        }
                        drainWithdrawalQueue(player);
                    }));
            return;
        }
        drafts.clearInFlight(draft);
        unguard(player.getUniqueId(), operation);
        tell(player, "Deposited " + count + " for " + money(amount) + ".");
        menus.refreshBalance(player);
        menus.refreshDepositSummary(player);
        if (draft.closing()) returnDraft(player, draft);
        else drafts.scheduleSnapshot(draft, player);
        drainWithdrawalQueue(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (guarded(player.getUniqueId())) { event.setCancelled(true); return; }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (!holder.playerId().equals(player.getUniqueId())) { event.setCancelled(true); return; }
        boolean exchangeMenu = holder.screen() == MenuHolder.Screen.DEPOSIT || holder.screen() == MenuHolder.Screen.WITHDRAW;
        int topSize = event.getView().getTopInventory().getSize();
        if (exchangeMenu && event.getRawSlots().stream().allMatch(slot -> slot >= topSize)) return;
        if (holder.screen() != MenuHolder.Screen.DEPOSIT) { event.setCancelled(true); return; }
        DepositDrafts.Draft draft = drafts == null ? null : drafts.find(player, event.getView().getTopInventory());
        if (draft == null && drafts != null) draft = drafts.open(player, event.getView().getTopInventory(), holder.sessionId());
        if (draft != null && draft.inFlight()) { event.setCancelled(true); return; }
        if (draft != null && !draft.baselineReady()) { event.setCancelled(true); return; }
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 45 && slot < topSize)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        if (draft != null) drafts.scheduleSnapshot(draft, player);
        menus.refreshDepositSummary(player);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (guarded(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (guarded(event.getPlayer().getUniqueId())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (guarded(event.getPlayer().getUniqueId())) { event.setCancelled(true); return; }
        if (!shouldHandleInteraction(event)) return;
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

    static boolean shouldHandleInteraction(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) return false;
        return !event.isCancelled() || event.getAction() == Action.RIGHT_CLICK_AIR;
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
        if (guarded(player.getUniqueId())) { tell(player, message("busy", "GoldBag is temporarily unavailable.")); return; }
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
            QuoteBook.Quote quote = quotes.put(playerId, QuoteBook.Kind.DEPOSIT, resource.material(), count, amount,
                    revision, config().settings().quoteTimeoutSeconds());
            executeImmediate(player, quote);
        }, player, "Could not read your balance.");
    }

    public void quoteDepositAll(Player player) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        if (guarded(player.getUniqueId())) { tell(player, message("busy", "GoldBag is temporarily unavailable.")); return; }
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
            QuoteBook.Quote quote = quotes.put(playerId, QuoteBook.Kind.DEPOSIT, "*", totalItems, totalAmount,
                    revision, config().settings().quoteTimeoutSeconds(), payload);
            executeImmediate(player, quote);
        }, player, "Could not read your balance.");
    }

    public void startPayPrompt(Player player) {
        if (!permissions.has(player, "goldbag.pay", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        beginPrompt(player, new StringPrompt() {
            @Override public String getPromptText(ConversationContext context) { return "Enter recipient and amount (for example: Alice 2.50), or cancel:"; }
            @Override public org.bukkit.conversations.Prompt acceptInput(ConversationContext context, String input) {
                if (input == null || input.trim().equalsIgnoreCase("cancel")) { promptTell(player, "Payment cancelled."); return null; }
                String[] parts = input.trim().split("\\s+", 3);
                if (parts.length != 2) { promptTell(player, "Use: <player|uuid> <amount>"); return this; }
                String[] commandArgs = new String[]{"pay", parts[0], parts[1]};
                try { CommandParser.parse(commandArgs); }
                catch (RuntimeException error) { promptTell(player, ChatColor.RED + error.getMessage()); return this; }
                runMain(() -> commands.onCommand(player, getCommand("goldbag"), "goldbag", commandArgs));
                return null;
            }
        });
    }

    public void startNotePrompt(Player player) {
        if (!permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        beginPrompt(player, new StringPrompt() {
            @Override public String getPromptText(ConversationContext context) { return "Enter banknote amount, or cancel:"; }
            @Override public org.bukkit.conversations.Prompt acceptInput(ConversationContext context, String input) {
                if (input == null || input.trim().equalsIgnoreCase("cancel")) { promptTell(player, "Banknote cancelled."); return null; }
                String[] commandArgs = new String[]{"note", input.trim()};
                try { CommandParser.parse(commandArgs); }
                catch (RuntimeException error) { promptTell(player, ChatColor.RED + error.getMessage()); return this; }
                runMain(() -> commands.onCommand(player, getCommand("goldbag"), "goldbag", commandArgs));
                return null;
            }
        });
    }

    private void beginPrompt(Player player, StringPrompt prompt) {
        new ConversationFactory(this).withLocalEcho(false).withTimeout(60).withFirstPrompt(prompt).buildConversation(player).begin();
    }

    public void quoteWithdrawal(Player player, Resource resource, int count, boolean max) {
        quoteWithdrawal(player, resource, count, max, false);
    }

    public void quoteWithdrawal(Player player, Resource resource, int count, boolean max, boolean clamp) {
        quoteWithdrawal(player, resource, count, max, clamp, null);
    }

    private void quoteWithdrawal(Player player, Resource resource, int count, boolean max, boolean clamp, UUID expectedSession) {
        if (!gameModeAllowed(player)) { tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (!permissions.has(player, "goldbag.withdraw", "goldpurse.use")) { tell(player, ChatColor.RED + "You do not have permission."); return; }
        UUID operation = UUID.randomUUID();
        if (!guard(player.getUniqueId(), operation)) { tell(player, message("busy", "GoldBag is temporarily unavailable.")); return; }
        final boolean chooseMax = max;
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        storageExecutor.submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")))
                .whenComplete((account, error) -> runMain(() -> {
                    if (error != null) { unguard(playerId, operation); tell(player, ChatColor.RED + "Could not read your balance. " + rootMessage(error)); drainWithdrawalQueue(player); return; }
                    if (!quoteRequestActive(playerId, request, revision)) { unguard(playerId, operation); drainWithdrawalQueue(player); return; }
                    if (expectedSession != null && !withdrawalSessionOpen(player, expectedSession)) {
                        unguard(playerId, operation);
                        withdrawalQueues.remove(playerId);
                        return;
                    }
                    int maximum = config().catalog().maximumWithdrawal(resource.material(), account.balance(), capacity(player, Material.matchMaterial(resource.material())), config().settings().maxItemsPerTransaction());
                    if (!chooseMax && count > maximum && !clamp) { unguard(playerId, operation); tell(player, "That quantity exceeds your balance, capacity, or transaction limit."); drainWithdrawalQueue(player); return; }
                    int selected = chooseMax ? maximum : clamp ? Math.min(count, maximum) : count;
                    if (selected < 1) { unguard(playerId, operation); tell(player, "Insufficient balance or inventory capacity."); drainWithdrawalQueue(player); return; }
                    long amount = config().catalog().withdrawalCost(resource.material(), selected);
                    QuoteBook.Quote quote = quotes.put(playerId, QuoteBook.Kind.WITHDRAW, resource.material(), selected, amount,
                            revision, config().settings().quoteTimeoutSeconds());
                    if (!quotes.remove(playerId, quote.id())) { unguard(playerId, operation); drainWithdrawalQueue(player); return; }
                    invalidateQuoteRequest(playerId);
                    executeWithdrawal(player, quote, operation);
                }));
    }

    private boolean withdrawalSessionOpen(Player player, UUID session) {
        if (player == null || session == null) return false;
        Inventory top = player.getOpenInventory().getTopInventory();
        return top.getHolder() instanceof MenuHolder holder
                && holder.screen() == MenuHolder.Screen.WITHDRAW
                && holder.sessionId().equals(session)
                && holder.playerId().equals(player.getUniqueId());
    }

    private void executeImmediate(Player player, QuoteBook.Quote quote) {
        if (!quotes.remove(player.getUniqueId(), quote.id())) {
            tell(player, "This GoldBag operation is no longer available.");
            return;
        }
        invalidateQuoteRequest(player.getUniqueId());
        if (quote.kind() == QuoteBook.Kind.DEPOSIT) executeDeposit(player, quote);
        else if (quote.kind() == QuoteBook.Kind.WITHDRAW) executeWithdrawal(player, quote);
    }

    public void quoteNote(Player player, long amount) {
        if (!gameModeAllowed(player) || !permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "Banknotes are unavailable."); return; }
        final long revision = quotes.catalogRevision();
        final UUID request = beginQuoteRequest(player.getUniqueId(), revision);
        final UUID playerId = player.getUniqueId();
        submit(() -> store().account(playerId).orElseThrow(() -> new IllegalStateException("Account is not initialized")), account -> {
            if (!quoteRequestActive(playerId, request, revision)) return;
            if (amount > account.balance()) { tell(player, "Insufficient balance for that banknote."); return; }
            QuoteBook.Quote quote = quotes.put(playerId, QuoteBook.Kind.NOTE, null, 0, amount,
                    revision, config().settings().quoteTimeoutSeconds());
            menus.openConfirmation(player, quote, account.balance());
        }, player, "Could not read your balance.");
    }

    public void confirm(Player player) {
        confirm(player, null);
        closeConfirmationNextTick(player);
    }

    private void confirm(Player player, UUID expectedQuoteId) {
        QuoteBook.Quote quote = quotes.current(player.getUniqueId()).orElse(null);
        if (quote == null || quote.catalogRevision() != quotes.catalogRevision()) { tell(player, "Your quote expired; request a new preview."); return; }
        if (expectedQuoteId != null && !expectedQuoteId.equals(quote.id())) {
            tell(player, "This confirmation expired; request a new preview.");
            return;
        }
        if (!gameModeAllowed(player)) { cancel(player); tell(player, ChatColor.RED + "GoldBag exchanges are unavailable in your current game mode."); return; }
        if (quote.kind() == QuoteBook.Kind.NOTE) {
            if (!permissions.has(player, "goldbag.note", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
            if (!quotes.remove(player.getUniqueId(), quote.id())) { tell(player, "This confirmation was already handled."); return; }
            invalidateQuoteRequest(player.getUniqueId());
            issueNote(player, quote.amount()); return;
        }
        if (quote.kind() == QuoteBook.Kind.DEPOSIT && !permissions.has(player, "goldbag.deposit", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
        if (quote.kind() == QuoteBook.Kind.WITHDRAW && !permissions.has(player, "goldbag.withdraw", "goldpurse.use")) { tell(player, ChatColor.RED + "You no longer have permission."); return; }
        if (!quotes.remove(player.getUniqueId(), quote.id())) { tell(player, "This confirmation was already handled."); return; }
        invalidateQuoteRequest(player.getUniqueId());
        if (quote.kind() == QuoteBook.Kind.DEPOSIT) executeDeposit(player, quote); else executeWithdrawal(player, quote);
    }

    public void cancel(Player player) {
        cancel(player, null);
        closeConfirmationNextTick(player);
    }

    private void cancel(Player player, UUID expectedQuoteId) {
        QuoteBook.Quote quote = quotes.current(player.getUniqueId()).orElse(null);
        if (quote == null) { invalidateQuoteRequest(player.getUniqueId()); tell(player, "There is no active GoldBag quote."); return; }
        if (expectedQuoteId != null && !expectedQuoteId.equals(quote.id())) {
            tell(player, "This confirmation expired; request a new preview.");
            return;
        }
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
        executeDeposit(player, quote, null, null, null);
    }

    private void executeDeposit(Player player, QuoteBook.Quote quote,
                                InventoryAdapter.Plan suppliedPlan, String suppliedEvidence) {
        executeDeposit(player, quote, suppliedPlan, suppliedEvidence, null);
    }

    private void executeDeposit(Player player, QuoteBook.Quote quote,
                                InventoryAdapter.Plan suppliedPlan, String suppliedEvidence,
                                UUID reservedOperation) {
        List<MaterialCount> materialPlan;
        try { materialPlan = depositPlan(quote); }
        catch (RuntimeException error) {
            if (reservedOperation != null) unguard(player.getUniqueId(), reservedOperation);
            tell(player, "Inventory changed; nothing was deposited.");
            return;
        }
        java.util.Map<Material, Integer> removals = new java.util.LinkedHashMap<>();
        for (MaterialCount entry : materialPlan) removals.merge(entry.material(), entry.count(), Integer::sum);
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = suppliedPlan == null ? inventory.planRemoval(player.getInventory(), removals) : suppliedPlan; }
        catch (RuntimeException error) { if (reservedOperation != null) unguard(player.getUniqueId(), reservedOperation); tell(player, "Inventory changed; nothing was deposited."); return; }
        if (!player.isOnline() || player.isDead()) { if (reservedOperation != null) unguard(player.getUniqueId(), reservedOperation); tell(player, "Inventory changed; nothing was deposited."); return; }
        UUID playerId = player.getUniqueId();
        UUID op = reservedOperation == null ? UUID.randomUUID() : reservedOperation;
        if (reservedOperation == null && !guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
        String payload = (quote.payload() == null ? quote.material() + ":" + quote.count() : quote.payload()) + "|"
                + (suppliedEvidence == null ? physicalPlan.evidence() : suppliedEvidence);
        CompletableFuture<SqliteStore.Receipt> operation;
        try {
            operation = coordinator().execute(op, playerId, SqliteStore.Kind.DEPOSIT, quote.amount(), payload, null, this::runMain,
                    new ExchangeCoordinator.InventoryPort() {
                        @Override public boolean ready() {
                            return player.isOnline() && !player.isDead()
                                    && gameModeAllowed(player)
                                    && permissions.has(player, "goldbag.deposit", "goldpurse.use")
                                    && quote.catalogRevision() == quotes.catalogRevision()
                                    && physicalPlan.ready(player);
                        }
                        @Override public void apply() { physicalPlan.apply(player); }
                    });
        } catch (RuntimeException error) {
            unguard(playerId, op);
            tell(player, ChatColor.RED + "Deposit is unresolved; no inventory change was applied.");
            return;
        }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> { tell(player, "Deposited " + quote.count() + " for " + money(quote.amount()) + "."); menus.refreshBalance(player); },
                "Deposit was cancelled or is unresolved; operation " + op + "."));
    }

    private void executeWithdrawal(Player player, QuoteBook.Quote quote) { executeWithdrawal(player, quote, null); }

    private void executeWithdrawal(Player player, QuoteBook.Quote quote, UUID reservedOperation) {
        Material material = Material.matchMaterial(quote.material());
        ItemStack item = new ItemStack(material, quote.count());
        InventoryAdapter.Plan physicalPlan;
        try { physicalPlan = inventory.planAddition(player.getInventory(), item); }
        catch (RuntimeException error) { if (reservedOperation != null) { unguard(player.getUniqueId(), reservedOperation); drainWithdrawalQueue(player); } tell(player, "Inventory changed; nothing was withdrawn."); return; }
        if (!player.isOnline() || player.isDead()) { if (reservedOperation != null) { unguard(player.getUniqueId(), reservedOperation); drainWithdrawalQueue(player); } tell(player, "Inventory changed; nothing was withdrawn."); return; }
        UUID playerId = player.getUniqueId();
        UUID op = reservedOperation == null ? UUID.randomUUID() : reservedOperation;
        if (reservedOperation == null && !guard(playerId, op)) { tell(player, "A GoldBag operation is already in progress."); return; }
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
                                    && physicalPlan.ready(player);
                        }
                        @Override public void apply() { physicalPlan.apply(player); }
                    });
        } catch (RuntimeException error) {
            unguard(playerId, op);
            drainWithdrawalQueue(player);
            tell(player, ChatColor.RED + "Withdrawal is unresolved; no inventory change was applied.");
            return;
        }
        operation.whenComplete((receipt, error) -> finishOperation(playerId, player, op, error,
                () -> { tell(player, "Withdrew " + quote.count() + " for " + money(quote.amount()) + "."); menus.refreshBalance(player); },
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
                                    && physicalPlan.ready(player);
                        }
                        @Override public void apply() { physicalPlan.apply(player); }
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
            drainWithdrawalQueue(player);
        };
        try { runMain(completion); }
        catch (Throwable schedulerError) {
            unguard(playerId, operation);
            getLogger().warning("Could not schedule GoldBag operation completion " + operation + "; durable state remains authoritative.");
        }
    }

    private void closeNextTick(Player player) {
        Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline()) player.closeInventory(); });
    }

    private void returnDraft(Player player, DepositDrafts.Draft draft) {
        if (draft == null || draft.inFlight() || !drafts.beginReturn(draft)) return;
        CompletableFuture<Void> saved = drafts.persistSnapshot(draft, player);
        boolean returned = returnStagedItems(player, draft);
        saved.whenComplete((ignored, saveError) -> {
            if (saveError != null || !returned) {
                getLogger().warning("Deposit draft " + draft.sessionId() + " sidecar retained for manual recovery.");
                drafts.remove(draft);
                return;
            }
            drafts.deleteSidecar(draft).whenComplete((deleted, deleteError) -> {
                if (deleteError != null) getLogger().warning("Deposit draft " + draft.sessionId() + " sidecar retained for recovery: " + rootMessage(deleteError));
                drafts.remove(draft);
            });
        });
    }

    private boolean returnStagedItems(Player player, DepositDrafts.Draft draft) {
        Inventory chest = draft.menu();
        boolean complete = true;
        for (int slot = 0; slot < Math.min(DepositDrafts.STAGED_SLOTS, chest.getSize()); slot++) {
            ItemStack item = chest.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            chest.setItem(slot, null);
            Map<Integer, ItemStack> leftovers;
            try { leftovers = player.getInventory().addItem(item.clone()); }
            catch (RuntimeException error) { complete = false; continue; }
            for (ItemStack leftover : leftovers.values()) {
                if (leftover == null || leftover.getType() == Material.AIR) continue;
                try { player.getWorld().dropItemNaturally(player.getLocation(), leftover); }
                catch (RuntimeException error) { complete = false; getLogger().warning("Could not return staged item for " + player.getUniqueId() + "; sidecar retained."); }
            }
        }
        try { player.saveData(); }
        catch (RuntimeException error) { complete = false; getLogger().warning("Could not save returned staged items for " + player.getUniqueId() + "; sidecar retained."); }
        return complete;
    }

    private void closeDraftsOnDisable() {
        if (drafts == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            DepositDrafts.Draft draft = drafts.find(player.getUniqueId());
            if (draft == null) continue;
            drafts.markClosing(draft);
            if (draft.inFlight()) {
                getLogger().warning("Deposit draft " + draft.sessionId() + " is abandoned during disable while operation " + draft.operation() + " is in flight; retain sidecar for manual recovery.");
                drafts.persistSnapshot(draft, player);
            } else if (drafts.beginReturn(draft)) {
                drafts.persistSnapshot(draft, player);
                returnStagedItems(player, draft);
                drafts.remove(draft);
            }
        }
    }
    private void closeConfirmationNextTick(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)
                || holder.screen() != MenuHolder.Screen.CONFIRM
                || holder.quoteId() == null) return;
        UUID quoteId = holder.quoteId();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) return;
            Inventory current = player.getOpenInventory().getTopInventory();
            if (current.getHolder() instanceof MenuHolder currentHolder
                    && currentHolder.screen() == MenuHolder.Screen.CONFIRM
                    && quoteId.equals(currentHolder.quoteId())) player.closeInventory();
        });
    }
    private void promptTell(Player player, String message) {
        runMain(() -> tell(player, message));
    }
    void runMainLater(Runnable action, long ticks) { Bukkit.getScheduler().runTaskLater(this, action, ticks); }
    private void runMain(Runnable action) { Bukkit.getScheduler().runTask(this, action); }
    private String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? "" : current.getMessage(); }
    public void tell(org.bukkit.command.CommandSender sender, String message) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message)); }
}
