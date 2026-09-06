# T6 player harness fixes

## Scope

This checkpoint addresses the two P2 findings in `T6-player-harness-review.md`: reset mode must clear both player inventories, and connection/cleanup failures must not leak Mineflayer clients. Production, Maven, and server files are outside this task.

## Planned changes

- Make the connection lifecycle cleanup-safe for pre-spawn timeout, early kick/error, second-client failure, and quit exceptions.
- Reset both named fixture inventories in reset mode before any item assertions.
- Select the exact copied note identity after the operator replacement rather than an arbitrary paper stack.
- Add focused Node lifecycle tests using stubbed Mineflayer clients; do not reconnect to the live server during this correction.

## Verification state

No server rerun has been performed for this correction. The prior 16-check actual-player run and no-reset restart result remain valid evidence and are preserved in `T6-player-server-tests.md`.

## Fix implementation and focused tests

Implemented in `player-smoke.js`:

- Reset mode now clears both A and B inventories after audited balance resets.
- Note identity extraction reads the UUID from the item data, and copied-note selection requires that exact UUID. A stale paper item cannot satisfy the replay assertion.
- `connect` cleans up clients on pre-spawn timeout, kick, error, or end. `run` retains cleanup-safe references and `closeBots` isolates exceptions so one failing `quit()` cannot skip another client.
- The executable path is guarded with `require.main === module`; narrow lifecycle and identity helpers are exported for tests.

Added `player-smoke.test.js` with five stubbed-client tests:

`$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path; & 'C:\nvm4w\nodejs\node.exe' --test scripts/server-tests/player-smoke.test.js`

PASS: 5 tests, including timeout cleanup, pre-spawn kick cleanup, exception-isolated dual cleanup, exact note identity selection, and normal spawned-client cleanup. No server connection or reset-mode rerun was performed after these edits.

The server-test README now invokes `node` from PATH rather than the machine-specific Node installation path; the runtime dependency remains isolated under `.runtime`.

## Post-fix live verification

After the controller installed reproducible artifact SHA-256 `C48C54EC4037C8777B32FEC2F5017B00A69200306D95C417A95C5845D9785535`, one authorized reset-mode run was performed against the stale B-inventory fixture. It passed all 16 actual-player checks. The reset log explicitly removed B's stale paper before the copied-note scenario; exact copied identity selection then passed. Final fixture was A=G85.00/B=G15.00 with one G5.00 note in A inventory, and both clients disconnected cleanly.

No further reset-mode run is needed. Controller may now run the no-reset restart check and then complete the final recovery/persistence handoff.

## Final pre-merge verification

The controller repeated the no-reset check after a clean stop/start with the reproducible `C48C54EC4037C8777B32FEC2F5017B00A69200306D95C417A95C5845D9785535` artifact. PASS: A reopened at G85.00, B at G15.00; persisted note redeemed to A=G90.00 and B's copied retry was rejected. Storage reported healthy with zero pending operations; server saved and stopped cleanly at17:17:55local. The five focused Node tests passed again. Independent review in `T6-player-harness-review.md` is clear, including the new Node22 CI job.
