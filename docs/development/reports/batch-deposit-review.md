# Batch deposit chest review

## Scope and result

Reviewed the final staged chest/listener, `InventoryAdapter.StagedPlan`, `DepositDrafts`, `MenuService.refreshDepositSummary`, withdrawal queue, and lifecycle callbacks on `codex/batch-deposit-chest`. The native 54-slot deposit menu now leaves slots 0–44 editable, blocks controls and cross-menu collect/double-click gestures, and uses one slot-order selection for both the summary and button transaction. Invalid/custom stacks are left in the chest without per-stack storage work. Deposit processing is the only path that acquires the operation guard; the withdrawal queue is session-bound and cleared by quit/reload/close.

The final blocker fixes remove the hidden withdrawal and ambiguous journal-correlation findings. The source is clear enough for focused unit evidence, with one bounded hard-crash limitation that should remain documented for operations.

## Findings

### Resolved: withdrawal request after menu close

`onClose` now invalidates the player request and clears the queue. Menu-triggered withdrawal callbacks carry and revalidate the originating session before creating a journal operation; command-triggered withdrawals remain independent of menu presence. This closes the hidden-future-purchase path. The focused suite should retain a close-before-account-callback regression.

### [P2] A hard crash can still fall between a native move and the coalesced latest snapshot

The opening baseline is now persisted before the first staging gesture is accepted, and the sidecar carries session, operation, in-flight, closing, and returned state. Later native moves remain coalesced to a next-tick asynchronous latest snapshot (`DepositDrafts.java:108-136`). A hard crash in that bounded interval can leave the newest move absent from durable evidence even though the baseline and prior latest snapshot remain available.

This is a deliberate UX/durability tradeoff: making every Bukkit gesture wait for an fsync would undermine native chest interaction. Keep baseline/latest evidence and manual quarantine on uncertain startup; do not guess a refund or automatically replay the missing move. A fault test for the event-to-snapshot interval is useful future coverage, but it is not a release blocker once the operator recovery path is documented.

### Resolved: exact journal correlation

The deposit payload now carries the draft session and operation IDs alongside the staged physical evidence, and the sidecar records the operation and lifecycle flags (`GoldBagPlugin.java:487-498`, `DepositDrafts.java:155-163`). Pending/APPLYING failures retain the draft in-flight and quarantine evidence instead of clearing it for a later automatic return.

## Verified behavior and remaining evidence

- `InventoryAdapter.StagedPlan` fingerprints only chest slots 0–44 plus the player inventory, and applies only the selected staged removals. A journaled batch leaves invalid/custom and over-limit items staged.
- The batch planner and summary both scan slots 0–44 in the same order, so the displayed count/value matches the selected batch.
- Deposit close/switch returns unsold stacks once with inventory overflow dropped in-world; quit uses the immediate return/save path and waits before sidecar deletion. In-flight or unresolved operations retain their draft rather than guessing a refund.
- Opening the staging chest persists a baseline before accepting gestures; subsequent coalesced snapshots provide latest evidence while preserving the bounded crash interval described above.
- `DOUBLE_CLICK`/`COLLECT_TO_CURSOR` are cancelled before menu handling, preventing top-menu icon sweeps. Bottom-only gestures and native top-45 staging remain available when no batch guard is active.
- Focused and full unit evidence run locally: `mvn -B -pl goldbag-plugin -am test` passed with 13 core, 17 storage, and 54 plugin tests (84 total). `git diff --check` passed.
- No native Paper player smoke was run for this fresh chest flow. Before release, perform a reasonable live smoke covering cursor left/right, plain and invalid shift-click, mixed top/bottom drag, slot 52 duplicate clicks, close/switch/quit with full inventory, and rapid withdrawal clicks across a close/reload boundary. Unit evidence is sufficient for planner/guard/queue invariants; native evidence is required for Bukkit movement and lifecycle behavior. An exhaustive fault-injection matrix is future work, provided unresolved drafts remain quarantined and operator-visible.
