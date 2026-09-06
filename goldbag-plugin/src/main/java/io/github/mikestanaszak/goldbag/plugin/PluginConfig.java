package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Catalog;
import io.github.mikestanaszak.goldbag.core.Settings;
import org.bukkit.Material;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Immutable active configuration snapshot. A failed reload never replaces it. */
public record PluginConfig(Settings settings, Catalog catalog, Map<String, String> messages) {
    public PluginConfig {
        messages = Map.copyOf(messages);
        validateMaterials(catalog);
    }

    public static PluginConfig load(Path directory) throws IOException {
        Path config = directory.resolve("config.yml");
        Path resources = directory.resolve("resources.yml");
        Path messages = directory.resolve("messages.yml");
        if (!Files.isRegularFile(config) || !Files.isRegularFile(resources) || !Files.isRegularFile(messages)) throw new IOException("GoldBag configuration files are incomplete");
        try (Reader configReader = Files.newBufferedReader(config, StandardCharsets.UTF_8);
             Reader resourceReader = Files.newBufferedReader(resources, StandardCharsets.UTF_8)) {
            Map<String, String> values = messages(messages);
            return new PluginConfig(Settings.load(configReader), Catalog.load(resourceReader), values);
        }
    }

    private static Map<String, String> messages(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (RuntimeException error) { throw new IOException("Invalid messages.yml", error); }
        if (!(root instanceof Map<?, ?>)) throw new IOException("messages.yml must contain a mapping");
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String) || ((String) entry.getKey()).isBlank()) throw new IOException("messages.yml keys and values must be strings");
            values.put((String) entry.getKey(), (String) entry.getValue());
        }
        if (values.isEmpty()) throw new IOException("messages.yml must not be empty");
        return values;
    }

    public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }

    private static void validateMaterials(Catalog catalog) {
        for (io.github.mikestanaszak.goldbag.core.Resource resource : catalog.resources()) {
            Material material = Material.matchMaterial(resource.material());
            if (material == null || !material.isItem() || material == Material.AIR) {
                throw new IllegalArgumentException("Unsupported Bukkit material in catalog: " + resource.material());
            }
            String block = switch (resource.material()) {
                case "COAL", "REDSTONE", "LAPIS_LAZULI", "RAW_COPPER", "RAW_IRON", "RAW_GOLD", "DIAMOND", "EMERALD" -> resource.material().equals("LAPIS_LAZULI") ? "LAPIS_BLOCK" : resource.material() + "_BLOCK";
                default -> null;
            };
            if (block != null && Material.matchMaterial(block) == null) throw new IllegalArgumentException("Unsupported Bukkit storage block: " + block);
        }
    }
}
