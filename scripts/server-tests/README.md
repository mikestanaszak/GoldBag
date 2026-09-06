# GoldBag Paper player smoke

This directory contains the Mineflayer protocol smoke intended for an isolated local Paper test server. Its default address is `127.0.0.1:25575`; the host is configurable. It does not start a server or accept an EULA. The full smoke resets its test accounts and inventories.

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

The reset-mode run sets both fixture accounts to zero, then checks the menu, rates, storage status, deposits, withdrawal, deposit-all, payment, main/off-hand notes, copied-note replay, custom item and full-inventory rejection, ingot and creative gates, leaderboard GUI, and duplicate confirmation. It ends with A at G85.00 and one unredeemed G5.00 note in A's inventory; B is G15.00.

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
