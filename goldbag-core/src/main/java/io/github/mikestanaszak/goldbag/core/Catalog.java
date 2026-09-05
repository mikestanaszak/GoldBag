package io.github.mikestanaszak.goldbag.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Catalog {
    private static final Set<String> PROHIBITED = Set.of(
            "IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT", "NETHERITE_INGOT",
            "IRON_NUGGET", "GOLD_NUGGET", "COPPER_NUGGET",
            "IRON_BLOCK", "GOLD_BLOCK", "COPPER_BLOCK", "NETHERITE_BLOCK");
    private static final Map<String, String> REVERSIBLE_BLOCKS = Map.of(
            "COAL", "COAL_BLOCK", "REDSTONE", "REDSTONE_BLOCK", "LAPIS_LAZULI", "LAPIS_BLOCK",
            "RAW_COPPER", "RAW_COPPER_BLOCK", "RAW_IRON", "RAW_IRON_BLOCK", "RAW_GOLD", "RAW_GOLD_BLOCK",
            "DIAMOND", "DIAMOND_BLOCK", "EMERALD", "EMERALD_BLOCK");

    private final List<Resource> resources;
    private final Map<String, Resource> lookup;

    private Catalog(List<Resource> resources, Map<String, Resource> lookup) {
        this.resources = List.copyOf(resources);
        this.lookup = Map.copyOf(lookup);
    }

    public static Catalog defaults() {
        List<Definition> definitions = new ArrayList<>();
        definitions.add(new Definition("coal", "COAL", 20, 20, true, true, "COAL_BLOCK", List.of()));
        definitions.add(new Definition("redstone", "REDSTONE", 100, 100, true, true, "REDSTONE_BLOCK", List.of()));
        definitions.add(new Definition("lapis_lazuli", "LAPIS_LAZULI", 100, 100, true, true, "LAPIS_BLOCK", List.of()));
        definitions.add(new Definition("raw_copper", "RAW_COPPER", 100, 100, true, true, "RAW_COPPER_BLOCK", List.of()));
        definitions.add(new Definition("raw_iron", "RAW_IRON", 200, 200, true, true, "RAW_IRON_BLOCK", List.of()));
        definitions.add(new Definition("raw_gold", "RAW_GOLD", 500, 500, true, true, "RAW_GOLD_BLOCK", List.of()));
        definitions.add(new Definition("diamond", "DIAMOND", 5000, 5000, true, true, "DIAMOND_BLOCK", List.of()));
        definitions.add(new Definition("emerald", "EMERALD", 2500, 2500, true, true, "EMERALD_BLOCK", List.of()));
        definitions.add(new Definition("quartz", "QUARTZ", 100, 100, true, true, null, List.of()));
        definitions.add(new Definition("amethyst_shard", "AMETHYST_SHARD", 50, 50, true, true, null, List.of()));
        return fromDefinitions(definitions);
    }

    public static Catalog load(Reader yaml) {
        Objects.requireNonNull(yaml, "yaml");
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object root;
        try {
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException | ClassCastException ex) {
            throw new IllegalArgumentException("Invalid catalog YAML", ex);
        }
        Map<String, Object> map = map(root, "catalog");
        rejectUnknown(map, Set.of("resources"), "catalog");
        Map<String, Object> rawResources = map(map.get("resources"), "resources");
        if (rawResources.isEmpty()) {
            throw new IllegalArgumentException("resources must contain at least one entry");
        }
        List<Definition> definitions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawResources.entrySet()) {
            String id = normalize(entry.getKey());
            Map<String, Object> values = map(entry.getValue(), "resource " + id);
            rejectUnknown(values, Set.of("material", "aliases", "deposit-enabled", "withdraw-enabled",
                    "deposit-price", "withdraw-price", "storage-block", "items-per-block"), "resource " + id);
            String material = string(values.get("material"), "resource " + id + ".material");
            long deposit = Money.positive(string(values.get("deposit-price"), "resource " + id + ".deposit-price"));
            long withdraw = Money.positive(string(values.get("withdraw-price"), "resource " + id + ".withdraw-price"));
            boolean depositEnabled = bool(values.getOrDefault("deposit-enabled", Boolean.TRUE), "resource " + id + ".deposit-enabled");
            boolean withdrawEnabled = bool(values.getOrDefault("withdraw-enabled", Boolean.TRUE), "resource " + id + ".withdraw-enabled");
            List<String> aliases = strings(values.getOrDefault("aliases", List.of()), "resource " + id + ".aliases");
            String block = values.containsKey("storage-block") ? normalizeMaterial(string(values.get("storage-block"), "resource " + id + ".storage-block")) : null;
            boolean hasItems = values.containsKey("items-per-block");
            if ((block == null) != !hasItems || (block != null && integer(values.get("items-per-block"), "items-per-block") != 9)) {
                throw new IllegalArgumentException("Storage blocks must specify items-per-block: 9");
            }
            if (block != null && !REVERSIBLE_BLOCKS.getOrDefault(normalizeMaterial(material), "").equals(block)) {
                throw new IllegalArgumentException("Storage block " + block + " does not match " + material);
            }
            definitions.add(new Definition(id, material, deposit, withdraw, depositEnabled, withdrawEnabled, block, aliases));
        }
        return fromDefinitions(definitions);
    }

    public List<Resource> resources() {
        return resources;
    }

    public Resource require(String materialOrAlias) {
        String key = normalize(materialOrAlias);
        Resource resource = lookup.get(key);
        if (resource == null) {
            throw new IllegalArgumentException("Unknown resource: " + materialOrAlias);
        }
        return resource;
    }

    public long depositValue(String material, int count) {
        requireCount(count);
        Resource resource = require(material);
        if (!resource.depositEnabled()) {
            throw new IllegalArgumentException("Deposits are disabled for " + material);
        }
        return multiply(resource.depositPrice(), count);
    }

    public long withdrawalCost(String material, int count) {
        requireCount(count);
        Resource resource = require(material);
        if (!resource.withdrawEnabled()) {
            throw new IllegalArgumentException("Withdrawals are disabled for " + material);
        }
        return multiply(resource.withdrawPrice(), count);
    }

    public int maximumWithdrawal(String material, long balance, int capacity, int limit) {
        Resource resource = require(material);
        if (!resource.withdrawEnabled()) {
            throw new IllegalArgumentException("Withdrawals are disabled for " + material);
        }
        if (balance < 0 || capacity < 0 || limit < 0) {
            throw new IllegalArgumentException("Balance and limits must be non-negative");
        }
        long affordable = balance / resource.withdrawPrice();
        return (int) Math.min(Math.min(affordable, capacity), limit);
    }

    static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid resource id: " + value);
        }
        return normalized;
    }

    static String normalizeMaterial(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        return normalized;
    }

    private static Catalog fromDefinitions(List<Definition> definitions) {
        List<Resource> resources = new ArrayList<>();
        Map<String, Resource> lookup = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            Resource resource = resource(definition.id, definition.material, definition.deposit, definition.withdraw,
                    definition.depositEnabled, definition.withdrawEnabled);
            add(resource, lookup, resources, definition.aliases);
            if (definition.storageBlock != null) {
                long blockDeposit = multiply(definition.deposit, 9);
                long blockWithdraw = multiply(definition.withdraw, 9);
                Resource block = resource(normalize(definition.storageBlock), definition.storageBlock, blockDeposit,
                        blockWithdraw, definition.depositEnabled, definition.withdrawEnabled);
                add(block, lookup, resources, List.of());
            }
        }
        return new Catalog(resources, lookup);
    }

    private static Resource resource(String id, String material, long deposit, long withdraw, boolean depositEnabled, boolean withdrawEnabled) {
        String normalizedMaterial = normalizeMaterial(material);
        if (PROHIBITED.contains(normalizedMaterial)) {
            throw new IllegalArgumentException("Prohibited refined material: " + material);
        }
        if (depositEnabled && withdrawEnabled && withdraw < deposit) {
            throw new IllegalArgumentException("Withdrawal price cannot be below deposit price: " + id);
        }
        return new Resource(id, normalizedMaterial, deposit, withdraw, depositEnabled, withdrawEnabled);
    }

    private static void add(Resource resource, Map<String, Resource> lookup, List<Resource> resources, List<String> aliases) {
        register(lookup, normalize(resource.id()), resource, "Duplicate resource or material: " + resource.id());
        register(lookup, normalizeMaterial(resource.material()).toLowerCase(Locale.ROOT), resource,
                "Duplicate resource or material: " + resource.id());
        resources.add(resource);
        for (String alias : aliases) {
            register(lookup, normalize(alias), resource, "Duplicate resource alias: " + alias);
        }
    }

    private static void register(Map<String, Resource> lookup, String key, Resource resource, String message) {
        Resource previous = lookup.putIfAbsent(key, resource);
        if (previous != null && previous != resource) {
            throw new IllegalArgumentException(message);
        }
    }

    private static long multiply(long price, int count) {
        try {
            return Math.multiplyExact(price, count);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Currency value overflow", ex);
        }
    }

    private static void requireCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) {
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

    private static void rejectUnknown(Map<String, Object> values, Set<String> allowed, String label) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown " + label + " key: " + key);
            }
        }
    }

    private static String string(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + label);
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(label + " must be a quoted string scalar");
        }
        return (String) value;
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

    private static List<String> strings(Object value, String label) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(label + " must be a list");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String)) {
                throw new IllegalArgumentException(label + " entries must be strings");
            }
            result.add((String) entry);
        }
        return result;
    }

    private record Definition(String id, String material, long deposit, long withdraw, boolean depositEnabled,
                              boolean withdrawEnabled, String storageBlock, List<String> aliases) { }
}
