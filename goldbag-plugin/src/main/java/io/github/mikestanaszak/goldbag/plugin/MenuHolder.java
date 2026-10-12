package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;
import java.util.List;

/** Plugin-owned holder prevents spoofing menus by title or lore. */
public final class MenuHolder implements InventoryHolder {
    public enum Screen { MAIN, DEPOSIT, WITHDRAW, TOP, PREVIEW, CONFIRM }
    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final Screen screen;
    private final int page;
    private final String selection;
    private final QuoteBook.Kind selectionKind;
    private final UUID quoteId;
    private final long balanceBefore;
    private final long catalogRevision;
    private final List<String> options;
    private final boolean hasNext;
    private Inventory inventory;

    public MenuHolder(UUID playerId, Screen screen) { this(playerId, screen, 1, null, null, null, 0, 0, List.of(), false); }
    public MenuHolder(UUID playerId, Screen screen, int page) { this(playerId, screen, page, null, null, null, 0, 0, List.of(), false); }
    public MenuHolder(UUID playerId, Screen screen, String selection, QuoteBook.Kind selectionKind) { this(playerId, screen, 1, selection, selectionKind, null, 0, 0, List.of(), false); }
    public MenuHolder(UUID playerId, Screen screen, String selection, QuoteBook.Kind selectionKind, long catalogRevision) { this(playerId, screen, 1, selection, selectionKind, null, catalogRevision, 0, List.of(), false); }
    public MenuHolder(UUID playerId, Screen screen, int page, long catalogRevision, List<String> options) { this(playerId, screen, page, null, null, null, catalogRevision, 0, options, false); }
    public MenuHolder(UUID playerId, Screen screen, int page, long catalogRevision, List<String> options, boolean hasNext) { this(playerId, screen, page, null, null, null, catalogRevision, 0, options, hasNext); }
    public static MenuHolder confirmation(UUID playerId, QuoteBook.Quote quote, long balanceBefore) {
        if (quote == null) throw new IllegalArgumentException("quote");
        return new MenuHolder(playerId, Screen.CONFIRM, 1, quote.material(), quote.kind(), quote.id(), quote.catalogRevision(), balanceBefore, List.of(), false);
    }
    private MenuHolder(UUID playerId, Screen screen, int page, String selection, QuoteBook.Kind selectionKind,
                       UUID quoteId, long catalogRevision, long balanceBefore, List<String> options, boolean hasNext) {
        this.playerId = playerId; this.screen = screen; this.page = page; this.selection = selection;
        this.selectionKind = selectionKind; this.quoteId = quoteId; this.catalogRevision = catalogRevision;
        this.balanceBefore = balanceBefore; this.options = List.copyOf(options); this.hasNext = hasNext;
    }
    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public Screen screen() { return screen; }
    public int page() { return page; }
    public String selection() { return selection; }
    public QuoteBook.Kind selectionKind() { return selectionKind; }
    public UUID quoteId() { return quoteId; }
    public long catalogRevision() { return catalogRevision; }
    public long balanceBefore() { return balanceBefore; }
    public List<String> options() { return options; }
    public boolean hasNext() { return hasNext; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
