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
