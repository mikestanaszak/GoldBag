package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuConfirmationTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void confirmationHolderCarriesQuoteIdentityAndCatalogRevision() {
        QuoteBook book = new QuoteBook(CLOCK);
        QuoteBook.Quote quote = book.put(PLAYER, QuoteBook.Kind.WITHDRAW, "GOLD_INGOT", 16, 3200, 7, 30);

        MenuHolder holder = MenuHolder.confirmation(PLAYER, quote, 12500);

        assertEquals(MenuHolder.Screen.CONFIRM, holder.screen());
        assertEquals(quote.id(), holder.quoteId());
        assertEquals(quote.catalogRevision(), holder.catalogRevision());
        assertEquals(12500, holder.balanceBefore());
    }

    @Test
    void staleReplacementAndCatalogReloadCannotValidateOldConfirmation() {
        QuoteBook book = new QuoteBook(CLOCK);
        QuoteBook.Quote first = book.put(PLAYER, QuoteBook.Kind.DEPOSIT, "RAW_IRON", 2, 400, 7, 30);
        MenuHolder holder = MenuHolder.confirmation(PLAYER, first, 1000);
        QuoteBook.Quote replacement = book.put(PLAYER, QuoteBook.Kind.NOTE, null, 0, 500, 7, 30);

        assertFalse(GoldBagPlugin.confirmationMatches(holder, replacement, 7));
        assertTrue(GoldBagPlugin.confirmationMatches(holder, first, 7));
        assertFalse(GoldBagPlugin.confirmationMatches(holder, first, 8));
    }

    @Test
    void closingAnOlderHolderCannotRemoveReplacementAndDuplicateRemovalIsRejected() {
        QuoteBook book = new QuoteBook(CLOCK);
        QuoteBook.Quote first = book.put(PLAYER, QuoteBook.Kind.DEPOSIT, "RAW_IRON", 2, 400, 7, 30);
        MenuHolder older = MenuHolder.confirmation(PLAYER, first, 1000);
        QuoteBook.Quote replacement = book.put(PLAYER, QuoteBook.Kind.NOTE, null, 0, 500, 7, 30);

        assertFalse(book.remove(PLAYER, older.quoteId()));
        assertSame(replacement, book.current(PLAYER).orElseThrow());
        assertTrue(book.remove(PLAYER, replacement.id()));
        assertFalse(book.remove(PLAYER, replacement.id()));
    }

    @Test
    void confirmationSummaryIncludesKindAmountCountAndBalanceAfter() {
        QuoteBook book = new QuoteBook(CLOCK);
        QuoteBook.Quote quote = book.put(PLAYER, QuoteBook.Kind.DEPOSIT, "RAW_IRON", 2, 400, 7, 30);

        String summary = MenuService.confirmationSummary(quote, 1000, "$4.00", "$10.00", "$14.00");

        assertAll(
                () -> assertTrue(summary.contains("Deposit")),
                () -> assertTrue(summary.contains("2")),
                () -> assertTrue(summary.contains("RAW_IRON")),
                () -> assertTrue(summary.contains("$4.00")),
                () -> assertTrue(summary.contains("14.00")));
    }

    @Test
    void depositAllSummaryNamesEveryMaterialInItsPayload() {
        QuoteBook book = new QuoteBook(CLOCK);
        QuoteBook.Quote quote = book.put(PLAYER, QuoteBook.Kind.DEPOSIT, "*", 5, 700, 7, 30,
                "RAW_IRON=2;GOLD_INGOT=3");

        String summary = MenuService.confirmationSummary(quote, 1000, "$7.00", "$10.00", "$17.00");

        assertTrue(summary.contains("2 x RAW_IRON, 3 x GOLD_INGOT"));
    }

    @Test
    void depositMaxIsClampedToTransactionLimit() {
        assertEquals(16, MenuService.depositMaxCount(100, 16));
        assertEquals(8, MenuService.depositMaxCount(8, 16));
    }
}
