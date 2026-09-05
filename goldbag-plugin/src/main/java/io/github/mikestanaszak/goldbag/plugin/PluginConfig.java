package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.core.Catalog;
import io.github.mikestanaszak.goldbag.core.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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
        try (Reader configReader = Files.newBufferedReader(config, StandardCharsets.UTF_8);
             Reader resourceReader = Files.newBufferedReader(resources, StandardCharsets.UTF_8)) {
            YamlConfiguration messageYaml = YamlConfiguration.loadConfiguration(messages.toFile());
            Map<String, String> values = new HashMap<>();
            for (String key : messageYaml.getKeys(false)) values.put(key, messageYaml.getString(key, key));
            return new PluginConfig(Settings.load(configReader), Catalog.load(resourceReader), values);
        }
    }

    public String message(String key, String fallback) { return messages.getOrDefault(key, fallback); }

    private static void validateMaterials(Catalog catalog) {
        for (io.github.mikestanaszak.goldbag.core.Resource resource : catalog.resources()) {
            if (Material.matchMaterial(resource.material()) == null) {
                throw new IllegalArgumentException("Unsupported Bukkit material in catalog: " + resource.material());
            }
        }
    }
}
