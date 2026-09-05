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
    private Inventory inventory;

    public MenuHolder(UUID playerId, Screen screen) { this(playerId, screen, 1); }
    public MenuHolder(UUID playerId, Screen screen, int page) { this.playerId = playerId; this.screen = screen; this.page = page; }
    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public Screen screen() { return screen; }
    public int page() { return page; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
