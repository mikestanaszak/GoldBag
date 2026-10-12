# Direct player exchange smoke

This report defines the live player checks for the direct exchange UI. The
Mineflayer harness is intended for an isolated Paper server only. It does not
start a server, accept an EULA, reset any production account, or connect to a
public host.

## Runnable command

The harness-only checks are safe to run without a server:

```powershell
node --test scripts/server-tests/player-smoke.test.js
```

Local result for this harness update: 11 tests passed, including connection
cleanup, exact copied-note selection, open-window inventory-state accounting,
plain-versus-custom item selection, cursor packet restoration/clearing, deposit
layout controls, withdrawal layout controls, and the same-window direct-exchange
guard. A live run was not performed here; it is controller-owned and must use
the isolated fixture below.

Prepare the ignored runtime once with Node 22 or newer:

```powershell
New-Item -ItemType Directory -Path .runtime/goldbag-server-tests -Force | Out-Null
Copy-Item scripts/server-tests/package.json,scripts/server-tests/package-lock.json .runtime/goldbag-server-tests/
npm ci --prefix .runtime/goldbag-server-tests
```

Run the reset-mode smoke only against the isolated native Paper 1.21.11
fixture described in `ui-live-validation.md`:

```powershell
$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path
$env:GOLDBAG_HOST='127.0.0.1'
$env:GOLDBAG_PORT='25577'
$env:GOLDBAG_MC_VERSION='1.21.11'
node scripts/server-tests/player-smoke.js
```

The deposit-all fixture deposits 25 raw iron plus 5 redstone for G51.25; the
payment check leaves A=G46.25 and B=G5.00, then the harness resets A to G100.00
for the unchanged banknote and replay endpoints. The controller owns the live
run. The same fixture is left with A=G85.00, B=G15.00,
and one unredeemed G5.00 note when reset mode passes. After a clean isolated
server restart, run the persistence/replay check without reset:

```powershell
$env:GOLDBAG_RESTART_CHECK='1'
node scripts/server-tests/player-smoke.js
Remove-Item Env:GOLDBAG_RESTART_CHECK
```

## Required coverage

The harness checks the direct command forms `/goldbag deposit <material> <count>`,
`/goldbag deposit all`, and `/goldbag withdraw <material> <count>`, with no
quantity or confirmation menu for those operations. It then opens the deposit
chest and verifies that slots 0-44 are blank and slots 45, 48, 49, 50, and 52
are Back to Main, Withdraw, Close, balance/instructions, and Deposit all. A real player inventory
shift-click and cursor placement immediately journal deposits and leave the
same chest open. A right-click split-stack placement covers the one-item path
when supported by the connected protocol. Uncatalogued and custom items remain
in the player inventory.

The withdrawal chest keeps slots 48, 49, 50, and 51 for Deposit, Close,
balance/instructions, and Back to Main; slot 45 is reserved for a previous-page
arrow only when a later page exists. Resource clicks use left=1, right=16, and shift=64;
balance and capacity clamp the requested amount, and the menu remains open for
repeat operations. Payment, banknote confirmation/redemption, copied-note
one-credit replay, full-inventory rejection, creative-mode rejection,
leaderboard, and no-reset persistence remain in the existing matrix.

Mineflayer 4.39.0 exposes ordinary click and shift-click packets but its public
`clickWindow` implementation does not implement mode-5 drag packets, and its
inventory plugin does not listen for Paper 1.21.11's `set_cursor_item` packet.
The harness installs a bounded raw-packet adapter for the latter, decoding the
packet's `contents` slot into the open window's selected cursor item and
handling the null clear packet. Cursor placement is therefore automated while
drag behavior remains a manual native-client check. Paper 26.3 player protocol
also remains unverified because the available Mineflayer releases have no
native 26.3 data. Never use the reset-mode command against production.

## Controller native player result

Final direct-exchange run PASS: 20 actual-player checks on isolated Paper 1.21.11 build 132, Java 25.0.4.1, loopback 127.0.0.1:25577, Mineflayer 4.39.0. The harness uses authoritative open-window player slots and the documented `set_cursor_item` adapter. Early failed assertions were stale client inventory/cursor observations and selection of the plain instead of named fixture; no economy workaround was applied.

Coverage: direct exact commands, repeated direct withdrawals, shift-click and cursor stack deposits, right-click single placement, named/ineligible rejection, persistent menus, 1/16/shift withdrawals with affordability and full-inventory clamping, Deposit All at revised prices (25 iron + 5 redstone = G51.25), payments, note identity/main/offhand redemption/replay, creative rejection, leaderboard and final restart fixture. Final A=G85.00, B=G15.00 with one unredeemed G5.00 note. Source runtime checkpoint efc9261; final harness checkpoint 9fac29f. Tested snapshot SHA-256 0b6a7e0e9770810c025cb941dd11476648be1ae63f3b1e0ab26ad559adbfda8a.

Log: /tmp/goldbag-direct-player-live.log. Drag packets, death/disconnect during APPLYING and native 26.3/Geyser player behavior remain manual/future fault-validation limits. Startup alone is not a player-flow pass.

No-reset restart PASS after clean native-server stop/start. A/B balances and issued note persisted; one redemption raised A to G90.00, copied replay left B at G15.00. Log: /tmp/goldbag-direct-player-restart.log. Server stopped cleanly afterward. Published 2.1.0 artifact verification and live deployment evidence are in direct-release-deployment.md.
