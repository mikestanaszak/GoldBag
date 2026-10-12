# GoldBag Paper player smoke

This directory contains the Mineflayer protocol smoke intended for an isolated local Paper test server. Its default address is `127.0.0.1:25575`; the host is configurable. It does not start a server or accept an EULA. The full smoke resets its test accounts and inventories.

Minecraft 26.3 validation is currently blocked: Mineflayer 4.38.0 rejects protocol 777, and pinned 4.39.0 reports `No data available for version 26.3`. Unit lifecycle tests still run, but they do not validate live 26.3 player behavior. Keep the pin until a client update supplies native protocol support; use an actual 26.3 client for manual inventory/banknote checks. See the [runtime report](../../docs/development/reports/26.3-runtime.md).

## Setup

Use Node 22 or newer. Install the pinned dependency into the ignored runtime directory:

```powershell
New-Item -ItemType Directory -Path .runtime/goldbag-server-tests -Force | Out-Null
Copy-Item scripts/server-tests/package.json,scripts/server-tests/package-lock.json .runtime/goldbag-server-tests/
npm ci --prefix .runtime/goldbag-server-tests
```

The two fixture names are `GoldBagSmokeA` and `GoldBagSmokeB`. On the isolated server, grant operator access only to those names so the harness can create item fixtures and reset accounts through audited commands.

## Full smoke

```powershell
$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path
node scripts/server-tests/player-smoke.js
```

The reset-mode run sets both fixture accounts to zero, then checks the menu, rates, storage status, direct deposit/withdraw commands, repeated and rapid queued withdrawals, close-cleared withdrawal requests, rapid multi-stack staging, cursor and right-click staging, one batch Deposit submission, duplicate-button idempotency, invalid/custom staged-item return on close and menu switch, direct withdrawal left/right/shift gestures, deposit-all (25 raw iron plus 5 redstone = G51.25), payment (A=G46.25 after G5.00), the banknote fixture reset to G100.00, main/off-hand notes, copied-note replay, custom and ineligible command rejection, full-inventory rejection, ingot and creative gates, leaderboard GUI, and the note-only confirmation fallback. It ends with A at G85.00 and one unredeemed G5.00 note in A's inventory; B is G15.00.

## No-reset restart check

After a clean Paper stop/start with the same plugin data, do not run the full smoke first. Run:

```powershell
$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path
$env:GOLDBAG_RESTART_CHECK='1'
node scripts/server-tests/player-smoke.js
Remove-Item Env:GOLDBAG_RESTART_CHECK
```

This verifies A=G85.00 and B=G15.00, copies the persisted note to B, redeems it once for A to reach G90.00, and verifies that B's replay does not credit its balance.

The harness requires the actual live chat and inventory protocol. Synthetic recovery resolution was tested separately; crash-at-every-journal-stage and hard-crash inventory persistence remain unverified.

## Synthetic recovery fixture

With a prepared test server stopped and its GoldBag database already initialized, run:

```powershell
java -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar scripts/server-tests/ServerRecoveryFixture.java '<isolated-server-directory>'
```

The helper refuses existing fixture accounts and acquires GoldBag's database lock. It creates two labeled pending deposits without changing any physical inventory. After startup, use the printed operation IDs with the recovery commands: apply should credit `GBRecoveryApply` G2.00; cancel should leave `GBRecoveryCancel` G0.00. The exact executed commands and outcomes are in [the server report](../../docs/development/reports/T6-server-validation.md).

## Batch deposit and direct exchange UI smoke

The current full smoke treats the deposit screen as a native editable 54-slot chest. Slots 0-44 accept ordinary, uncatalogued, and custom stacks as staging data; slots 45/48/49/50/52 provide Back to Main, Withdraw, Close, balance/instructions, and Deposit. Rapid shift-click, cursor, and supported right-click placement must leave the balance and chat unchanged. Slot 52 submits eligible ordinary staged resources in one journaled batch, keeps the same menu open, and is idempotent when clicked again. Unsupported and custom stacks remain staged and are returned exactly once on close or when switching to withdrawal. The withdrawal screen keeps slots 48/49/50/51 for Deposit, Close, balance/instructions, and Back to Main; slot 45 is reserved for an optional previous-page control when a later page exists. Resource left-click, right-click, and shift-click request 1, 16, and 64 items, clamped to balance and capacity while the screen remains open. The smoke also queues three rapid same-resource clicks and verifies all three items arrive, then closes during another bounded queued batch and verifies waiting requests are discarded. Commands `/goldbag deposit`, `/goldbag deposit all`, and `/goldbag withdraw` execute directly. Banknote issue remains the only smoke path that opens Confirm; `/goldbag confirm` is retained as a compatibility replay check.

Mineflayer's public click API does not implement mode-5 drag packets. Mineflayer 4.39.0 also omits the Paper 1.21.11 `set_cursor_item` listener, so the harness adds a bounded raw-packet adapter for the packet's `contents` slot and its null clear packet. Shift-click and cursor placement are automated; drag coverage remains a native-client follow-up because the client library has no supported mode-5 operation. The right-click single-item staging case runs when the connected protocol supports normal split-stack clicks.

For the isolated Paper 26.3 bridge experiment, ViaVersion/ViaBackwards 5.12.1 translate an older test client. Use client 1.21.8 or 1.21.4, Mineflayer 4.39.0, and `GOLDBAG_DISABLE_PHYSICS=1` (disables physics at spawn). The short bridge probes connected but the full run still disconnected; this configuration does not certify 26.3 player behavior. Final full UI validation instead passed on native Paper 1.21.11 build 132. See [UI live validation](../../docs/development/reports/ui-live-validation.md).
