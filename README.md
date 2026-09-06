# GoldBag

A Minecraft resource-exchange plugin: deposit raw metals and mining resources into one Gold balance, then withdraw diamonds, redstone, or other configured resources.

## Development build

The SQLite-only rewrite lives on [`codex/goldbag-rebuild`](https://github.com/mikestanaszak/GoldBag/tree/codex/goldbag-rebuild). It has passed 69 automated tests and an actual Paper 1.21.11 build 132 smoke test, including player exchanges, banknotes, restart persistence, and recovery. Other versions and extended crash scenarios remain unverified; see the [compatibility evidence](docs/operations/compatibility.md). The original implementation remains in `java/` and `resources/` as reference; Maven builds only the new `goldbag-*` modules.

- [Current task board and verification status](docs/development/STATUS.md)
- [Resume development after an interruption](RESUME.md)
- [Approved plugin specification](docs/superpowers/specs/2026-09-05-goldbag-design.md)
- [Implementation tasks and API contracts](docs/superpowers/plans/2026-09-05-goldbag-rebuild.md)

The rebuild targets Spigot and Paper from Minecraft 1.17 onward, subject to a tested compatibility matrix. It uses SQLite without an external database, preserves original ore prices, and excludes smelted ingots. Commands, menus, payments, one-time banknotes, and a leaderboard are implemented and have passed the scoped integration reviews. Fabric and interest are outside this release.

For example, depositing 25 raw iron credits 50.00 G, enough to withdraw one diamond. Each player has one balance across all worlds, identified by their Minecraft UUID. Ordinary resources and reversible storage blocks share consistent rates; custom items are excluded from exchange.

Physical exchanges use a preview followed by confirmation. SQLite records pending operations before inventory changes. An uncertain outcome remains blocked for operator recovery; GoldBag does not guess whether to replay it. Minecraft inventory saves and database commits are separate, so the server recovery checks remain part of release validation.

## Build

Use JDK 17 or 21 and Maven 3.9 or newer:

```sh
mvn -B verify
```

The build targets Java 16 bytecode. Each Minecraft server still needs its own supported Java runtime. The packaged plugin will be `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; compilation alone does not certify in-game behavior.

On Windows, run `pwsh -File scripts/Invoke-GoldBagBuild.ps1` to also check the packaged libraries, SQLite restart persistence, and offline restore, and generate the JAR's SHA-256 checksum. GitHub Actions runs these package checks on Java 17 and 21 and retains the JAR, checksum, and test reports.

## Operator guides

- [Installation and preparing a local test server](docs/operations/installation.md)
- [Commands and permissions](docs/operations/commands-and-permissions.md)
- [SQLite backups and offline JSON restore](docs/operations/backup-and-restore.md)
- [Reviewing uncertain inventory operations](docs/operations/recovery.md)
- [Exact server compatibility and required checks](docs/operations/compatibility.md)

The plugin generates `config.yml`, `resources.yml`, and `messages.yml` beneath `plugins/GoldBag`. The live database defaults to `goldbag.db`; JSON is for export/restore. No database credentials or external database service are needed.

## Example exchange

With 25 ordinary raw iron in your main inventory:

```text
/goldbag deposit raw_iron 25
/goldbag confirm
/goldbag withdraw diamond 1
/goldbag confirm
```

The deposit credits 50.00 G and the diamond costs 50.00 G at default prices. `/goldbag` opens the menu, `/goldbag rates` lists prices, and `/goldbag cancel` cancels a preview. Items stay in your inventory until confirmation.

## Development layout

| Module | Purpose |
|---|---|
| `goldbag-core` | Exact currency, resource catalog, configuration |
| `goldbag-storage` | SQLite accounts, transactions, banknotes, recovery journal |
| `goldbag-plugin` | Bukkit/Spigot lifecycle, commands, menus, inventory coordination |

Work is split into independently resumable Luna subagent tasks. Completed work, exact checks, remaining steps, and checkpoint commits are recorded in the task board and per-task reports. No live account data, server worlds, or database files belong in Git.
