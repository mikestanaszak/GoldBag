# T4 physical transaction review

Scope: frozen commit `05e7ab4`, limited to `InventoryAdapter`, `ExchangeCoordinator`, `StorageExecutor`, their focused tests, and the physical/lifecycle/guard portions of `GoldBagPlugin`. Command, menu, configuration, quote, core, storage, and offline CLI behavior are outside this review. The shared working tree was changing concurrently, so every source conclusion below was checked with `git show 05e7ab4:<path>`.

## Findings

### P1 — A timed-out shutdown can lose the live writer and close its store on re-enable

Files: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:62-92,121-128`; `StorageExecutor.java:69-85`.

`closeStorage()` intentionally leaves `storageExecutor` and `store` assigned when `StorageExecutor.stop()` returns false, because an active writer may still be using the database. However, a later `onEnable()` unconditionally overwrites `storageExecutor` with a new executor before constructing the replacement `SqliteStore`. If the old writer still owns the database lock, the new `SqliteStore` construction throws. The `onEnable()` catch then calls `closeStorage()` against the new, already-idle executor and the old `store` field, closing the old store while the old executor thread is still active. That contradicts the stated ownership-retention safety and can produce a use-after-close SQLite failure or data corruption.

Reproduction: submit a storage task that ignores interruption and runs longer than both five-second shutdown waits; call `onDisable()` so `stop()` returns false; enable the same plugin instance again before the worker exits. The second `SqliteStore` fails on the existing lock, then the catch path closes the old store even though its old executor is still running.

Fix by making a non-terminated owner a terminal/unavailable lifecycle state that prevents re-enable, or retain the old executor/store as explicit owner fields and never overwrite or close them until termination is observed. Add a lifecycle regression covering disable timeout followed by re-enable.

### P2 — Banknote capacity is checked with the wrong item metadata

Files: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:498-507`; `InventoryAdapter.java:170-195`.

`issueNote()` first calls `canFit(..., new ItemStack(Material.PAPER))`, but the actual note created at line 504 has a display name, lore, and a unique PDC UUID. `planAddition()` merges only equal type and metadata and otherwise requires an empty slot. Therefore a plain PAPER stack with one free item slot can make the precheck pass even when there is no empty slot for the uniquely-metadata note.

Reproduction: fill slot 0 with 63 plain PAPER, fill slots 1–35 with full STONE stacks, and issue a note. The plain PAPER precheck succeeds; the guarded `planAddition(noteItem)` throws `Inventory does not have capacity`, the catch releases the guard, and no journal or inventory mutation occurs. The operation is safe but incorrectly reports an inventory change instead of a capacity rejection and cannot issue a note despite the precheck.

Construct the note and capture its exact addition plan before acquiring the guard, or use the exact note stack for the capacity check and reuse that same plan.

### P2 — Completion callback scheduler failure can permanently retain a player guard

Files: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:288,464,495,527`; `GoldBagPlugin.java:155-157,95`.

All four physical-operation completion callbacks call `runMain(...)` before releasing their operation token. If Bukkit rejects that scheduling call, the callback throws before `unguard(...)`; the exception is only recorded by `CompletableFuture` and there is no cleanup path. `onDisable()` sets `healthy` false and closes storage but does not clear `guardedPlayers`, so an online player can retain a stale token across a same-process disable/re-enable, causing every guarded inventory event to remain cancelled and every new physical operation to be rejected.

Reproduction: let a physical operation reach completion while the plugin is being disabled (or use a scheduler double that throws for the completion callback). The callback’s `runMain` throws, leaving `guardedPlayers` populated. Re-enable the same plugin instance and check `guarded(playerId)`; it remains true indefinitely.

Wrap completion scheduling with a cleanup fallback that releases only the operation’s token, and clear or otherwise reconcile stale in-memory guards during lifecycle shutdown. Keep durable APPLYING/PREPARED records as the recovery authority.

### P2 — Bukkit Player methods are evaluated on the storage worker

Files: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:160`; `GoldBagPlugin.java:562-569`.

The `Callable` passed to `submit()` runs on `StorageExecutor`’s worker, but the join/account-initialization lambda calls `player.getUniqueId()` and `player.getName()` inside the callable. This violates the plugin’s main-thread boundary and can race a quit/rejoin or fail under a thread-checking Bukkit implementation. The join path can then leave the account uninitialized before a physical exchange is confirmed.

Reproduction: invoke `onJoin` while a test Player double rejects calls off the server thread, or force the worker to run after `PlayerQuitEvent`; the worker calls the Player object rather than captured UUID/name and account initialization fails or observes stale identity.

Capture UUID/name on the server thread before submitting the storage task and pass immutable values to the worker. Physical inventory reads/writes and all Player state rechecks already remain in the main-thread coordinator callbacks in this snapshot.

## Review disposition

The frozen coordinator sequencing is otherwise sound for the reviewed boundary: it prepares durably, rechecks readiness on the main-thread bridge before and after `markApplying`, applies only the captured immutable plan, leaves APPLYING pending on physical/complete/scheduler failure, and does not auto-replay a timed-out operation. `InventoryAdapter.Plan` compares all 36 main-inventory slots and writes only affected slots; its evidence includes before/after snapshots and the GoldBag note UUID. The focused tests cover changed inventory, physical failure quarantine, scheduler failure, late prepare timeout, duplicate note redemption, exact plan application, and bounded shutdown, but none exercise the four failure paths above.

No full reactor retest was run; the controller reported the focused suite and full verification as already passing at this frozen checkpoint.

## Offhand helper follow-up (`77b417f`)

The `49e8615..77b417f` change was reviewed independently with the source and tests read from commit `77b417f`. `planSlotRemoval` correctly accepts only hotbar slots `0–8` and offhand slot `40`, reads ordinary plans from only slots `0–35`, captures a cloned selected offhand stack, includes slot 40 in `evidence()` and `affectedSlots()`, rechecks the selected stack before applying, and writes only the selected offhand slot. Main-hand plans also recheck `getHeldItemSlot()`, so a held-slot change invalidates them. Armor indices `9–39` are rejected. No P1/P2 physical mutation or duplication issue was found in this helper.

### P2 — Offhand `beforeEvidence()`/`afterEvidence()` omit the selected slot

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/InventoryAdapter.java:57-62` at `77b417f`.

For `planSlotRemoval(inventory, 40)`, the constructor’s `evidence()` correctly calls `snapshot(..., selectedSlot, selectedBefore/After)` and records `slot=40`. The public `beforeEvidence()` and `afterEvidence()` methods instead call `snapshot(before)` and `snapshot(after)` without the selected-slot arguments. They therefore return only the 36 main slots and silently omit the offhand item and its PDC note identity. Any integration persisting these helpers would record incomplete before/after evidence even though readiness and apply are correct.

Reproduction: create an offhand note plan and assert `plan.evidence()` contains `slot=40`; `plan.beforeEvidence()` and `plan.afterEvidence()` do not. Include slot 40 in both helper methods (or make the evidence API explicitly main-inventory-only and keep the offhand evidence in a dedicated accessor).
