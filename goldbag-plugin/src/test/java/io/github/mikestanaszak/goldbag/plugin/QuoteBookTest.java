package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuoteBookTest {
    @Test
    void expiresQuotesAndInvalidatesAllOnCatalogReload() {
        UUID player = UUID.randomUUID();
        QuoteBook book = new QuoteBook(Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC));
        QuoteBook.Quote quote = book.put(player, QuoteBook.Kind.DEPOSIT, "RAW_IRON", 2, 400, 7, 30);
        assertSame(quote, book.current(player).orElseThrow());
        assertTrue(book.current(player).isPresent());
        assertEquals(1, book.expireBefore(Instant.parse("2026-09-05T00:00:31Z")));
        book.invalidateCatalog(8);
        assertTrue(book.current(player).isEmpty());
        assertEquals(8, book.catalogRevision());
    }

    @Test
    void oneQuotePerPlayerReplacesOlderQuote() {
        UUID player = UUID.randomUUID();
        QuoteBook book = new QuoteBook(Clock.systemUTC());
        book.put(player, QuoteBook.Kind.DEPOSIT, "COAL", 1, 20, 1, 60);
        QuoteBook.Quote next = book.put(player, QuoteBook.Kind.WITHDRAW, "DIAMOND", 1, 5000, 1, 60);
        assertEquals(next, book.current(player).orElseThrow());
        assertEquals(1, book.size());
    }
}
