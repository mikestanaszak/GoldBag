# Direct exchange release and activation — 2026-10-12

## Integrated and published

- Implementation PR: https://github.com/mikestanaszak/GoldBag/pull/4 — merged with commit 4b28e6c7d98537961f56d9e8c50864afeb1672d3 after passing exact-head Java 17/21/25, packaged, harness and release-analysis checks.
- Release workflow: https://github.com/mikestanaszak/GoldBag/actions/runs/38291887812 — PASS, including semantic-release publication.
- Published release: https://github.com/mikestanaszak/GoldBag/releases/tag/v2.1.0 . JAR plus checksum downloaded and verified; packaged descriptor/manifest/Java 16 bytecode/shaded SQLite/native/restart/restore checks PASS on Java 25.
- Published/installed JAR SHA-256: 24968ffcd814bbb955fccbfb0b499f4bb001d89b11b33440ff6d9ca9731eb493.

## Isolated player validation

83 Java tests, 11 harness unit tests and five release-analysis tests PASS. Twenty actual-player checks pass on isolated native Paper 1.21.11 build 132 / Temurin 25.0.4.1. Clean no-reset restart preserves balances and banknote; one redemption raises A from G85 to G90 and copied-note replay leaves B at G15. Logs: /tmp/goldbag-direct-player-live.log and /tmp/goldbag-direct-player-restart.log. Both isolated servers are stopped.

Automated player evidence covers shift-click, cursor stack and single-item deposit placement, direct left/right/shift withdrawals and repeated exchanges, balance/capacity/custom/game-mode gates, prices, payments and note identity/replay. The harness corrects Mineflayer 4.39 open-window inventory observation and its missing set_cursor_item observation. It does not alter plugin decisions. Native drag packets, death/disconnect during APPLYING and native 26.3/Geyser player gestures remain unverified. Unit/source review alone does not certify those flows.

## Existing local installation

Verified Docker Compose project maya-funtimes, service/container minecraft, /opt/stacks/maya-funtimes/data mounted at /data. Graceful stop logged player/world saves and GoldBag shutdown. Before replacement, copied the stopped plugin data/config/journal and old JAR to:

/opt/stacks/maya-funtimes/data/plugin-backups/2026-10-12-direct-exchange-2.1.0

Previous manually prepared v2.0.0 JAR hash: 6dfdae69137b506b583fc1ccd0262530c3b4e3294c8ef616c579de7392a06c71. New JAR installed atomically as plugins/GoldBag-2.1.0.jar; one GoldBag JAR remains in plugins. resources.yml updated only in approved deposit/withdraw price fields; material/enabled/storage configuration preserved. Existing account data was never reset or adjusted.

After start, the stack's existing Paper selection resolved to Paper 26.3 build 177 (fbec96a), Temurin 25.0.4.1. GoldBag 2.1.0 enabled with SQLite and 18 resources, server reached Done and Docker health is healthy. Read-only rates show all 10 approved unit values and 8 derived blocks at 9×. Four existing account id/balance/revision snapshots match exactly before/after; SQLite quick_check=ok, unresolved operations 0 before/after. Installed checksum equals published checksum. Other stack services/config and player inventories were not modified by fixture tooling.

Configured resource file SHA-256: 07fe4a8a8abf6824a53ea5829bcae64766c1698a81c1f0e0cb1e8eed72d373c8.

Production read-only startup/rate/account checks are not a native 26.3 or Geyser player experience pass. No second production restart was needed.
