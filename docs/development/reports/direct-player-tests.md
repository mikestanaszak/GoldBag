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

Local result for this harness update: 8 tests passed, including connection
cleanup, exact copied-note selection, deposit layout controls, withdrawal
layout controls, and the same-window direct-exchange guard. A live run was not
performed here; it is controller-owned and must use the isolated fixture below.

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
`clickWindow` implementation does not implement mode-5 drag packets. Cursor
placement is therefore the automated equivalent; drag behavior remains a
manual native-client check. Paper 26.3 player protocol also remains unverified
because the available Mineflayer releases have no native 26.3 data. Never use
the reset-mode command against production.
