package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoldBagPluginCompletionTest {
    @Test
    void staleCompletionCannotReleaseNewerOperationGuard() {
        UUID player = UUID.randomUUID();
        UUID staleOperation = UUID.randomUUID();
        UUID currentOperation = UUID.randomUUID();
        Map<UUID, UUID> guards = new HashMap<>();
        guards.put(player, currentOperation);

        GoldBagPlugin.releaseGuard(guards, player, staleOperation);

        assertEquals(currentOperation, guards.get(player));

        GoldBagPlugin.releaseGuard(guards, player, currentOperation);

        assertFalse(guards.containsKey(player));
    }
}
