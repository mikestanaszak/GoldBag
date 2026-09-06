package io.github.mikestanaszak.goldbag.plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread quote registry. It never changes balances or inventory. */
public final class QuoteBook {
    public enum Kind { DEPOSIT, WITHDRAW, NOTE }
    public record Quote(UUID id, UUID player, Kind kind, String material, int count, long amount,
                        long catalogRevision, Instant expiresAt, String payload) {
        public Quote(UUID id, UUID player, Kind kind, String material, int count, long amount,
                     long catalogRevision, Instant expiresAt) {
            this(id, player, kind, material, count, amount, catalogRevision, expiresAt, null);
        }
    }

    private final Clock clock;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private volatile long catalogRevision;

    public QuoteBook(Clock clock) { this.clock = clock == null ? Clock.systemUTC() : clock; }

    public Quote put(UUID player, Kind kind, String material, int count, long amount, long revision, int timeoutSeconds) {
        return put(player, kind, material, count, amount, revision, timeoutSeconds, null);
    }

    public Quote put(UUID player, Kind kind, String material, int count, long amount, long revision, int timeoutSeconds, String payload) {
        if (player == null || kind == null || timeoutSeconds <= 0 || count < 0 || amount < 0) throw new IllegalArgumentException("Invalid quote");
        Quote quote = new Quote(UUID.randomUUID(), player, kind, material, count, amount, revision,
                clock.instant().plus(Duration.ofSeconds(timeoutSeconds)), payload);
        quotes.put(player, quote);
        return quote;
    }

    public Optional<Quote> current(UUID player) {
        Quote quote = quotes.get(player);
        if (quote == null) return Optional.empty();
        if (!quote.expiresAt().isAfter(clock.instant())) { quotes.remove(player, quote); return Optional.empty(); }
        return Optional.of(quote);
    }

    public boolean remove(UUID player, UUID quoteId) {
        Quote quote = quotes.get(player);
        return quote != null && quote.id().equals(quoteId) && quotes.remove(player, quote);
    }

    public int expireBefore(Instant instant) {
        int removed = 0;
        for (Map.Entry<UUID, Quote> entry : quotes.entrySet()) {
            if (!entry.getValue().expiresAt().isAfter(instant) && quotes.remove(entry.getKey(), entry.getValue())) removed++;
        }
        return removed;
    }

    public void invalidateCatalog(long revision) { quotes.clear(); catalogRevision = revision; }
    public long catalogRevision() { return catalogRevision; }
    public int size() { return quotes.size(); }
}
