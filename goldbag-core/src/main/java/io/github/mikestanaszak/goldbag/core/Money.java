package io.github.mikestanaszak.goldbag.core;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;

/** Exact, non-negative currency values represented as cents. */
public final class Money {
    private Money() { }

    public static long parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        String value = text.trim();
        if (!value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Amount must be a non-negative decimal with at most two places");
        }
        int point = value.indexOf('.');
        String whole = point < 0 ? value : value.substring(0, point);
        String fraction = point < 0 ? "" : value.substring(point + 1);
        if (fraction.length() == 1) {
            fraction += "0";
        }
        BigInteger cents = new BigInteger(whole).multiply(BigInteger.valueOf(100))
                .add(fraction.isEmpty() ? BigInteger.ZERO : new BigInteger(fraction));
        if (cents.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Amount is too large");
        }
        return cents.longValue();
    }

    public static long positive(String text) {
        long value = parse(text);
        if (value == 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return value;
    }

    public static String format(long cents) {
        if (cents < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        return (cents / 100) + "." + String.format(Locale.ROOT, "%02d", cents % 100);
    }
}
