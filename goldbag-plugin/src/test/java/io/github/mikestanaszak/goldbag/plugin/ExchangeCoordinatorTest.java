package io.github.mikestanaszak.goldbag.plugin;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeCoordinatorTest {
    @Test
    void stateMachineRequiresApplyingBeforeCompletion() {
        ExchangeCoordinator.StateMachine machine = new ExchangeCoordinator.StateMachine();
        UUID operation = UUID.randomUUID();
        assertEquals(ExchangeCoordinator.Stage.PREPARED, machine.prepare(operation));
        assertThrows(IllegalStateException.class, () -> machine.complete(operation));
        assertEquals(ExchangeCoordinator.Stage.APPLYING, machine.markApplying(operation));
        assertEquals(ExchangeCoordinator.Stage.COMPLETED, machine.complete(operation));
        assertEquals(ExchangeCoordinator.Stage.COMPLETED, machine.complete(operation));
    }

    @Test
    void cancellationOnlyAppliesBeforeInventoryMutation() {
        ExchangeCoordinator.StateMachine machine = new ExchangeCoordinator.StateMachine();
        UUID prepared = UUID.randomUUID();
        machine.prepare(prepared);
        assertEquals(ExchangeCoordinator.Stage.CANCELLED, machine.cancel(prepared));
        UUID applying = UUID.randomUUID();
        machine.prepare(applying);
        machine.markApplying(applying);
        assertThrows(IllegalStateException.class, () -> machine.cancel(applying));
    }
}
