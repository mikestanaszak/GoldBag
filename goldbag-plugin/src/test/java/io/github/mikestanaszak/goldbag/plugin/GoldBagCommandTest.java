package io.github.mikestanaszak.goldbag.plugin;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class GoldBagCommandTest {
    @Test void aliasesDispatchTheSameStrictCommands() {
        assertEquals(CommandParser.Action.BALANCE, GoldBagCommand.parseAlias("goldbag:money", new String[0]).action());
        assertEquals(CommandParser.Action.TOP, GoldBagCommand.parseAlias("pursetop", new String[]{"2"}).action());
        assertEquals(CommandParser.Action.NOTE, GoldBagCommand.parseAlias("withdraw", new String[]{"2.50"}).action());
        assertEquals(CommandParser.Action.WITHDRAW, GoldBagCommand.parseAlias("gb", new String[]{"withdraw", "diamond", "max"}).action());
        assertEquals(CommandParser.Action.ADMIN, GoldBagCommand.parseAlias("purse", new String[]{"give", "Alice", "1.00", "reward"}).action());
        assertThrows(IllegalArgumentException.class, () -> GoldBagCommand.parseAlias("withdraw", new String[]{"2.001"}));
    }

    @Test void playerCommandsGiveConsoleAnActionableError() {
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> null);
        assertEquals("This command requires a player.", assertThrows(IllegalArgumentException.class,
                () -> GoldBagCommand.player(console)).getMessage());
    }

    @Test void helpIsDiscoverableAsACanonicalCommand() {
        assertEquals("HELP", CommandParser.parse(new String[]{"help"}).action().name());
        assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(new String[]{"help", "unknown"}));
        assertTrue(GoldBagCommand.topLevelCompletions(senderWithPermissions(true), new PermissionService(), "he").contains("help"));
    }
    @Test void commandSuggestionsRespectExplicitPermissionDenials() {
        var suggestions = GoldBagCommand.topLevelCompletions(senderWithPermissions(false), new PermissionService(), "");
        assertTrue(suggestions.contains("help"));
        assertFalse(suggestions.contains("admin"));
        assertFalse(suggestions.contains("reload"));
        assertFalse(suggestions.contains("storage"));
        assertFalse(suggestions.contains("recovery"));
    }

    private static CommandSender senderWithPermissions(boolean admin) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isPermissionSet")) return true;
                    if (method.getName().equals("hasPermission")) return !String.valueOf(args[0]).contains("admin") || admin;
                    return null;
                });
    }
}
