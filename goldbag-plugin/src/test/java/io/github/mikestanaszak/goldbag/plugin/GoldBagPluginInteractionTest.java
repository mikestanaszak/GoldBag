package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldBagPluginInteractionTest {
    @Test
    void preCancelledAirWithAllowedItemUseIsStillHandled() {
        PlayerInteractEvent event = new PlayerInteractEvent(
                null, Action.RIGHT_CLICK_AIR, new ItemStack(Material.PAPER), null,
                BlockFace.NORTH, EquipmentSlot.HAND);

        assertTrue(event.isCancelled(), "Spigot 1.17 marks a no-op air interaction cancelled");
        assertEquals(Event.Result.DEFAULT, event.useItemInHand());
        assertTrue(GoldBagPlugin.shouldHandleInteraction(event));
    }

    @Test
    void explicitItemDenialIsNeverHandledEvenWhenLegacyCancellationIsFalse() {
        PlayerInteractEvent event = new PlayerInteractEvent(
                null, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.PAPER), null,
                BlockFace.NORTH, EquipmentSlot.HAND);
        event.setUseInteractedBlock(Event.Result.ALLOW);
        event.setUseItemInHand(Event.Result.DENY);

        assertFalse(event.isCancelled(), "Spigot 1.17 isCancelled checks only the block-use state");
        assertFalse(GoldBagPlugin.shouldHandleInteraction(event));
    }

    @Test
    void cancelledBlockInteractionRemainsIgnored() {
        PlayerInteractEvent event = new PlayerInteractEvent(
                null, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.PAPER), null,
                BlockFace.NORTH, EquipmentSlot.HAND);

        assertTrue(event.isCancelled());
        assertFalse(GoldBagPlugin.shouldHandleInteraction(event));
    }
}
