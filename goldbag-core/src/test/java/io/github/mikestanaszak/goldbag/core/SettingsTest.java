package io.github.mikestanaszak.goldbag.core;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test
    void defaultsMatchApprovedConfiguration() {
        Settings settings = Settings.defaults();
        assertEquals(100_000_000_000L, settings.maxBalance());
        assertEquals(30, settings.quoteTimeoutSeconds());
        assertEquals(2304, settings.maxItemsPerTransaction());
        assertEquals("goldbag.db", settings.databaseFile());
    }

    @Test
    void parsesNestedConfigAndRejectsUnknownKeys() {
        String yaml = "config-version: 1\n"
                + "storage: {file: purse.db}\n"
                + "currency: {max-balance: '10.00'}\n"
                + "exchange: {quote-timeout-seconds: 45, max-items-per-transaction: 10, allow-creative: true, allow-spectator: true}\n"
                + "menu: {sneak-right-click-raw-gold: true}\n"
                + "banknotes: {enabled: false}\n"
                + "compatibility: {legacy-aliases: false}\n";
        Settings settings = Settings.load(new StringReader(yaml));
        assertEquals(1000, settings.maxBalance());
        assertEquals("purse.db", settings.databaseFile());
        assertTrue(settings.allowCreative());
        assertFalse(settings.banknotesEnabled());
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("wat: true\n")));
    }

    @Test
    void packagedDefaultsLoad() throws Exception {
        try (var config = getClass().getResourceAsStream("/defaults/config.yml")) {
            assertNotNull(config);
            assertEquals(Settings.defaults(), Settings.load(new java.io.InputStreamReader(config)));
        }
        try (var resources = getClass().getResourceAsStream("/defaults/resources.yml")) {
            assertNotNull(resources);
            assertEquals(18, Catalog.load(new java.io.InputStreamReader(resources)).resources().size());
        }
    }
}
