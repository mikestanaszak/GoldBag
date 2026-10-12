# Batch deposit player harness

This report defines the Mineflayer checks for the staged deposit chest. The
harness is intended for an isolated Paper server only. It does not start a
server, accept an EULA, reset a production account, or connect to a public
host.

## Harness-only verification

Run the focused unit suite with Node 22 or newer:

```text
NODE_PATH=scripts/server-tests/node_modules node --test scripts/server-tests/player-smoke.test.js
```

The current harness update passes 11 tests. The fixtures cover open-window
inventory accounting, custom-versus-plain stack selection, raw
`set_cursor_item` restoration and clearing, staged chest items in slots 0-44,
the deposit and withdrawal control layouts, lifecycle cleanup, and the
same-window guard. The unit suite does not connect to a server.

## Isolated live command

Prepare the ignored Mineflayer runtime once:

```text
mkdir -p .runtime/goldbag-server-tests
cp scripts/server-tests/package.json scripts/server-tests/package-lock.json .runtime/goldbag-server-tests/
npm ci --prefix .runtime/goldbag-server-tests
```

Run the reset-mode smoke only against the isolated native Paper 1.21.11
fixture described by `ui-live-validation.md`:

```text
GOLDBAG_HOST=127.0.0.1 \
GOLDBAG_PORT=25577 \
GOLDBAG_MC_VERSION=1.21.11 \
NODE_PATH=.runtime/goldbag-server-tests/node_modules \
node scripts/server-tests/player-smoke.js
```

The controller owns this live run. The isolated server path, Java version,
Paper build, and previous protocol limitations are recorded in
`docs/development/reports/ui-live-validation.md`; no production endpoint is a
valid target for this command.

## Coverage

The reset smoke checks direct `/goldbag deposit`, `/goldbag deposit all`, and
`/goldbag withdraw` compatibility commands, then opens the deposit chest and
stages five coal stacks plus raw iron through rapid shift-clicks. It verifies
that staging leaves balance, chat, and the menu unchanged until slot 52 is
pressed. Cursor placement and supported right-click single-item placement are
also staged before one batch submission; the expected batch is G216.25 at the
approved rates (320 coal, 25 raw iron, 5 redstone, and one raw gold). A second
slot-52 click must not credit the batch again.

The harness stages an uncatalogued ingot and a named custom raw-iron item to
verify they survive submission and return exactly once when the menu closes.
It also verifies that switching to withdrawal returns unsold staging and that
closing an eligible staged item without submission makes no economy change.
Withdrawal left/right/shift gestures continue to request 1, 16, and 64 items,
with affordability and capacity clamping. The live smoke also queues three
rapid same-resource clicks and verifies all three items arrive, then closes
while another bounded queue is active and verifies waiting requests are
discarded. Persistence, payments, banknotes, copy/replay protection, and the
existing rejection matrix remain retained.

Mineflayer 4.39.0 has no public mode-5 drag operation, so drag staging remains
a native-client follow-up. The harness installs a bounded raw-packet adapter
for Paper 1.21.11's `set_cursor_item` packet, including its null clear packet,
to keep cursor staging and rejected-cursor recovery observable. Native Paper
26.3 player behavior remains unverified because the available Mineflayer
releases do not provide protocol 777 data.

## Controller live validation (2026-10-12)

- Source runtime commit `8a4acd4`; snapshot SHA-256 `ec61033a51002fbe11000dc26423511a3e6d9d31e5cbfcc90e53cbbfe99d92fe`.
- Paper 1.21.11 build 132 (`c5eb079`), Temurin Java 25.0.4.1+1, isolated loopback port 25577.
- Full reset-mode harness: **23 actual-player checks PASS**. One button deposited 351 staged items for G216.25; repeated submission did not double-credit. Rapid multi-stack staging, cursor/single placement, mixed custom/unsupported returns, menu switching, queued withdrawals and clearing on close all passed.
- Full reactor: **84 tests PASS**; harness unit suite: **13 PASS**; release analysis: **5 PASS**; packaged verifier PASS (Java16 bytecode, SQLite natives, restart/offline restore).
- Initial harness attempt placed into occupied chest slots; native chest swapping exposed the fixture mistake. Corrected placement chooses an empty slot and returns right-click remainder. The full rerun above passed.
- Log: `/tmp/goldbag-batch-player-live.log`. Native client drag, Paper26.3/Geyser player behavior and extended abrupt-crash faults remain unverified. Coalesced draft evidence has a documented native-gesture-to-snapshot crash window; recovery remains manual.

Supplementary isolated lifecycle check PASS: staged two coal, quit without submitting, rejoined with exactly two coal. Log `/tmp/goldbag-batch-lifecycle.log`. No fixture touched production.

Clean stop/start with the same artifact and no fixture reset PASS: balances A85/B15 and issued note persisted; note redeemed once to A90/B15 and copied replay rejected. Log `/tmp/goldbag-batch-player-restart.log`.
