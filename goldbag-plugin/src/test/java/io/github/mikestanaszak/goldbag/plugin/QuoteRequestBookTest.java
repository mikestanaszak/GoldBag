package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuoteRequestBookTest {
    @Test
    void cancelReloadAndNewerRequestsInvalidateDelayedCallbacks() {
        QuoteRequestBook book = new QuoteRequestBook();
        UUID player = UUID.randomUUID();
        UUID first = book.begin(player, 4);
        assertTrue(book.active(player, first, 4, 4));
        book.invalidate(player);
        assertFalse(book.active(player, first, 4, 4));
        UUID second = book.begin(player, 5);
        assertFalse(book.active(player, first, 4, 5));
        assertTrue(book.active(player, second, 5, 5));
        book.clear();
        assertFalse(book.active(player, second, 5, 5));
    }
}
