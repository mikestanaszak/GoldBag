# GoldBag

A Minecraft resource-exchange plugin: deposit raw metals and mining resources into one Gold balance, then withdraw diamonds, redstone, or other configured resources.

## Rebuild in progress

The SQLite-only rewrite lives on [`codex/goldbag-rebuild`](https://github.com/mikestanaszak/GoldBag/tree/codex/goldbag-rebuild). It is under development, not yet a verified server release. The original implementation remains in `java/` and `resources/` as reference; Maven builds only the new `goldbag-*` modules.

- [Current task board and verification status](docs/development/STATUS.md)
- [Resume development after an interruption](RESUME.md)
- [Approved plugin specification](docs/superpowers/specs/2026-09-05-goldbag-design.md)
- [Implementation tasks and API contracts](docs/superpowers/plans/2026-09-05-goldbag-rebuild.md)

The rebuild targets Spigot and Paper from Minecraft 1.17 onward, subject to a tested compatibility matrix. It uses SQLite without an external database, preserves original ore prices, excludes smelted ingots, and plans commands, menus, payments, one-time banknotes, and a leaderboard. Fabric and interest are outside this release.

## Build

Use JDK 17 or 21 and Maven 3.9 or newer:

```sh
mvn -B verify
```

The build targets Java 16 bytecode. Each Minecraft server still needs its own supported Java runtime. The packaged plugin will be `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; compilation alone does not certify in-game behavior.

## Development layout

| Module | Purpose |
|---|---|
| `goldbag-core` | Exact currency, resource catalog, configuration |
| `goldbag-storage` | SQLite accounts, transactions, banknotes, recovery journal |
| `goldbag-plugin` | Bukkit/Spigot lifecycle, commands, menus, inventory coordination |

Work is split into independently resumable Luna subagent tasks. Completed work, exact checks, remaining steps, and checkpoint commits are recorded in the task board and per-task reports. No live account data, server worlds, or database files belong in Git.
