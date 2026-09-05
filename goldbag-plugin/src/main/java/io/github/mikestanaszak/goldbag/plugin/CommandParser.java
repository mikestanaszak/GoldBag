package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Money;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Strict parser shared by the Bukkit command executor and tab completion. */
public final class CommandParser {
    public enum Action { MAIN, BALANCE, RATES, DEPOSIT, WITHDRAW, PAY, NOTE, TOP,
        ADMIN, RELOAD, STORAGE_STATUS, STORAGE_EXPORT, RECOVERY_LIST, RECOVERY_RESOLVE,
        CONFIRM, CANCEL }

    public record Command(Action action, String material, String target, long amount, int count,
                           boolean max, boolean all, int page, List<String> reasonWords,
                           String subcommand) {
        public Command {
            reasonWords = List.copyOf(reasonWords == null ? List.of() : reasonWords);
        }
    }

    private CommandParser() { }

    public static Command parse(String[] raw) {
        if (raw == null) throw new IllegalArgumentException("Arguments are required");
        List<String> args = new ArrayList<>(Arrays.asList(raw));
        if (args.isEmpty()) return new Command(Action.MAIN, null, null, 0, 0, false, false, 1, List.of(), null);
        String verb = lower(args.remove(0));
        switch (verb) {
            case "balance": return balance(args);
            case "rates": return rates(args);
            case "deposit": return deposit(args);
            case "withdraw": return withdraw(args);
            case "pay": return pay(args);
            case "note": return note(args);
            case "top": return top(args);
            case "admin": return admin(args);
            case "reload": requireNoArgs(args, "reload"); return simple(Action.RELOAD);
            case "confirm": requireNoArgs(args, "confirm"); return simple(Action.CONFIRM);
            case "cancel": requireNoArgs(args, "cancel"); return simple(Action.CANCEL);
            case "storage": return storage(args);
            case "recovery": return recovery(args);
            default: throw new IllegalArgumentException("Unknown GoldBag command: " + verb);
        }
    }

    private static Command balance(List<String> args) {
        if (args.size() > 1) throw usage("balance [player]");
        return new Command(Action.BALANCE, null, args.isEmpty() ? null : args.get(0), 0, 0, false, false, 1, List.of(), null);
    }

    private static Command rates(List<String> args) {
        if (args.size() > 1) throw usage("rates [material]");
        return new Command(Action.RATES, args.isEmpty() ? null : args.get(0), null, 0, 0, false, false, 1, List.of(), null);
    }

    private static Command deposit(List<String> args) {
        if (args.size() == 1 && lower(args.get(0)).equals("all")) {
            return new Command(Action.DEPOSIT, null, null, 0, 0, false, true, 1, List.of(), null);
        }
        if (args.size() != 2) throw usage("deposit <material> <count>");
        return new Command(Action.DEPOSIT, args.get(0), null, 0, positiveInt(args.get(1), "count"), false, false, 1, List.of(), null);
    }

    private static Command withdraw(List<String> args) {
        if (args.size() == 1) {
            // The historical /withdraw <amount> command creates a note.
            return new Command(Action.NOTE, null, null, Money.positive(args.get(0)), 0, false, false, 1, List.of(), null);
        }
        if (args.size() != 2) throw usage("withdraw <material> <count|max>");
        String count = lower(args.get(1));
        if (count.equals("max")) return new Command(Action.WITHDRAW, args.get(0), null, 0, 0, true, false, 1, List.of(), null);
        return new Command(Action.WITHDRAW, args.get(0), null, 0, positiveInt(count, "count"), false, false, 1, List.of(), null);
    }

    private static Command pay(List<String> args) {
        if (args.size() != 2) throw usage("pay <player|uuid> <amount>");
        return new Command(Action.PAY, null, args.get(0), Money.positive(args.get(1)), 0, false, false, 1, List.of(), null);
    }

    private static Command note(List<String> args) {
        if (args.size() != 1) throw usage("note <amount>");
        return new Command(Action.NOTE, null, null, Money.positive(args.get(0)), 0, false, false, 1, List.of(), null);
    }

    private static Command top(List<String> args) {
        if (args.size() > 1) throw usage("top [page]");
        return new Command(Action.TOP, null, null, 0, 0, false, false,
                args.isEmpty() ? 1 : positiveInt(args.get(0), "page"), List.of(), null);
    }

    private static Command admin(List<String> args) {
        if (args.size() < 4) throw usage("admin <give|take|set> <player|uuid> <amount> <reason>");
        String sub = lower(args.get(0));
        if (!sub.equals("give") && !sub.equals("take") && !sub.equals("set")) throw new IllegalArgumentException("Unknown admin operation");
        long amount = sub.equals("set") ? Money.parse(args.get(2)) : Money.positive(args.get(2));
        return new Command(Action.ADMIN, null, args.get(1), amount, 0,
                false, false, 1, args.subList(3, args.size()), sub);
    }

    private static Command storage(List<String> args) {
        if (args.size() != 1) throw usage("storage <status|export>");
        String sub = lower(args.get(0));
        if (sub.equals("status")) return simple(Action.STORAGE_STATUS);
        if (sub.equals("export")) return simple(Action.STORAGE_EXPORT);
        throw new IllegalArgumentException("Unknown storage operation: " + sub);
    }

    private static Command recovery(List<String> args) {
        if (args.size() == 1 && lower(args.get(0)).equals("list")) return simple(Action.RECOVERY_LIST);
        if (args.size() < 4 || !lower(args.get(0)).equals("resolve")) throw usage("recovery resolve <op> <apply|cancel> <reason>");
        String mode = lower(args.get(2));
        if (!mode.equals("apply") && !mode.equals("cancel")) throw new IllegalArgumentException("Resolution must be apply or cancel");
        return new Command(Action.RECOVERY_RESOLVE, null, args.get(1), 0, 0, mode.equals("apply"), false, 1,
                args.subList(3, args.size()), mode);
    }

    private static Command simple(Action action) { return new Command(action, null, null, 0, 0, false, false, 1, List.of(), null); }
    private static void requireNoArgs(List<String> args, String usage) { if (!args.isEmpty()) throw usage(usage); }
    private static IllegalArgumentException usage(String text) { return new IllegalArgumentException("Usage: /goldbag " + text); }
    private static String lower(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Argument is required"); return value.toLowerCase(Locale.ROOT); }
    private static int positiveInt(String value, String label) {
        try { int parsed = Integer.parseInt(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be a positive whole number"); }
    }
}
