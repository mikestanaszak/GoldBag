package io.github.mikestanaszak.goldbag.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Settings(long maxBalance, int quoteTimeoutSeconds, int maxItemsPerTransaction,
                       boolean allowCreative, boolean allowSpectator, boolean shortcutEnabled,
                       boolean banknotesEnabled, boolean legacyAliases, String databaseFile) {
    public Settings {
        if (maxBalance <= 0 || quoteTimeoutSeconds <= 0 || maxItemsPerTransaction <= 0) {
            throw new IllegalArgumentException("Settings limits must be positive");
        }
        if (databaseFile == null || databaseFile.isBlank() || databaseFile.contains("..")) {
            throw new IllegalArgumentException("databaseFile must be a simple non-empty path");
        }
    }

    public static Settings defaults() {
        return new Settings(100_000_000_000L, 30, 2304, false, false, false, true, true, "goldbag.db");
    }

    public static Settings load(Reader yaml) {
        Objects.requireNonNull(yaml, "yaml");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException | ClassCastException ex) {
            throw new IllegalArgumentException("Invalid settings YAML", ex);
        }
        Map<String, Object> root = map(loaded, "settings");
        reject(root, Set.of("config-version", "storage", "currency", "exchange", "menu", "banknotes", "compatibility"), "settings");
        int version = integer(root.getOrDefault("config-version", 1), "config-version");
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported config-version: " + version);
        }
        Map<String, Object> storage = section(root, "storage");
        Map<String, Object> currency = section(root, "currency");
        Map<String, Object> exchange = section(root, "exchange");
        Map<String, Object> menu = section(root, "menu");
        Map<String, Object> banknotes = section(root, "banknotes");
        Map<String, Object> compatibility = section(root, "compatibility");
        reject(storage, Set.of("file"), "storage");
        reject(currency, Set.of("name", "symbol", "max-balance"), "currency");
        reject(exchange, Set.of("quote-timeout-seconds", "max-items-per-transaction", "allow-creative", "allow-spectator"), "exchange");
        reject(menu, Set.of("sneak-right-click-raw-gold"), "menu");
        reject(banknotes, Set.of("enabled"), "banknotes");
        reject(compatibility, Set.of("legacy-aliases"), "compatibility");
        Settings defaults = defaults();
        if (currency.containsKey("name")) {
            nonBlank(string(currency.get("name"), "currency.name"), "currency.name");
        }
        if (currency.containsKey("symbol")) {
            nonBlank(string(currency.get("symbol"), "currency.symbol"), "currency.symbol");
        }
        long max = currency.containsKey("max-balance") ? Money.positive(string(currency.get("max-balance"), "currency.max-balance")) : defaults.maxBalance();
        int timeout = integer(exchange.getOrDefault("quote-timeout-seconds", defaults.quoteTimeoutSeconds()), "exchange.quote-timeout-seconds");
        int items = integer(exchange.getOrDefault("max-items-per-transaction", defaults.maxItemsPerTransaction()), "exchange.max-items-per-transaction");
        boolean creative = bool(exchange.getOrDefault("allow-creative", defaults.allowCreative()), "exchange.allow-creative");
        boolean spectator = bool(exchange.getOrDefault("allow-spectator", defaults.allowSpectator()), "exchange.allow-spectator");
        boolean shortcut = bool(menu.getOrDefault("sneak-right-click-raw-gold", defaults.shortcutEnabled()), "menu.sneak-right-click-raw-gold");
        boolean notes = bool(banknotes.getOrDefault("enabled", defaults.banknotesEnabled()), "banknotes.enabled");
        boolean legacy = bool(compatibility.getOrDefault("legacy-aliases", defaults.legacyAliases()), "compatibility.legacy-aliases");
        String file = storage.containsKey("file") ? string(storage.get("file"), "storage.file") : defaults.databaseFile();
        return new Settings(max, timeout, items, creative, spectator, shortcut, notes, legacy, file);
    }

    private static String string(Object value, String label) {
        if (value == null || (!(value instanceof String) && !(value instanceof Number))) {
            throw new IllegalArgumentException(label + " must be a scalar");
        }
        return String.valueOf(value);
    }

    private static void nonBlank(String value, String label) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    private static boolean bool(Object value, String label) {
        if (!(value instanceof Boolean b)) {
            throw new IllegalArgumentException(label + " must be boolean");
        }
        return b;
    }

    private static int integer(Object value, String label) {
        if (!(value instanceof Number n) || n.longValue() != n.doubleValue()) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        try {
            return Math.toIntExact(n.longValue());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(label + " is out of range", ex);
        }
    }

    private static Map<String, Object> section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (value == null) {
            return Map.of();
        }
        return map(value, name);
    }

    private static Map<String, Object> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) {
            if (value == null) {
                return Map.of();
            }
            throw new IllegalArgumentException(label + " must be a mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(label + " keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static void reject(Map<String, Object> map, Set<String> allowed, String label) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown " + label + " key: " + key);
            }
        }
    }
}
