package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Resource;
import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Canonical command entry point and compatibility alias dispatcher. */
public final class GoldBagCommand implements CommandExecutor, TabCompleter {
    private final GoldBagPlugin plugin;

    public GoldBagCommand(GoldBagPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            CommandParser.Command parsed = parseAlias(label, args);
            return dispatch(sender, parsed);
        } catch (IllegalArgumentException error) {
            plugin.tell(sender, ChatColor.RED + error.getMessage());
            return true;
        } catch (RuntimeException error) {
            plugin.tell(sender, ChatColor.RED + "GoldBag is temporarily unavailable.");
            return true;
        }
    }

    private CommandParser.Command parseAlias(String label, String[] args) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.equals("balance") || lower.equals("money")) return CommandParser.parse(join(new String[]{"balance"}, args));
        if (lower.equals("pursetop")) return CommandParser.parse(join(new String[]{"top"}, args));
        if (lower.equals("withdraw") && args.length == 1) return CommandParser.parse(new String[]{"withdraw", args[0]});
        if (lower.equals("purse") && args.length > 0 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) return CommandParser.parse(join(new String[]{"admin"}, args));
        return CommandParser.parse(args);
    }

    private boolean dispatch(CommandSender sender, CommandParser.Command parsed) {
        switch (parsed.action()) {
            case MAIN:
                require(sender, "goldbag.use", "goldpurse.use"); player(sender).ifPresent(plugin.menus()::openMain); return true;
            case BALANCE:
                if (parsed.target() != null && !parsed.target().isBlank()) {
                    if (!plugin.permissions().has(sender, "goldbag.balance.others", "goldpurse.admin")) throw new IllegalArgumentException("You may not view another balance.");
                    lookup(sender, parsed.target(), true);
                } else {
                    require(sender, "goldbag.balance", "goldpurse.use"); player(sender).ifPresent(p -> plugin.showBalance(p, p.getUniqueId()));
                }
                return true;
            case RATES: require(sender, "goldbag.use", "goldpurse.use"); if (parsed.material() == null) { plugin.tell(sender, "Use /goldbag rates <material>."); } else plugin.showRates(sender, parsed.material()); return true;
            case DEPOSIT: requirePlayerPermission(sender, "goldbag.deposit", "goldpurse.use"); player(sender).ifPresent(p -> { if (parsed.all()) plugin.menus().openDeposit(p); else plugin.quoteDeposit(p, plugin.config().catalog().require(parsed.material()), parsed.count()); }); return true;
            case WITHDRAW: requirePlayerPermission(sender, "goldbag.withdraw", "goldpurse.use"); player(sender).ifPresent(p -> plugin.quoteWithdrawal(p, plugin.config().catalog().require(parsed.material()), parsed.count(), parsed.max())); return true;
            case PAY: requirePlayerPermission(sender, "goldbag.pay", "goldpurse.use"); pay(sender, parsed); return true;
            case NOTE: requirePlayerPermission(sender, "goldbag.note", "goldpurse.use"); player(sender).ifPresent(p -> { plugin.quotes().put(p.getUniqueId(), QuoteBook.Kind.NOTE, null, 0, parsed.amount(), plugin.quotes().catalogRevision() + 1, plugin.config().settings().quoteTimeoutSeconds()); plugin.tell(p, "Banknote preview for " + plugin.money(parsed.amount()) + ". Use /goldbag confirm or /goldbag cancel."); }); return true;
            case TOP: require(sender, "goldbag.top", "goldpurse.use"); player(sender).ifPresent(p -> plugin.showTop(p, parsed.page())); return true;
            case CONFIRM: player(sender).ifPresent(plugin::confirm); return true;
            case CANCEL: player(sender).ifPresent(plugin::cancel); return true;
            case ADMIN: require(sender, "goldbag.admin.balance", "goldpurse.admin"); admin(sender, parsed); return true;
            case RELOAD: require(sender, "goldbag.admin.reload", "goldpurse.admin"); plugin.reloadConfiguration(sender); return true;
            case STORAGE_STATUS: require(sender, "goldbag.admin.storage", "goldpurse.admin"); storageStatus(sender); return true;
            case STORAGE_EXPORT: require(sender, "goldbag.admin.storage", "goldpurse.admin"); export(sender); return true;
            case RECOVERY_LIST: require(sender, "goldbag.admin.storage", "goldpurse.admin"); recoveryList(sender); return true;
            case RECOVERY_RESOLVE: require(sender, "goldbag.admin.storage", "goldpurse.admin"); recoveryResolve(sender, parsed); return true;
            default: throw new IllegalArgumentException("Unsupported command");
        }
    }

    private void pay(CommandSender sender, CommandParser.Command parsed) {
        if (!(sender instanceof Player)) throw new IllegalArgumentException("Payments require a player sender.");
        Player player = (Player) sender;
        plugin.submit(() -> plugin.store().findAccount(parsed.target()).orElseThrow(() -> new IllegalArgumentException("Unknown GoldBag account")), account -> {
            if (account.id().equals(player.getUniqueId())) { plugin.tell(player, "You cannot pay yourself."); return; }
            plugin.submit(() -> plugin.store().transfer(UUID.randomUUID(), player.getUniqueId(), account.id(), parsed.amount()), receipt -> plugin.tell(player, "Paid " + account.name() + " " + plugin.money(parsed.amount()) + "."), player, "Payment failed.");
        }, player, "Recipient lookup failed.");
    }

    private void admin(CommandSender sender, CommandParser.Command parsed) {
        List<String> reason = parsed.reasonWords();
        if (reason.isEmpty()) throw new IllegalArgumentException("A reason is required.");
        UUID actor = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        plugin.submit(() -> plugin.store().findAccount(parsed.target()).orElseThrow(() -> new IllegalArgumentException("Unknown GoldBag account")), account -> {
            UUID op = UUID.randomUUID(); String why = String.join(" ", reason);
            if (parsed.subcommand().equals("set")) plugin.submit(() -> plugin.store().setBalance(op, actor, account.id(), parsed.amount(), why), receipt -> plugin.tell(sender, "Balance set to " + plugin.money(parsed.amount()) + "."), sender, "Balance update failed.");
            else { long delta = parsed.subcommand().equals("take") ? -parsed.amount() : parsed.amount(); plugin.submit(() -> plugin.store().adjust(op, actor, account.id(), delta, why), receipt -> plugin.tell(sender, "Balance adjusted by " + plugin.money(parsed.amount()) + "."), sender, "Balance update failed."); }
        }, sender, "Account lookup failed.");
    }

    private void lookup(CommandSender sender, String id, boolean other) {
        plugin.submit(() -> plugin.store().findAccount(id).orElse(null), account -> { if (account == null) plugin.tell(sender, "No GoldBag account exists."); else plugin.tell(sender, account.name() + " balance: " + plugin.money(account.balance())); }, sender, "Could not read balance.");
    }

    private void storageStatus(CommandSender sender) {
        plugin.submit(() -> plugin.store().pending(), pending -> plugin.tell(sender, "GoldBag SQLite storage is healthy; pending operations: " + pending.size()), sender, "Could not read storage status.");
    }

    private void export(CommandSender sender) {
        plugin.submit(() -> plugin.store().exportJson(), json -> { try { java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("goldbag-export.json"); Files.writeString(file, json, StandardCharsets.UTF_8); plugin.tell(sender, "GoldBag export written to " + file.getFileName()); } catch (Exception e) { plugin.tell(sender, ChatColor.RED + "Export failed: " + e.getMessage()); } }, sender, "Could not export storage.");
    }

    private void recoveryList(CommandSender sender) {
        plugin.submit(() -> plugin.store().pending(), pending -> { if (pending.isEmpty()) plugin.tell(sender, "No unresolved GoldBag operations."); else for (SqliteStore.Pending operation : pending) plugin.tell(sender, operation.id() + " " + operation.kind() + " " + plugin.money(operation.amount()) + " " + operation.state()); }, sender, "Could not read recovery records.");
    }

    private void recoveryResolve(CommandSender sender, CommandParser.Command parsed) {
        UUID operation; try { operation = UUID.fromString(parsed.target()); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Operation id must be a UUID"); }
        UUID actor = sender instanceof Player ? ((Player)sender).getUniqueId() : UUID.nameUUIDFromBytes("GoldBagConsole".getBytes(StandardCharsets.UTF_8));
        plugin.submit(() -> plugin.store().resolve(operation, parsed.max(), actor, String.join(" ", parsed.reasonWords())), receipt -> plugin.tell(sender, "Recovery " + parsed.subcommand() + " applied for " + operation + "."), sender, "Recovery resolution failed.");
    }

    private static String[] join(String[] prefix, String[] rest) { String[] all = Arrays.copyOf(prefix, prefix.length + rest.length); System.arraycopy(rest, 0, all, prefix.length, rest.length); return all; }
    private void require(CommandSender sender, String modern, String legacy) { if (!plugin.permissions().has(sender, modern, legacy)) throw new IllegalArgumentException("You do not have permission."); }
    private void requirePlayerPermission(CommandSender sender, String modern, String legacy) { require(sender, modern, legacy); if (!(sender instanceof Player)) throw new IllegalArgumentException("This command requires a player."); }
    private java.util.Optional<Player> player(CommandSender sender) { return sender instanceof Player ? java.util.Optional.of((Player) sender) : java.util.Optional.empty(); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("balance", "rates", "deposit", "withdraw", "pay", "note", "top", "confirm", "cancel", "admin", "reload", "storage", "recovery").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        return Collections.emptyList();
    }
}
