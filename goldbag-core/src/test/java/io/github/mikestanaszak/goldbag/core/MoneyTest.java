package io.github.mikestanaszak.goldbag.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void parsesAndFormatsExactCents() {
        assertEquals(200, Money.parse("2.00"));
        assertEquals(5, Money.parse("0.05"));
        assertEquals("50.00", Money.format(5000));
    }

    @Test
    void rejectsInvalidPrecisionSignExponentAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse("1.001"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("-1"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("1e2"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("92233720368547758.08"));
    }

    @Test
    void positiveRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> Money.positive("0.00"));
    }
}
