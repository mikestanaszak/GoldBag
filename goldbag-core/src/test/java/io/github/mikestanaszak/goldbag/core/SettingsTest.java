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
        assertEquals("Gold", settings.currencyName());
        assertEquals("G", settings.currencySymbol());
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

    @Test
    void retainsDisplaySettingsAndRejectsUnsafeOrMalformedScalars() {
        Settings settings = Settings.load(new StringReader(
                "currency: {name: Coins, symbol: '$', max-balance: '10.00'}\n"
                        + "storage: {file: purse-2.db}\n"));
        assertEquals("Coins", settings.currencyName());
        assertEquals("$", settings.currencySymbol());
        assertEquals("purse-2.db", settings.databaseFile());
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("storage: null\n")));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("exchange: null\n")));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("storage: {file: C:/outside.db}\n")));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("storage: {file: 123}\n")));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("currency: {name: 123}\n")));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(new StringReader("currency: {max-balance: 1e2}\n")));
    }
}
