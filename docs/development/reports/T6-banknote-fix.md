# T6 banknote interaction fix

## Root cause

The live Paper 1.21.11 test showed that a banknote right-clicked into air did
not start redemption. The production handler is annotated with
`@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)`.

The target Spigot 1.17 API confirms why this skips the event. Its
`PlayerInteractEvent` constructor initializes `useInteractedBlock` to `DENY`
when `clickedBlock` is null, which makes the legacy `isCancelled()` result
true for `RIGHT_CLICK_AIR`; `useItemInHand` remains `DEFAULT`. Bukkit's
Javadoc explicitly documents that vanilla no-op air interactions are fired as
cancelled. The event is therefore filtered before `onInteract` can inspect the
banknote.

The same API has a second boundary: its deprecated `isCancelled()` check only
reflects the interacted-block result. A plugin can deny `useItemInHand` while
`isCancelled()` remains false. The fix must therefore explicitly reject
`useItemInHand() == Event.Result.DENY`, while allowing the vanilla
pre-cancelled air event and continuing to ignore cancelled block interactions.

Evidence sources:

- Spigot `PlayerInteractEvent` Javadoc:
  https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/player/PlayerInteractEvent.html
- Local target API bytecode:
  `org.bukkit.event.player.PlayerInteractEvent` from
  `spigot-api-1.17-R0.1-20210706.122143-68.jar`; constructor and
  `isCancelled()` were inspected with `javap`.
- Current Paper source keeps separate `cancelledBlock` and `cancelledItem`
  values when constructing the event in `CraftEventFactory`, then exposes
  them through `useInteractedBlock()` and `useItemInHand()`:
  https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java

## Failing regression

Added `GoldBagPluginInteractionTest` with three cases:

- pre-cancelled `RIGHT_CLICK_AIR` with allowed item use must be handled;
- explicit item-use denial must be rejected even when legacy `isCancelled()` is
  false;
- cancelled block interactions must remain ignored.

Before the production change, the focused command failed at test compilation
because the new `GoldBagPlugin.shouldHandleInteraction(PlayerInteractEvent)`
helper did not exist:

```text
mvn -B -pl goldbag-plugin -am '-Dtest=GoldBagPluginInteractionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
BUILD FAILURE
cannot find symbol: method shouldHandleInteraction(PlayerInteractEvent)
```

## Next implementation step

Remove `ignoreCancelled` only for this handler, route the event through the
small helper covered by the regression, reject explicit item-use denial, allow
the pre-cancelled air/no-denial case, and preserve the existing guard, hand,
permission, game-mode, note-PDC, and exact-slot checks.

## Implementation and focused verification

Implemented the planned change in `GoldBagPlugin.onInteract` and added the
package-private `shouldHandleInteraction` gate. The handler now receives
cancelled events, but only treats a cancelled event as actionable when it is a
`RIGHT_CLICK_AIR` with no item-use denial. Explicit `useItemInHand() == DENY`
always stops processing; cancelled block interactions remain ignored. The
existing guard cancellation, permission, game-mode, PDC identity, exact hand,
and `InventoryAdapter.Plan` checks remain in the redemption path.

Focused verification after the change:

```text
mvn -B -pl goldbag-plugin -am '-Dtest=GoldBagPluginInteractionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The controller owns rebuilding the packaged JAR, replacing the isolated local
server artifact, and rerunning the actual Paper player harness. No server
process or server files were changed by this worker.
