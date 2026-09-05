package io.github.mikestanaszak.goldbag.core;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    @Test
    void defaultsUseExactPricesAndCapacityLimits() {
        Catalog catalog = Catalog.defaults();
        assertEquals(18, catalog.resources().size());
        assertEquals(5000, catalog.depositValue("raw_iron", 25));
        assertEquals(5000, catalog.withdrawalCost("DIAMOND", 1));
        assertEquals(4, catalog.maximumWithdrawal("diamond", 25000, 4, 64));
        assertEquals("RAW_IRON", catalog.require("raw iron").material());
    }

    @Test
    void rejectsUnknownDisabledAndInvalidCounts() {
        Catalog catalog = Catalog.defaults();
        assertThrows(IllegalArgumentException.class, () -> catalog.require("missing"));
        assertThrows(IllegalArgumentException.class, () -> catalog.depositValue("iron", 1));
        assertThrows(IllegalArgumentException.class, () -> catalog.depositValue("raw_iron", 0));
    }

    @Test
    void loadsResourcesAndDerivesStorageBlock() {
        String yaml = "resources:\n"
                + "  raw_iron:\n"
                + "    material: RAW_IRON\n"
                + "    aliases: [iron_raw]\n"
                + "    deposit-enabled: true\n"
                + "    withdraw-enabled: true\n"
                + "    deposit-price: '2.00'\n"
                + "    withdraw-price: '2.00'\n"
                + "    storage-block: RAW_IRON_BLOCK\n"
                + "    items-per-block: 9\n";
        Catalog catalog = Catalog.load(new StringReader(yaml));
        assertEquals(1000, catalog.depositValue("iron_raw", 5));
        assertEquals(1800, catalog.withdrawalCost("raw_iron_block", 1));
    }

    @Test
    void rejectsDuplicateKeysUnknownKeysProhibitedItemsAndBadRatios() {
        assertThrows(IllegalArgumentException.class, () -> Catalog.load(new StringReader(
                "resources:\n  x:\n    material: RAW_IRON\n    deposit-price: '1.00'\n    deposit-price: '2.00'\n")));
        assertThrows(IllegalArgumentException.class, () -> Catalog.load(new StringReader(
                "resources:\n  x:\n    material: IRON_INGOT\n    deposit-price: '1.00'\n")));
        assertThrows(IllegalArgumentException.class, () -> Catalog.load(new StringReader(
                "resources:\n  x:\n    material: RAW_IRON\n    deposit-price: '1.00'\n    typo: true\n")));
        assertThrows(IllegalArgumentException.class, () -> Catalog.load(new StringReader(
                "resources:\n  x:\n    material: RAW_IRON\n    deposit-price: '1.00'\n    storage-block: RAW_IRON_BLOCK\n    items-per-block: 8\n")));
    }
}
