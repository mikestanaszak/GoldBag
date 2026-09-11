# GoldBag

Turn mining resources into one shared Gold balance, then spend it on diamonds, redstone, or other resources. For example, **25 raw iron = 50.00 G = one diamond** at the default prices.

GoldBag is a server-side Minecraft plugin for Bukkit/Spigot-compatible servers, including Paper. Players do not need a client mod. Balances are stored locally in SQLite, with no database service or credentials to configure.

## Current status and compatibility

The SQLite rebuild is merged into `main`. The current version is **2.0.0-SNAPSHOT**, a development build.

| Platform | Status |
| --- | --- |
| Paper 1.21.11 build 132, Java 21, Windows | Passed local server and automated player smoke tests |
| Other Bukkit/Spigot-compatible builds from Minecraft 1.17 onward | Targeted by the API; exact builds still need testing |
| Fabric, Forge, vanilla servers, and Realms | Not supported |

Recorded validation includes 69 Java tests, five test-client lifecycle tests, and 16 actual-player checks, plus restart persistence, banknotes, operator recovery, and export/restore. Other server versions and extended crash scenarios remain unverified. See the [compatibility matrix](docs/operations/compatibility.md) for exact evidence and limits.

## Features

- One balance per Minecraft UUID, shared across worlds, with exact cent amounts.
- Resource deposits and withdrawals through commands and menus, with a preview and confirmation before inventory changes.
- Player payments, one-time redeemable banknotes, and a balance leaderboard.
- Configurable prices, messages, permissions, and transaction limits.
- Local SQLite storage, JSON exports, and an offline restore tool.
- A recovery journal that blocks uncertain inventory operations for operator review.

Smelted ingots, nuggets, refined metal blocks, and custom items are excluded from exchange. Creative and spectator exchanges are disabled by default. There is no interest or MySQL backend.

## Default prices

Each price applies to both depositing and withdrawing one item. The original rates are preserved for coal, redstone, lapis, and raw metals.

| Resource | Gold per item |
| --- | ---: |
| Coal | 0.20 G |
| Redstone | 1.00 G |
| Lapis lazuli | 1.00 G |
| Raw copper | 1.00 G |
| Raw iron | 2.00 G |
| Raw gold | 5.00 G |
| Diamond | 50.00 G |
| Emerald | 25.00 G |
| Nether quartz | 1.00 G |
| Amethyst shard | 0.50 G |

Reversible storage blocks for coal, redstone, lapis, raw metals, diamonds, and emeralds exchange at nine times the item price. Quartz and amethyst blocks are excluded. This gives 18 enabled resource entries. Edit `resources.yml` to customize the catalog and prices.

## Build and install

1. Check out `main` and build from the repository root with JDK 17 or 21 and Maven 3.9 or newer:

   ```sh
   mvn -B verify
   ```

2. Stop your server and make a backup. Copy `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar` into its `plugins` directory.
3. Start the server and check the console for successful GoldBag startup. Stop it again to review the generated files in `plugins/GoldBag/`:

   | File | Purpose |
   | --- | --- |
   | `config.yml` | Storage, currency, limits, and feature settings |
   | `resources.yml` | Enabled resources and exchange prices |
   | `messages.yml` | Player-facing messages |
   | `goldbag.db` | Live SQLite database; do not edit by hand |

4. Start the server, join, and use `/goldbag` to open the menu. Test an exchange before opening the economy to players.

The plugin targets Java 16 bytecode; use the Java runtime required by your chosen server build. Check the compatibility matrix above before choosing a server.

On Windows, `pwsh -File scripts/Invoke-GoldBagBuild.ps1` also verifies the packaged libraries, SQLite restart persistence, and offline restore, and generates a SHA-256 checksum. [GitHub Actions](https://github.com/mikestanaszak/GoldBag/actions/workflows/ci.yml) runs package checks on Java 17 and 21 and retains the JAR, checksum, and test reports as build artifacts.

See the [installation guide](docs/operations/installation.md) for the full setup procedure and local test-server preparation. Existing installations of the original plugin need a migration plan: this build has no legacy database importer, and old handwritten banknotes cannot be redeemed.

## Your first exchange

With 25 ordinary raw iron in your main inventory:

```text
/goldbag deposit raw_iron 25
/goldbag confirm
/goldbag withdraw diamond 1
/goldbag confirm
```

The deposit credits 50.00 G and the diamond costs 50.00 G. Items stay in your inventory until confirmation. Use `/goldbag cancel` to discard a preview.

| Command | Purpose |
| --- | --- |
| `/goldbag` | Open the menu |
| `/goldbag balance` | Check your balance |
| `/goldbag rates` | List resource prices |
| `/goldbag deposit all` | Preview depositing eligible inventory resources |
| `/goldbag pay <player> <amount>` | Send Gold to an existing account |
| `/goldbag note <amount>` | Preview creating a banknote; confirm to issue it |
| `/goldbag top` | View the leaderboard |

Right-click an issued banknote to redeem it once. See [commands and permissions](docs/operations/commands-and-permissions.md) for withdrawals, administration, aliases, and permission nodes.

## Backups and recovery

SQLite is the live database; JSON is an export/restore format. Keep the database local to one running server. Use the [backup and offline restore guide](docs/operations/backup-and-restore.md) when moving or restoring data.

Minecraft inventory saves and database commits are separate. GoldBag records pending physical operations before changing inventory and blocks uncertain outcomes for an operator to investigate. Follow the [recovery guide](docs/operations/recovery.md) to resolve them without guessing whether items were delivered.

## Development

| Module | Purpose |
| --- | --- |
| `goldbag-core` | Exact currency, resource catalog, configuration |
| `goldbag-storage` | SQLite accounts, transactions, banknotes, recovery journal |
| `goldbag-plugin` | Bukkit/Spigot lifecycle, commands, menus, inventory coordination |

Maven builds the `goldbag-*` modules. The superseded implementation has been removed from the working tree; the [original source remains available in Git history](https://github.com/mikestanaszak/GoldBag/tree/45c349257a1f8a1e87683a7e24d0d6befdef6ad4).

- [Approved specification](docs/superpowers/specs/2026-09-05-goldbag-design.md)
- [Implementation plan and API contracts](docs/superpowers/plans/2026-09-05-goldbag-rebuild.md)
- [Task board, verification results, and review reports](docs/development/STATUS.md)
- [Resume work after an interruption](RESUME.md)
- [Player smoke-test tooling](scripts/server-tests/README.md)

Development progress is saved in tracked reports and checkpoint commits. Keep live account data, server worlds, and database files out of Git.
