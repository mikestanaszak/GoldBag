package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginConfigTest {
    @Test
    void rejectsAirAndOtherNonItemMaterialsBeforeActivation() throws Exception {
        Path directory = Files.createTempDirectory("goldbag-config");
        try {
            copyResource(directory, "config.yml");
            copyResource(directory, "messages.yml");
            Files.writeString(directory.resolve("resources.yml"), "resources:\n  air: {material: AIR, deposit-enabled: true, withdraw-enabled: true, deposit-price: \"1.00\", withdraw-price: \"1.00\"}\n");
            assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(directory));
        } finally {
            Files.deleteIfExists(directory.resolve("config.yml"));
            Files.deleteIfExists(directory.resolve("messages.yml"));
            Files.deleteIfExists(directory.resolve("resources.yml"));
            Files.deleteIfExists(directory);
        }
    }

    private static void copyResource(Path directory, String name) throws Exception {
        try (InputStream input = PluginConfigTest.class.getResourceAsStream("/" + name)) {
            if (input == null) throw new IllegalStateException("Missing test resource " + name);
            Files.copy(input, directory.resolve(name));
        }
    }
}
