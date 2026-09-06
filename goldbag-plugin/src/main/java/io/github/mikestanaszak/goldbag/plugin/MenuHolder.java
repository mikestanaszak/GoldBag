package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Plugin-owned holder prevents spoofing menus by title or lore. */
public final class MenuHolder implements InventoryHolder {
    public enum Screen { MAIN, DEPOSIT, WITHDRAW, TOP, PREVIEW }
    private final UUID sessionId = UUID.randomUUID();
    private final UUID playerId;
    private final Screen screen;
    private final int page;
    private final String selection;
    private final QuoteBook.Kind selectionKind;
    private Inventory inventory;

    public MenuHolder(UUID playerId, Screen screen) { this(playerId, screen, 1, null, null); }
    public MenuHolder(UUID playerId, Screen screen, int page) { this(playerId, screen, page, null, null); }
    public MenuHolder(UUID playerId, Screen screen, String selection, QuoteBook.Kind selectionKind) { this(playerId, screen, 1, selection, selectionKind); }
    private MenuHolder(UUID playerId, Screen screen, int page, String selection, QuoteBook.Kind selectionKind) { this.playerId = playerId; this.screen = screen; this.page = page; this.selection = selection; this.selectionKind = selectionKind; }
    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public Screen screen() { return screen; }
    public int page() { return page; }
    public String selection() { return selection; }
    public QuoteBook.Kind selectionKind() { return selectionKind; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
