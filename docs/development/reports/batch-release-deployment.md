# Batch chest release and deployment (2026-10-12)

PR #6 merged at `febce1450bfa79076f9a72380e3e90bd9f43cf79` after exact-head Java17/21/25, harness and release-analysis checks passed. The feature fills a native staging chest and sells eligible items through one Deposit button; queued withdrawals stay bound to their screen. Prices and balances are unchanged.

Local evidence: 84 Java tests, 13 harness unit tests, 5 release-analysis tests, shaded verifier PASS; 23 actual-player checks, staged quit/rejoin return and no-reset clean restart PASS on isolated Paper1.21.11 build132/Java25. Tested snapshot SHA-256 `ec61033a51002fbe11000dc26423511a3e6d9d31e5cbfcc90e53cbbfe99d92fe`.

Publication workflow `38295432460` completed successfully and published [v2.2.0](https://github.com/mikestanaszak/GoldBag/releases/tag/v2.2.0). The downloaded JAR passed its published SHA-256 and the shaded packaged verifier.

- Installed: `/opt/stacks/maya-funtimes/data/plugins/GoldBag-2.2.0.jar`.
- Published/installed SHA-256: `bd4dfe9287313e3815ac2ea8d03497edf850f3cdb51f6524ec881e74482edcfa`.
- Consistent stopped-server backup: `/opt/stacks/maya-funtimes/data/plugin-backups/2026-10-12-batch-chest-2.2.0` (previous v2.1.0 JAR and complete GoldBag data directory).
- Graceful stop saved server data; plugin replacement was atomic and only one GoldBag JAR remains in `plugins/`. File mode/ownership preserved as 0644 and UID/GID1000.
- Startup: Paper26.3 build177 `fbec96a`, Temurin Java25.0.4.1+1; GoldBag2.2.0 enabled with SQLite and18 resources, Docker state running/healthy.
- Read-only checks: RCON reported2.2.0 and all18 approved rates. All4 existing account balance/revision rows unchanged across activation; pending0; SQLite quick_check ok. No other service/config was changed.
- Deployment state: `/tmp/goldbag-batch-deployment.json`. Production validation establishes startup/storage/rates; actual-player behavior was tested on the isolated Paper1.21.11 server as described above.

Native Paper26.3/Geyser gesture flows and native-client drag remain unverified. The initial draft baseline is persisted before staging is allowed; later native changes are snapshotted together. Abrupt-crash investigation uses baseline/latest evidence and SQLite pending operations manually, with no automatic refunds. All fixtures remained isolated; no production inventories or account balances were reset.

## Affordable grouped withdrawal patch

PR #7 merged at `132b65263c2c8de531b5074344f0fd3ad3347627` after exact-head checks. Grouped queued left-clicks clamp to remaining affordability/capacity; single clicks and command exact-count behavior stay unchanged. Full24 actual-player checks and same-artifact no-reset restart passed; see batch-player-tests.md for the snapshot and logs. Release run38296302348 succeeded and published v2.2.1; published checksum and packaged verifier passed.

Final activation PASS: GoldBag2.2.1 enabled on Paper26.3 build177/Java25 with SQLite and18 resources; RCON confirms2.2.1 and approved18 prices, Docker running/healthy. All4 account balance/revision rows preserved; pending0; quick_check ok. Final deployment state `/tmp/goldbag-batch-patch-deployment.json`. Backup `/opt/stacks/maya-funtimes/data/plugin-backups/2026-10-12-batch-chest-2.2.1` preserves v2.2.0 and its complete stopped plugin data. The published2.2.1 JAR was installed atomically with mode0644, UID/GID1000. SHA-256 `f8f03f5e50fb2437774f5b21a676755a4e098d03916813c963585fac796fc34e`. No live fixture was run.
