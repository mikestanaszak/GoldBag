# T4 InventoryAdapter hardening

## Scope

This worker owns only `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/InventoryAdapter.java`, its focused `InventoryAdapterTest`, and this report. No Git staging or commits are performed by the worker.

## Current API for plugin integration

`InventoryAdapter` keeps the existing wrappers (`count`, `canRemove`, `remove`, `canFit`, `add`, `eligible`, and evidence helpers) and adds an immutable `InventoryAdapter.Plan` API scoped to the first 36 player inventory slots:

- `planRemoval(PlayerInventory, Material, int)` and `planRemoval(PlayerInventory, Map<Material,Integer>)` capture cloned before/after stacks in deterministic slot order.
- `planAddition(PlayerInventory, ItemStack)` captures exact merge and empty-slot choices without Bukkit `addItem`.
- `planHeldRemoval(PlayerInventory)` captures one-item removal from the selected main-hand slot.
- `Plan.ready(inventory)` rechecks all 36 slots immediately before mutation; `Plan.apply(inventory)` rechecks again and writes only affected slots.
- `Plan.evidence()` is generated from the same cloned plan and contains `before=`/`after=` slot IDs, material, count, and metadata text (including PDC note identity); `affectedSlots()` identifies exact writes.

The plugin worker can capture a plan before durable `prepare`, use `ready` as its pre-APPLYING and post-APPLYING guard, and call `apply` only on the Bukkit main thread. A changed unrelated slot rejects the whole plan without rollback or mutation.

## Remaining work at this checkpoint

- The pure Bukkit API fixture is complete: it supplies an `ItemFactory` and `PlayerInventory` proxy without adding a test dependency or pretending to be a live server.

## Changes and verification

- `isPlain` now compares `ItemMeta` with a fresh vanilla stack of the expected material. This rejects all metadata components that differ from the pristine baseline, including components outside the former denylist.
- Addition planning compares type and full metadata directly, avoiding Bukkit `ItemStack.isSimilar` side effects and preserving distinct note PDC identities. Evidence canonicalizes serialized metadata, sorts map/collection fields, records PDC keys, and emits the GoldBag note UUID explicitly.
- Existing wrapper methods now delegate to exact plans; no wrapper calls Bukkit `Inventory.addItem` or independently recomputes evidence.
- `mvn -B -pl goldbag-plugin -am '-Dtest=InventoryAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` — PASS, 6 tests, 0 failures/errors.
- `mvn -B -pl goldbag-plugin -am test` — PASS on the current workspace: core 12, storage 17, plugin 29 tests, 0 failures/errors.
- `git diff --check` over the owned tracked files — PASS.

The existing plugin worker still needs to adopt `Plan` in each coordinator `InventoryPort`: capture before `prepare`, return `plan.ready(inventory)` immediately before and after `markApplying`, and call `plan.apply(inventory)` on the main thread. This worker did not edit `GoldBagPlugin.java` or `ExchangeCoordinator.java`.

## Off-hand and selected-slot extension

The plan API now also exposes `planSlotRemoval(PlayerInventory, int)`. It
accepts hotbar slots `0..8` and Bukkit off-hand slot `40`; armor and all other
slots are rejected. A slot plan records the exact selected stack, rechecks the
selected slot and full metadata/PDC identity before applying, and emits the
selected off-hand slot in `before=`/`after=` evidence. Main-hand plans also
recheck that the held hotbar slot is unchanged. `planHeldRemoval` delegates to
the selected-slot API. Ordinary `planRemoval`, `count`, and `eligible` scans
remain limited to slots `0..35`, so off-hand notes/resources are not included
in ordinary deposit flows.

Focused tests cover off-hand note identity and evidence, ordinary off-hand
exclusion, armor rejection, and main-hand selection changes. The test-first
Maven attempt was blocked before test execution by an unrelated active
`GoldBagPlugin.java` compile error: its current lines 212-213 call a missing
`message(String, String)` method. Independent Java 16 compilation of the
updated `InventoryAdapter.java` and `InventoryAdapterTest.java` passed.
An isolated JUnit-method runner over the same fixture then passed all 9
`InventoryAdapterTest` methods after adding the cached Spigot/JUnit runtime
dependencies; the normal Maven route remains blocked by that unrelated source
error.

Integration API for `/root/plugin`: for a redemption event, choose slot `40`
for `EquipmentSlot.OFF_HAND`, otherwise `player.getInventory().getHeldItemSlot()`;
call `inventory.planSlotRemoval(player.getInventory(), slot)` on the main thread,
use `plan.ready(...)` for each revalidation, `plan.apply(...)` for the physical
change, and `plan.evidence()` for the durable payload. The plan owns the exact
slot and PDC identity; do not fall back to `getItemInMainHand()` for off-hand
redemption.
