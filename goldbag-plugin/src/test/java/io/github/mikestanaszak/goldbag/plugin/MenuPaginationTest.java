package io.github.mikestanaszak.goldbag.plugin;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuPaginationTest {
    @Test
    void resourceNextPageExistsOnlyAfterTheFirst45Entries() {
        assertFalse(MenuService.hasNextResourcePage(1, 45));
        assertTrue(MenuService.hasNextResourcePage(1, 46));
        assertFalse(MenuService.hasNextResourcePage(2, 46));
    }

    @Test
    void topLookaheadHandlesExactTenTwentyAndPartialTotals() {
        assertFalse(MenuService.hasNextTopPage(page(10, 2)));

        assertTrue(MenuService.hasNextTopPage(page(20, 2)));
        assertFalse(MenuService.hasNextTopPage(page(20, 3)));

        assertTrue(MenuService.hasNextTopPage(page(11, 2)));
        assertFalse(MenuService.hasNextTopPage(page(11, 3)));
    }

    private static List<SqliteStore.Account> page(int total, int page) {
        List<SqliteStore.Account> accounts = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            accounts.add(new SqliteStore.Account(UUID.randomUUID(), "player" + index, index, index));
        }
        int from = (page - 1) * 10;
        return from >= accounts.size() ? List.of() : accounts.subList(from, Math.min(from + 10, accounts.size()));
    }
}
