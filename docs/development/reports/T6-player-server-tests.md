# T6 actual-player server smoke

## Scope and environment

This report records the loopback player protocol harness in `scripts/server-tests/`. It uses Mineflayer 4.38.0 from an ignored `.runtime/goldbag-server-tests` installation, Node 24.18.0, two offline clients, and no Bukkit classes in the client process. The authorized server is Paper `1.21.11-132` on Java 21 at `127.0.0.1:25575`; no external server or public account is used.

The bounded fixture names are `GoldBagSmokeA` and `GoldBagSmokeB`. The operator granted only those names access to `/give` and the audited balance reset commands. The harness resets both accounts to zero at the start of every run and always disconnects clients in `finally`, including a second-client connection failure.

## Harness behavior implemented

`player-smoke.js` currently exercises:

- two-player connection, canonical menu opening, live catalog rates, and storage status;
- exact 25 raw iron deposit for 50.00 G and one diamond withdrawal for 50.00 G;
- deposit-all with raw iron and raw gold using the default catalog prices;
- online payment from A to B;
- banknote issue, identity-component inspection, main-hand redemption, and off-hand redemption;
- renamed/custom raw iron rejection and full-inventory withdrawal rejection;
- leaderboard output.

The script uses live chat responses and Mineflayer inventory state, not Bukkit mocks. Node dependency setup is intentionally outside the repository source tree:

`npm install --prefix .runtime/goldbag-server-tests --no-save --no-package-lock mineflayer@4.38.0`

Run with:

`$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path; & 'C:\nvm4w\nodejs\node.exe' scripts/server-tests/player-smoke.js`

## Diagnostic checkpoint before plugin replacement

Two runs connected both players and passed these actual-server checks before reaching note redemption:

- canonical menu opened;
- rates listed all 18 enabled resources;
- storage status reported healthy with zero pending operations;
- account reset via audited admin actions;
- 25 raw iron deposited for exactly G50.00;
- one diamond withdrawn for exactly G50.00 and appeared in A's inventory;
- deposit-all consumed 25 raw iron plus one raw gold for G55.00;
- A paid B G5.00 and balances became A G50.00 / B G5.00;
- A deposited another 25 raw iron and reached G100.00;
- A issued a G10.00 banknote and balance became G90.00; Mineflayer observed a paper item with custom-data/custom-name/lore components.

Both runs then failed at the same point: after equipping that issued paper in A's main hand and calling Mineflayer `activateItem()`, no `Banknote redeemed.` message arrived and `/goldbag balance` remained G90.00. The server log recorded the issue but no redemption interaction. This is preserved as a production diagnosis for the separate `onInteract` review; this worker did not edit production. The likely platform detail is Paper's pre-cancelled air interaction and the plugin handler's `ignoreCancelled=true`, but that remains a reviewer-owned conclusion.

The second run ended at 09:54 with both clients disconnected by the harness's `finally` block. No client is active now. Root may stop/restart and replace the isolated plugin JAR before the next run.

## Remaining after the bounded fix

- Re-run the same harness after the plugin interaction fix; preserve the red reproduction if it remains.
- Complete note handoff/copy replay using the operator-only `item replace entity` command, then assert one credit across two actual clients.
- Complete custom-item/full-inventory/leaderboard assertions currently after the note section.
- Root will perform the authorized clean restart and verify final balances/notes and database persistence. No claim of exact-version compatibility is made until that result is recorded.

No EULA or server process was started by this worker; the operator supplied and authorized the already running isolated server.

## Harness additions awaiting the patched JAR

The next run also includes an operator-scoped duplicate-note replay: A issues a note, the server `/item replace entity` command copies the same physical note identity to B, B redeems it, and A retries the original. The expected result is one credit only. The final fixture then leaves one unredeemed G5.00 note in A's inventory and expects A=G85.00 / B=G15.00, allowing the controller to restart the server and verify both balances and note persistence without rerunning the reset step.

## Patched-JAR player evidence checkpoint

Against the controller's patched artifact SHA-256 `676E04E0112B2411FE10C95262EE3F2E5AE8E32FDEDCCF2B512DCC8CEC530472`, the second full run reached and passed:

- main-hand G10.00 banknote redemption (A returned from G90.00 to G100.00);
- off-hand G5.00 banknote redemption (A remained G100.00 after the round trip);
- copied-note replay: B redeemed the copied identity once to G15.00, while A's retry returned an unresolved/cancelled message and remained G90.00;
- renamed raw-iron rejection at preview;
- full-inventory withdrawal rejection;
- smelted iron-ingot catalog rejection;
- creative-mode exchange rejection followed by survival restoration.

That run stopped at the leaderboard assertion because `/goldbag top` opens a player GUI and emits no matching chat line. The harness has now been corrected to wait for and inspect the actual top inventory window. The production behavior was not changed by this harness correction.

## Final patched-JAR run

Command:

`$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path; & 'C:\nvm4w\nodejs\node.exe' scripts/server-tests/player-smoke.js`

PASS: 16 actual-player checks on Paper `1.21.11-132` with SHA-256 `676E04E0112B2411FE10C95262EE3F2E5AE8E32FDEDCCF2B512DCC8CEC530472`. This includes the repaired main/off-hand note paths, copied-note one-credit replay, custom item, full inventory, smelted ingot, creative gate, live top GUI, duplicate confirmation, and final persistence fixture. Both clients disconnected cleanly.

Final live fixture left by the script: `GoldBagSmokeA` balance G85.00 with one unredeemed G5.00 issued note in its inventory; `GoldBagSmokeB` balance G15.00. The controller should use the no-reset restart check below before running any further reset-mode smoke.

## Restart-check mode prepared

`GOLDBAG_RESTART_CHECK=1` bypasses the reset and runs against the fixture left by the full smoke. It expects A=G85.00/B=G15.00, copies A's persisted G5.00 note to B, redeems once for A=G90.00, and verifies B's replay cannot credit its account. `scripts/server-tests/package-lock.json` pins the Mineflayer 4.38.0 test dependency; `node_modules` remains outside the repository under `.runtime`.

The lockfile generation completed successfully; npm reported six moderate advisories in transitive Mineflayer test dependencies. These packages are used only by the loopback smoke harness and are not part of the Maven plugin artifact.

## No-reset restart check

After the controller's clean stop/restart and synthetic recovery fixture seeding, the isolated Paper server restarted with GoldBag enabled and the expected pending-recovery startup warning for the separate synthetic accounts. The no-reset command was run without account resets:

`$env:NODE_PATH=(Resolve-Path .runtime/goldbag-server-tests/node_modules).Path; $env:GOLDBAG_RESTART_CHECK='1'; & 'C:\nvm4w\nodejs\node.exe' scripts/server-tests/player-smoke.js; Remove-Item Env:GOLDBAG_RESTART_CHECK`

PASS: A reopened at G85.00 and B at G15.00; A's persisted G5.00 note was copied to B, redeemed once for A=G90.00, and B's replay returned an unresolved/cancelled message while B stayed G15.00. Both clients disconnected cleanly. Final live balances are A=G90.00 and B=G15.00.

The separate synthetic recovery fixtures and their console apply/cancel checks remain controller-owned. The actual-player matrix still does not claim crash-at-every-journal-stage or automated recovery resolution.
