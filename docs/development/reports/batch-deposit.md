# Batch deposit chest implementation

## Scope

Replace per-gesture direct deposits with a native editable 45-slot staging chest. Player gestures only mutate the virtual chest; the Deposit all button performs one journaled transaction for eligible ordinary staged items. Unsupported/custom items remain staged until the player takes them back. Closing, switching, quitting, and disabling return unsold staged items exactly once, while in-flight transactions retain their snapshot until completion or cancellation.

## Initial state

- Branch: `codex/batch-deposit-chest`, based on current `main` after direct exchange PRs.
- Existing deposit listener consumes cursor/shift-click gestures into immediate transactions, causing latency and repeated invalid-item feedback.
- Existing `InventoryAdapter.Plan` supports player inventory and cursor snapshots but no staged chest plus player-inventory before/after plan.
- Controller owns `MenuService.java` and user-facing documentation; this worker owns plugin listener/transaction code, inventory plans, holder state, draft persistence, focused tests, and this report.

## Design checkpoint

Implement a per-player/session draft that snapshots staged chest contents and the player inventory. Chest controls remain slots 45–53 and are never editable. Deposit button 52 coalesces eligible ordinary stacks into one immutable physical plan and one coordinator operation. Draft close/quit/disable return logic will be identity-safe and deferred while the operation is in flight. Durable sidecar snapshots will be written asynchronously from main-thread serialized item data before clean return/deletion; abandoned snapshots remain for manual recovery evidence.

No schema migration or price changes are in scope.

## Implementation checkpoint

The plugin now opens a native 54-slot deposit inventory with editable slots 0-44. Bukkit
handles ordinary staging gestures locally; controls and cross-menu collect gestures remain
cancelled. Slot 52 builds one exact multi-material staged plan, persists a sidecar snapshot,
and executes one journaled deposit. Unsupported or custom stacks remain staged. Closing,
quitting, and disable paths use an identity-bound draft and return unsold items with overflow
drops while retaining sidecar evidence when saving or returning fails.

Withdrawal resource clicks reserve the operation guard before their account read. Resource
clicks observed during that guard are bounded to 64 session-bound requests and drained one at
a time after completion; unrelated inventory gestures are not queued.

`InventoryAdapter.StagedPlan` fingerprints editable chest slots and the unchanged player
inventory, and its journal evidence includes chest/player before and after state. Draft writes
are coalesced to one main-thread snapshot per tick and written through `StorageExecutor`; the
opening snapshot is persisted immediately and native staging is held until that baseline write
acknowledges, closing the pre-first-gesture crash window. Sidecars include session, operation,
in-flight, closing, and returned state. Menu withdrawal callbacks revalidate the originating
session, and leaving a withdrawal screen invalidates its pending quote and queue.

## Verification

`mvn -pl goldbag-plugin -am -DskipTests compile` passes after the implementation checkpoint.
The focused plugin suite also passes: `mvn -pl goldbag-plugin -am
-Dtest=InventoryAdapterTest,GoldBagPluginInteractionTest,GoldBagPluginCompletionTest,ExchangeCoordinatorExecutionTest
-Dsurefire.failIfNoSpecifiedTests=false test` (25 tests, 0 failures). No commits are made by
this worker; the controller stages and integrates the files.
