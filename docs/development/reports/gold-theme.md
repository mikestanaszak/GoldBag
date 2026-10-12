# GoldBag gold theme

User requested `[GoldBag]` on all player messages, full gold coloring, and a more refined/fun experience. Shared formatting strips previous color codes and adds a gold prefix on each line; already prefixed custom text does not duplicate the prefix. Conversation prompts explicitly use the same formatter. Existing messages.yml wording remains user owned on installed servers; the theme applies automatically. New installs receive refreshed defaults.

Menus retain sizes, staging slots, navigation and transaction dispatch. Titles use `[GoldBag]`, item names gold and lore yellow. Deposit footer panes are yellow, instructions reflect batch staging, banknotes retain identity metadata and gain redemption instructions. Success messages add brief treasure/adventure wording while retaining amount/count diagnostics. Economy, persistence, rates, permissions and existing notes are unchanged.

Verification: focused formatter tests2; `mvn -B clean verify` and final `mvn -B verify` PASS (86 Java tests); packaged-JAR verifier PASS (716 base classes, major60,20 SQLite natives); harness13 and release-analysis5 PASS. Node/npm discovered from bundled runtime; plain npm was unavailable. Independent Luna review cleared the corrected conversation prompt bypass. Harness title expectations updated to branded titles.

Native Paper1.21.11 build132 / Java25 isolated loopback25579 theme probe PASS:12 gold/prefixed messages, error/help, main menu, both conversation prompts and invalid retry/cancel. Client JSON verified actual gold color. Server stopped cleanly. Logs `/tmp/goldbag-theme-live.log`; isolated fixture `/tmp/goldbag-theme-server`. Publication and activation complete:

- Source PR [#9](https://github.com/mikestanaszak/GoldBag/pull/9), exact head `2441164e946b8c94dd1a6b4573202b1fa7c764a3`, merged as `a7b33372adcdf13711a3e2b46d0bb534e5eb0768`. Exact-head Java17/21/25, packaged, harness and release-analysis checks passed.
- Main workflow [38296887510](https://github.com/mikestanaszak/GoldBag/actions/runs/38296887510) succeeded and published [v2.3.0](https://github.com/mikestanaszak/GoldBag/releases/tag/v2.3.0).
- Published checksum and packaged verifier PASS. Installed `/opt/stacks/maya-funtimes/data/plugins/GoldBag-2.3.0.jar`, SHA-256 `db609ddb329de795942c2ab8d26e9b06c780d80f28bb11f784f17246875665e4`,0644 UID/GID1000. Only one GoldBag JAR remains.
- Graceful Minecraft stop saved player/world data. Consistent complete GoldBag directory and previous2.2.1 JAR backup: `/opt/stacks/maya-funtimes/data/plugin-backups/2026-10-12-gold-theme-2.3.0`. Atomic replacement, full restart.
- Active RCON version2.3.0; Docker running/healthy. Production Paper26.3 build177 / TemurinJava25.0.4.1+1 enables GoldBag with SQLite and18 resources.
- Read-only comparison: all4 account balance/revision rows unchanged, pending0, SQLite quick_check ok. Config/resources/messages checksums unchanged. Installed custom wording is preserved while receiving the shared gold prefix. Deployment evidence `/tmp/goldbag-theme-deployment.json`.
- Native Paper26.3/Geyser visual/player flows remain manual; actual theme/protocol checks above passed on isolated native Paper1.21.11. No production fixtures or account resets.
