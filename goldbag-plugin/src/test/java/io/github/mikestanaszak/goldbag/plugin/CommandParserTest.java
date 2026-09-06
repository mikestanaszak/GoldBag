package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    @Test
    void parsesCountAndMaxWithoutAcceptingMalformedAmounts() {
        CommandParser.Command command = CommandParser.parse(new String[]{"withdraw", "diamond", "max"});
        assertEquals(CommandParser.Action.WITHDRAW, command.action());
        assertEquals("diamond", command.material());
        assertTrue(command.max());
        assertThrows(IllegalArgumentException.class,
                () -> CommandParser.parse(new String[]{"withdraw", "diamond", "1.0"}));
    }

    @Test
    void requiresReasonForAdministrativeChanges() {
        assertThrows(IllegalArgumentException.class,
                () -> CommandParser.parse(new String[]{"admin", "give", "Alice", "1.00"}));
        CommandParser.Command command = CommandParser.parse(
                new String[]{"admin", "give", "Alice", "1.00", "event", "reward"});
        assertEquals(List.of("event", "reward"), command.reasonWords());
    }

    @Test
    void parsesLegacyWithdrawAsNoteAmount() {
        assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(new String[]{"withdraw", "2.50"}));
        CommandParser.Command command = CommandParser.parseLegacyWithdraw("2.50");
        assertEquals(CommandParser.Action.NOTE, command.action());
        assertEquals(250, command.amount());
    }
}
