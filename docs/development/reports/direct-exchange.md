# Direct exchange implementation

## Scope

Implement the direct deposit and withdrawal interaction path for the GoldBag menus. The plugin listener owns gesture interception and transaction dispatch; `InventoryAdapter` owns immutable physical plans, including cursor evidence and exact slot removal. The deposit chest remains virtual: eligible cursor and shift-click gestures are consumed into a journaled exchange rather than being placed in the menu inventory.

## Initial state

- Branch: `codex/ui-confirmation-experience` (working tree initially clean).
- Existing transaction flow opens quantity and confirmation menus for deposits and withdrawals.
- Existing `InventoryAdapter.Plan` snapshots the player inventory and supports deterministic slot changes, but has no cursor snapshot/ready/apply support.

## Planned verification

- Focused `goldbag-plugin` tests for cursor/slot plans, gesture eligibility, direct transactions and guard behavior.
- Full Maven verification is owned by the controller after integration.

## Integration notes

- Controller owns `MenuService.java` and `MenuHolder.java`; direct menu layout additions must use the listener/API changes here.
- No commits are made by this worker. The controller stages and integrates the implementation.

## Implementation checkpoint

- `InventoryAdapter.Plan` now supports exact main-slot removal, cursor removal with before/after evidence, `ready(Player)`, and `apply(Player)`. Existing hotbar/off-hand note plans retain their selected-slot checks and evidence.
- `GoldBagPlugin` intercepts deposit cursor left/right clicks, chest drags, and player-inventory shift-clicks. Eligible ordinary resources are journaled through the existing coordinator; the virtual chest never receives the item. Metadata/custom/uncatalogued items are rejected with feedback. A per-player guard is acquired before the direct deposit account read and remains through APPLYING.
- Resource clicks in the withdrawal menu execute one, up to 16, or up to 64 items for left, right, and shift clicks; the latter two clamp to balance, capacity, and transaction limits. Deposits, deposit-all, and withdrawals invoked through commands now execute immediately while banknote previews retain confirmation.
- Menu balance refreshes after successful deposit/withdrawal and the deposit/withdraw chest remains open.

## Verification

- `mvn -B -pl goldbag-plugin -am -Dtest=InventoryAdapterTest,GoldBagPluginInteractionTest -Dsurefire.failIfNoSpecifiedTests=false test`: PASS (14 focused tests).
- `mvn -B -pl goldbag-plugin -am test`: PASS (13 core, 17 storage, 52 plugin tests; 82 total).
- `git diff --check`: PASS.
