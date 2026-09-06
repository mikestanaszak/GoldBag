# T6 local Paper server validation

## Authorization and scope

On 2026-09-06 the user explicitly instructed: "Set EULA to true and also test the server." This authorizes the isolated local test server's `eula=true` and startup, superseding the earlier no-acceptance boundary for this test. No hosting purchase or public deployment is involved.

Server directory: `C:\Users\mfsta\Documents\ChatGPT\GoldBag-local-tests\paper-1.21.11-132` (outside source repository).

- Official Paper stable Minecraft 1.21.11 build 132, commit `c5eb0790f199da6c38d0a650e1e5cd5415b28185`.
- Download metadata: `https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds`; [official download service documentation](https://docs.papermc.io/misc/downloads-service/).
- Server JAR SHA-256: `5FFEF465EEEB5F2A3C23A24419D97C51AFD7DBB4923FF42DF9A3F58BBA1CCFBA`, verified against official metadata before execution.
- GoldBag source `39c93b7`, JAR SHA-256 `43377E3C0C2E8AAB9F5AB22F4DD65A38B0353ED48363683CEFBC2F0ACBB4AE0E`.
- Windows 11 amd64; Eclipse Temurin Java 21.0.11+10; startup `java -Xms512M -Xmx2G -jar server.jar --nogui`.
- Network bound to `127.0.0.1:25575`, offline-mode solely for local automated protocol clients, query/RCON disabled, max four players, peaceful survival test world.
- `scripts/Prepare-GoldBagTestServer.ps1` prepared the directory. Controller then wrote `eula=true` and local-only server properties per the explicit user request.

## In progress

Initial startup and generated configuration validation are running. Luna worker owns `scripts/server-tests/**` and `T6-player-server-tests.md` for actual protocol-client exchange/menu tests. Controller owns server lifecycle, commands, logs, compatibility/status updates, and commits.

The server is a local validation fixture; do not certify any other Minecraft/Paper/Spigot build from its results. Exact results and remaining checks will be appended here and in the player-test report.

## First startup and console checks

- PASS: Paper startup completed, GoldBag enabled with SQLite and all 18 default resources. `plugins` listed GoldBag as enabled. Listening socket confirmed `127.0.0.1:25575`.
- PASS: `goldbag rates` printed original/default rates including raw iron G2.00 and diamond G50.00; ingots absent.
- PASS: `goldbag reload` with valid configuration; invalid `exchange.allow-creative` text rejected with a precise diagnostic while active rates remained available. Original config restored and successful reload repeated.
- PASS: `goldbag storage status` reported healthy with no pending operations; `goldbag storage export` wrote schema2 JSON; `goldbag recovery list` empty.
- PASS: console deposit rejected because inventory operations require a player.
- Setup-only observations: flat-world generation used default empty generator settings and logged `No key layers in MapLike[{}]` during initial world creation; server continued normally. Paper warns about deliberate offline mode and newer releases, and uses its Java profiler on Windows. These are not GoldBag exceptions. Initial mistyped `goldbag export` correctly returned unknown command; canonical `goldbag storage export` passed.
- Actual clients created GoldBagSmokeA/B accounts. Controller opped only those fixture users. Basic deposit, withdrawal and payment progressed; a banknote air-interaction check then failed. Investigation is scoped to event cancellation semantics versus protocol-client behavior; do not mark the banknote test passing yet. See the player-test and banknote-fix reports.

## Completed corrected-build checks (2026-09-06)

- Corrected source: `37fb85f`; independent banknote review: `3ba815e` (`T6-banknote-review.md`). The live defect was Bukkit's pre-cancelled air event being filtered by `ignoreCancelled=true`; the minimal fix admits default item-use air events while rejecting explicit denied item use and cancelled block interactions.
- Corrected JAR SHA-256: `676E04E0112B2411FE10C95262EE3F2E5AE8E32FDEDCCF2B512DCC8CEC530472`.
- Controller `mvn -B verify`: PASS, 69 tests (12 core, 17 storage, 40 plugin); packaged verifier PASS. GitHub Java 17/21 Maven and packaged checks PASS at `3ba815e`: [run 34040881714](https://github.com/mikestanaszak/GoldBag/actions/runs/34040881714).
- Actual protocol clients: 16 checks PASS, including main/top menus, catalog/status, exact deposits/withdrawals, deposit-all, payments, main/offhand notes, copied-note single credit, custom/full-inventory/ingot/creative rejection, and duplicate confirmation. See `T6-player-server-tests.md` for exact commands and diagnostic history.
- Clean restart PASS: A retained G85.00 plus an issued G5.00 note, B retained G15.00. The persisted note redeemed once for A=G90.00; B's copied-note retry left B=G15.00. Both clients disconnected.

## Synthetic recovery checks

After stopping the server cleanly, controller ran the independently reviewed helper:

`java -cp '.runtime/final-verifier;goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar' ServerRecoveryFixture 'C:\Users\mfsta\Documents\ChatGPT\GoldBag-local-tests\paper-1.21.11-132'`

It seeded two fresh accounts and clearly labeled APPLYING deposits of 200 cents, without modifying any physical inventory. These exercise startup quarantine and operator recovery, not an induced Minecraft crash.

- Apply account `GBRecoveryApply`, operation `3bcd4238-20ce-356e-bd4f-e365c176de92`.
- Cancel account `GBRecoveryCancel`, operation `680727ec-bbb9-37c2-b276-d1516e58b7aa`.
- Restart printed the expected pending-operation warning. `goldbag recovery list` exposed both operation IDs, player IDs, amount G2.00, APPLYING state, and synthetic evidence.
- `goldbag admin give GBRecoveryApply 1.00 should remain blocked` was rejected because of the unresolved pending operation; both accounts remained G0.00.
- `goldbag recovery resolve 3bcd4238-20ce-356e-bd4f-e365c176de92 apply synthetic fixture expected credit` resulted in G2.00.
- `goldbag recovery resolve 680727ec-bbb9-37c2-b276-d1516e58b7aa cancel synthetic fixture no inventory change` left G0.00.
- Repeating the already resolved apply was rejected and did not add money. Recovery list became empty.

## Export, restore, and final shutdown

- Live `goldbag storage export` PASS. Packaged `OfflineImport` validate-only and restore into a new `.runtime/server-final-restored.db` both PASS against that actual server export.
- Export summary: schema2, four accounts, 57 operations, 61 entries, 12 note records, 44 historical pending/journal records, two recovery audit records, aggregate balance G107.00, unresolved operations zero. Historical finalized journal rows are retained; their count is not the unresolved count.
- Restored account balances: A=9000 cents, B=1500, recovery apply=200, recovery cancel=0. Store `pending()` returned zero.
- Final `goldbag storage status` healthy, zero pending. `save-all flush` and `stop` completed at 10:07:17 local; GoldBag disabled cleanly, process exited zero. Reopening the stopped database succeeded and confirmed the same balances and no pending operations. Port 25575 no longer has a listener.
- `eula.txt` still contains `eula=true`. Server files and worlds are preserved outside the repository. Latest logs are at `C:\Users\mfsta\Documents\ChatGPT\GoldBag-local-tests\paper-1.21.11-132\logs\latest.log`; earlier runs are retained in that directory's rotated logs.

## Limits and remaining review

This is an automated smoke pass for exactly Paper 1.21.11 build 132 on Windows/Java 21. It does not certify other builds, actual crash-at-every-journal-stage inventory persistence, disconnect/death races during APPLYING, interactions with other permission/protection plugins, or broad Spigot compatibility. Those remain extended validation scenarios.

The final Luna review of the new JavaScript test harness hit the usage limit after all server tests completed. Production banknote and recovery-fixture reviews are already clear. Resume only the bounded harness review described in `T6-player-harness-review.md`; do not repeat completed implementation or server checks without a concrete reason.

Final source/test checkpoint `5ffc16d` is pushed to the feature branch. Its Java 17/21 Maven and packaged checks both passed: [run34063224382](https://github.com/mikestanaszak/GoldBag/actions/runs/34063224382). The remaining harness-review handoff is tracked in that checkpoint; no active server process remains.
