# Direct exchange transaction review

Date: 2026-10-12

Scope: the settled direct deposit and withdrawal implementation in
`GoldBagPlugin`, `InventoryAdapter`, `ExchangeCoordinator`, `MenuService`, the
resource defaults, and the direct exchange verification notes. This was an
independent read-only review while the direct-exchange worker edited the shared
tree. No source files were changed by this review.

## Disposition

No P1 or P2 source finding remains after the final re-read. The transaction
path is clear for integration subject to the live player checks below.

The deposit menu is a 54-slot virtual chest: slots 0–44 remain blank, while
the listener treats cursor left/right placement, chest drag targets, and
player-inventory shift-clicks as input gestures. Eligible items are checked for
the configured material and vanilla metadata before an immutable physical plan
is captured. The event is cancelled before the guarded account read, and the
plan is sent through the existing prepare/apply/complete journal boundary; the
virtual chest never becomes a second item store. The plan rechecks both the
36-slot inventory and cursor state on the main thread before applying, so a
cursor or inventory change cancels safely.

The listener checks menu holder identity and catalog revision before handling a
click. Bottom-inventory click mapping uses `InventoryView.convertSlot`, and
`InventoryAdapter.planMainSlotRemoval` rejects armor and other non-main slots.
Drag handling sums only newly placed top slots 0–44 and rejects ordinary
metadata/custom or unlisted items. Resource icons in the withdrawal menu map
left click to one item, right click to at most 16, and shift click to at most
64; the account balance, plain-item capacity, and configured transaction limit
are applied before the immediate quote executes. A zero-capacity request is
rejected, and successful exchanges leave the menu open and refresh its balance.

Direct command deposits, deposit-all, and resource withdrawals call the same
immediate execution path. The banknote path retains its confirmation UI. The
operation guard is acquired before the direct deposit account read and is
released on rejected plans, coordinator setup failures, completion, and
player/plugin lifecycle cleanup. Catalog reload invalidates quote requests and
the readiness check rejects a stale operation before applying; if the journal
has already reached APPLYING, the unresolved record remains available for
operator recovery. Offline/dead players and close-time inventory changes fail
readiness without guessing a balance or replaying a physical mutation.

## Material verification gap

The focused unit suite exercises cursor plans, exact selected-slot removal,
plan readiness/application, coordinator journal sequencing, quote identity, and
interaction helpers. It does not instantiate actual `InventoryClickEvent` and
`InventoryDragEvent` flows through a Paper server. The saved direct-player
report also says the live run was not performed; its Mineflayer coverage is a
test plan rather than player evidence.

Before integration, run the isolated native-player checks on the supported test
server and record each result: shift-click a stack, cursor left/right placement,
drag across several blank slots, rejection of custom and uncatalogued items,
right-click 16 and shift-click 64 withdrawals, balance/capacity clamping,
full-inventory rejection, repeated clicks while a transaction is pending,
catalog reload, menu close, disconnect/offline completion, and clean restart
recovery. Verify both account balance and physical inventory after each case,
including a forced APPLYING/recovery record where available.

The current focused command was:

```text
mvn -B -pl goldbag-plugin -am test
```

Result: PASS — 13 core, 17 storage, and 52 plugin tests (82 reactor tests),
zero failures. The compile check and `git diff --check` also passed in the
worker’s checkpoint.

## Rates and existing state

The approved resource table changes packaged/default rates while preserving
account balances. `GoldBagPlugin.copyDefault` only creates `resources.yml`
when it is absent, so updating a JAR does not silently overwrite an existing
server’s configured rates. If the approved table is intended for the existing
installation, apply that configuration change explicitly with a backup and
verify balances, enabled flags, materials, and storage settings remain intact.

## Review conclusion

Source review is clear after the worker’s fixes. Integration should treat the
native player run as required evidence for gesture semantics and lifecycle
behavior; the focused unit suite alone cannot certify the actual chest UI.

## Follow-up: bottom-inventory gestures — resolved

The controller now checks `ClickType.DOUBLE_CLICK` and
`InventoryAction.COLLECT_TO_CURSOR` before the bottom-inventory allowance for
every `MenuHolder`. The event remains cancelled, so a matching custom item
cannot sweep protected menu controls. The focused interaction test covers both
signals independently and the combined case.

The controller also verifies the holder player identity in drag events and
allows a drag only when every raw slot is in the player’s bottom inventory for
deposit/withdraw views. Mixed or top-slot drags remain cancelled or route
through the deposit handler, so controls cannot move and eligible deposits
still use the journaled path. This resolves the conditional P2 and the
bottom-only drag UX gap. Native player verification should still exercise both
bottom-only rearrangement and mixed top/bottom drags.
