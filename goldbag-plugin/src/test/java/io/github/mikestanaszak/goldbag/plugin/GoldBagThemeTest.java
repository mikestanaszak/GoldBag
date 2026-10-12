package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldBagThemeTest {
    @Test void allChatUsesGoldEvenForLegacyErrorsAndConfiguredColors() {
        assertEquals(ChatColor.GOLD + "[GoldBag] You do not have permission.",
                GoldBagPlugin.themedMessage(ChatColor.RED + "You do not have permission."));
        assertEquals(ChatColor.GOLD + "[GoldBag] Your treasure: G12.50",
                GoldBagPlugin.themedMessage("&bYour treasure: &lG12.50&r"));
    }

    @Test void multilineHelpAndAlreadyPrefixedCustomMessagesStayConsistent() {
        assertEquals(ChatColor.GOLD + "[GoldBag] First line\n"
                        + ChatColor.GOLD + "[GoldBag] Second line",
                GoldBagPlugin.themedMessage("&6[GoldBag] First line\r\n&cSecond line"));
    }
}
