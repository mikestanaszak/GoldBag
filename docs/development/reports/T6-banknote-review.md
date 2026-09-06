# T6 banknote interaction independent review

Scope: the uncommitted banknote interaction change in
`GoldBagPlugin.java`, `GoldBagPluginInteractionTest`, and its fix report.
The remainder of the plugin was not re-reviewed; production code was read
only and no staging or commit was performed.

## Disposition: clear

The target Spigot 1.17 API confirms the reported platform behavior: a
`RIGHT_CLICK_AIR` event with no clicked block starts with
`useInteractedBlock() == DENY`, so legacy `isCancelled()` is true while
`useItemInHand()` remains `DEFAULT`. Removing `ignoreCancelled` from this
handler and applying `shouldHandleInteraction` therefore admits the vanilla
air event needed for banknote redemption.

The gate also rejects explicit item-use denial independently of legacy
`isCancelled()`, and it continues rejecting cancelled block interactions.
This preserves other plugins' explicit item or block vetoes while allowing
the documented vanilla air no-op. The three focused regression cases cover
those exact API states.

After the gate, the existing redemption path remains intact: guarded players
are cancelled before any new operation; permission and game-mode checks still
run before redemption; the note UUID still comes only from the note PDC; the
event is cancelled before the durable operation starts; and the selected hand
still maps to main-hand slot or off-hand slot 40, with the coordinator's
readiness check revalidating the same note identity and physical plan.

The helper performs only event-result inspection. All Bukkit inventory and
player operations remain on the event/main thread, and no asynchronous
storage callback behavior was changed by this fix.

## Verification

Target API bytecode inspection of the local Spigot 1.17 API confirmed the
constructor and legacy cancellation semantics described above. The worker's
focused check, recorded in `T6-banknote-fix.md`, passed:

`mvn -B -pl goldbag-plugin -am '-Dtest=GoldBagPluginInteractionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Result: 3 tests passed, zero failures/errors/skips. No Maven command was run
for this review because the controller had an active full build.

No concrete remaining defect was found in the scoped change.
