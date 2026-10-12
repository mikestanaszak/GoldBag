# Paper 26.3 live UI validation

## Scope

This report covers the isolated Paper 26.3 bridge attempt for the live player
commands and clickable confirmation UI. The isolated server directory is
`/tmp/goldbag-26.3-runtime`; it used loopback `127.0.0.1:25576`, Java 25,
Paper `26.3-175`, and the release GoldBag artifact. Production was not
restarted or written.

The server is stopped after the preliminary bridge checks. The bridge jars
remain only in the isolated server's `plugins/` directory for the final UI run.

## Bridge preparation

The requested production filenames `ViaVersion-5.12.0.jar` and
`ViaBackwards-5.12.0.jar` were absent. The production directory contained
5.12.1, so read-only copies of those exact available files were used:

- ViaVersion `5.12.1`, SHA-256
  `551265afd892e78bb869162e08b4fdd151c518e245fce60af36b0603ad695d76`.
- ViaBackwards `5.12.1`, SHA-256
  `dd72f7a459cbf1ea7862781dac92ed0ba738aa3cb3b7955e716ba379889d612a`.

Paper loaded both bridge plugins and logged:

```text
ViaVersion detected server version: 26.3 (777)
```

GoldBag `2.0.0` also enabled with SQLite and all 18 resources. This startup
check passed on Java 25 and the server listened on port 25576.

## Client bridge result

The existing Mineflayer 4.38.0 smoke was run with:

```text
GOLDBAG_HOST=127.0.0.1 GOLDBAG_PORT=25576 GOLDBAG_MC_VERSION=1.21.11
node scripts/server-tests/player-smoke.js
```

Both offline clients reached the world through ViaVersion/ViaBackwards and
received join messages. The bridge therefore removes the original
`Unsupported protocol version '777'`/missing-26.3-data failure at login.

The run failed before opening the GoldBag menu. During the harness's initial
operator reset and `/minecraft:clear`, Paper disconnected both clients with:

```text
Invalid move player packet received
```

The server log also reported a `moved too quickly` warning. No command,
confirmation, menu, inventory, note, or balance result from this run is valid
as UI compatibility evidence.

A narrow probe with Mineflayer physics disabled immediately after spawn did not
provide a workaround: the client was rejected during login with
`Failed to decode packet 'serverbound/minecraft:hello'`. The bridge/client
combination therefore remains unsuitable for the final UI matrix without a
working protocol-client configuration or a newer compatible client library.

## Older-client bridge workaround

The isolated server's persisted `white-list=true` setting was disabled through
the local console (`whitelist off`) for the bounded probe. Using Mineflayer
4.39.0 from the temporary runtime installation, clients `1.21.8` and `1.21.4`
both passed the bridge when physics was disabled synchronously in their spawn
listeners:

- `1.21.8`: spawned at `(6.5, 63, 6.5)`, physics changed `true` to `false`,
  `/goldbag rates` returned catalog chat in 1.8 seconds.
- `1.21.4`: spawned at `(2.5, 63, 8.5)`, physics changed `true` to `false`,
  `/goldbag rates` returned catalog chat in 0.9 seconds.

These probes show a usable protocol workaround for the final UI run. The
physics toggle must occur in the spawn event before the harness resolves its
connection promise; leaving default Mineflayer physics enabled caused the
earlier 1.21.11 bridge run to disconnect with `Invalid move player packet
received`.

## Limits and next run

- No tracked harness or production code was changed by this worker.
- The final clickable confirmation run remains pending the controller's UI
  implementation and final release JAR.
- The final run should use this isolated server with the copied 5.12.1 bridge
  jars, and should report any client protocol workaround separately from
  GoldBag behavior.
- Because the live clients did not survive initial movement, this report does
  not certify any Paper 26.3 player flow.

The isolated server logs are preserved at:

- `/tmp/goldbag-26.3-runtime/logs/server-bridge.log`
- `/tmp/goldbag-26.3-runtime/logs/player-bridge-current.log`
- `/tmp/goldbag-26.3-runtime/logs/physics-probe.log`
- `/tmp/goldbag-26.3-runtime/logs/server-bridge-physics.log`
- `/tmp/goldbag-26.3-runtime/logs/older-client-probes-allow.log`
- `/tmp/goldbag-26.3-runtime/logs/server-bridge-older.log`

## Final controller validation after resume

The short bridge probes above were not a stable full-test workaround. The final bridge harness still disconnected during movement, so no Paper 26.3 player-flow pass is claimed. Tests were moved to an isolated native Paper 1.21.11 build 132 server at `127.0.0.1:25577`, with Temurin 25.0.4.1 and Mineflayer 4.39.0 using client 1.21.11. Fixture operator permissions were explicitly granted only on this isolated server.

The first full run reached UI deposit/cancel, exact count, withdrawal, Deposit All, payment and banknote issuance/redemption. It stopped when a copied banknote activation did not credit B. A narrow repeat with the same B fixture, aiming the item-use click into the air, redeemed it and produced B=G15.00. The tracked harness now aims at air before using notes, avoiding incidental block interactions. No GoldBag source change was needed for that test-fixture issue.

Final full command:

```text
GOLDBAG_PORT=25577 GOLDBAG_MC_VERSION=1.21.11 node scripts/server-tests/player-smoke.js
```

Result: PASS, 17 actual-player checks. Coverage included real Confirm/Cancel clicks, preserving inventory/balance on cancel, exact-count input, deposit/withdrawal menus, Deposit All breakdown, payment prompt, invalid banknote input/retry, main/offhand notes, copied-note replay, custom/full-inventory/ingot/creative rejection, leaderboard and duplicate-confirm fallback rejection. The final fixture balances were A=G85.00, B=G15.00 with one issued G5.00 note.

After clean shutdown, the final versioned release JAR was installed into the isolated native server and the no-reset restart check ran:

```text
GOLDBAG_PORT=25577 GOLDBAG_MC_VERSION=1.21.11 GOLDBAG_RESTART_CHECK=1 node scripts/server-tests/player-smoke.js
```

Result: PASS. Balances and issued note survived; redemption changed A to G90.00 and B's replay left B at G15.00. The native server then stopped cleanly.

The final release JAR also started successfully on isolated Paper 26.3 build 175: GoldBag v2.0.0 enabled with SQLite and 18 resources, storage status and reload succeeded, rates printed, and shutdown completed. Both isolated servers are stopped. Their ports 25576 and 25577 are closed; test EULAs remain saved.

Final artifact SHA-256: `6dfdae69137b506b583fc1ccd0262530c3b4e3294c8ef616c579de7392a06c71`. Native 26.3 player protocol and the installed combination with Essentials/LuckPerms/Geyser remain manual-validation limits, not claims inferred from the 1.21.11 result.

Controller logs:
- `/tmp/goldbag-ui-native-live-final.log`
- `/tmp/goldbag-ui-native-restart.log`
- `/tmp/goldbag-ui-26.3-final-server.log`
