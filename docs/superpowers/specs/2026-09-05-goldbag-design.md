# GoldBag — Plugin specification

Version: 1.1 draft • Date: September 5, 2026

## 1. Purpose and agreed direction

GoldBag is a Minecraft Java Edition server plugin that lets players deposit mining resources into a virtual purse and spend that balance to withdraw other resources. For example, a player can deposit raw iron and later withdraw diamonds or redstone.

**Confirmed requirement:** each player has one shared currency balance, as in the original plugin. GoldBag does not maintain separate coal, iron, gold, and diamond balances.

**Confirmed requirement:** SQLite is the only storage backend. No MySQL support or database-server setup is required.

**Confirmed scope:** preserve original resource prices, estimate new prices by rarity and availability, exclude smelted ingots, and include resource exchanges, player payments, secure banknotes, and a leaderboard. Use commands and a chest-style menu with exchange confirmations. No interest in the first release.

**Confirmed compatibility goal:** support as many Minecraft versions as possible while retaining the entire default item catalog. The owner has no existing server or live dataset to migrate.

**Recommended platform direction:** a Bukkit/Spigot-compatible plugin, tested on both Spigot and Paper, with Paper recommended for the owner's future server. The full catalog establishes a Minecraft 1.17 minimum. Fabric would require a separate mod implementation and is outside this draft's first-release scope. Platform choice remains a recommendation; the other decisions above are confirmed. This document specifies the plugin; implementation is a separate step.

## 2. Relationship to the original

Reviewed [mikestanaszak/GoldBag](https://github.com/mikestanaszak/GoldBag/tree/45c349257a1f8a1e87683a7e24d0d6befdef6ad4), commit `45c3492`.

The original contains a purse menu, configurable resource deposits and withdrawals, player payments, paper banknotes, balance commands, administrator adjustments, a top-balances list, and optional interest. Its default catalog includes coal, redstone, lapis, raw copper, raw iron, raw gold, and their storage blocks. It connects to MySQL; the README identifies flat-file storage as unfinished. Its plugin descriptor targets API 1.17.

The new version preserves the purse model and original resource prices, adds diamonds and other resources, and uses SQLite exclusively. It replaces floating-point balances with exact accounting and replaces banknote name/lore validation with database-backed identities. Automatic interest is excluded from the first release.

Sources: [original prices](https://github.com/mikestanaszak/GoldBag/blob/45c349257a1f8a1e87683a7e24d0d6befdef6ad4/resources/defaultValues.json), [commands and menu](https://github.com/mikestanaszak/GoldBag/blob/45c349257a1f8a1e87683a7e24d0d6befdef6ad4/java/goldbag/goldbag/GoldBagCommands.java), [event handlers](https://github.com/mikestanaszak/GoldBag/blob/45c349257a1f8a1e87683a7e24d0d6befdef6ad4/java/goldbag/goldbag/EventHandlers.java).

## 3. Platform and scope

- Server-side Java plugin; players do not need a client mod or resource pack.
- Target stable Minecraft Java releases from **1.17 through the latest stable release available when implementation is tested**, across Spigot and Paper where builds are obtainable. Include the 1.17, 1.18, 1.19, 1.20, 1.21, and subsequent release families. Minecraft versions before 1.17 lack required raw-metal and amethyst items and are excluded; do not replace those items or silently reduce the default catalog.
- Build against the oldest required public Bukkit/Spigot API and use a traditional `plugin.yml`. Prefer one compatible JAR; isolate API differences in small compatibility adapters. If verified incompatibilities require separate JARs, provide clearly labeled version ranges with identical gameplay and storage semantics. Do not rely on Paper-only APIs in shared plugin behavior or on internal Minecraft server classes.
- Pin exact server builds and their supported Java runtimes in a compatibility matrix. Plugin bytecode and bundled dependencies must work with the oldest runtime in their advertised range; newer servers use the runtime required by that server. Do not impose Java 25 on all older servers merely because current Paper requires it.
- Test every server/version combination advertised as supported. Prioritize the oldest release, API transition releases, and the latest patch in each release family, then expand across intervening patches. Mark combinations without test coverage as unverified and document unavailable server builds. Future releases and snapshots are not automatically supported.
- Compatibility means the plugin runs on those server versions. Allowing differently versioned clients to join one server is a separate server capability, outside GoldBag's scope.
- One running Minecraft server per local GoldBag database. Network-shared databases and balances are outside scope.
- Accounts use player UUIDs. Names are display and lookup information; changing a name does not create a new purse. Changing a server's authentication/UUID mode requires an explicit migration.
- All worlds share each player's purse. Creative and spectator players cannot exchange resources or issue/redeem notes by default. No special behavior on mining or item pickup.
- Default starting balance is zero. Virtual balances survive player death. Banknotes are physical bearer items and follow normal item loss and death rules.

The catalog's version floor follows Minecraft's [1.17 release notes](https://www.minecraft.net/en-us/article/caves---cliffs--part-i-out-today-java). Consult [Paper's Java requirements](https://docs.papermc.io/paper/getting-started/) for each tested version. Paper supports Bukkit-style plugins; Fabric uses a different mod system and cannot directly load this plugin. See [Paper plugin documentation](https://docs.papermc.io/paper/reference/paper-plugins/) and [platform migration guidance](https://docs.papermc.io/paper/migration/).

Not included in version 1: MySQL, live legacy-database migration, a Fabric/Forge mod edition, automatic interest, dynamic market prices, bag upgrades, resource-specific accounts, auction houses, automatic deposits, Vault integration, Folia support, or a web dashboard.

## 4. Currency and resource exchange

### Shared balance

The displayed currency is **Gold**, abbreviated **G**. One raw gold is initially worth 5.00 G, preserving the original scale; Gold is an accounting unit, not a claim that one G equals one gold item.

Store currency as signed 64-bit integer minor units: `1.00 G = 100 units`. Parse input exactly with at most two decimal places. Do not use floating-point arithmetic. Reject negative amounts, zero transaction amounts, non-numeric input, excess precision, and overflow. Administrator `set` may use zero. The default maximum purse balance is 1,000,000,000.00 G; an operation that exceeds it fails in full.

### Default catalog

Prices are per item. Deposit and withdrawal prices are equal by default, with no fee. The first six rows preserve original prices. The remaining rows are initial estimates chosen under the owner's direction to use rarity and availability; server owners can rebalance them.

| Resource | Material ID | Deposit / withdrawal | Storage block | Block price |
|---|---|---:|---|---:|
| Coal | `COAL` | 0.20 G | `COAL_BLOCK` | 1.80 G |
| Redstone | `REDSTONE` | 1.00 G | `REDSTONE_BLOCK` | 9.00 G |
| Lapis lazuli | `LAPIS_LAZULI` | 1.00 G | `LAPIS_BLOCK` | 9.00 G |
| Raw copper | `RAW_COPPER` | 1.00 G | `RAW_COPPER_BLOCK` | 9.00 G |
| Raw iron | `RAW_IRON` | 2.00 G | `RAW_IRON_BLOCK` | 18.00 G |
| Raw gold | `RAW_GOLD` | 5.00 G | `RAW_GOLD_BLOCK` | 45.00 G |
| Diamond | `DIAMOND` | 50.00 G | `DIAMOND_BLOCK` | 450.00 G |
| Emerald | `EMERALD` | 25.00 G | `EMERALD_BLOCK` | 225.00 G |
| Nether quartz | `QUARTZ` | 1.00 G | Not enabled | — |
| Amethyst shard | `AMETHYST_SHARD` | 0.50 G | Not enabled | — |

Pricing rationale: diamond at 50.00 G is ten raw gold or 25 raw iron, making it a premium exchange. Emerald at 25.00 G balances its rare ore form against availability through trading. Quartz at 1.00 G is a common resource that requires Nether access, while renewable amethyst starts at 0.50 G. These are gameplay estimates, not measured mining-yield ratios. Review them in survival playtesting while keeping the original prices fixed.

**Smelted ingots are not exchangeable in either direction.** Reject configurations that enable iron, gold, copper, or netherite ingots. Also exclude nuggets and compacted refined-metal forms such as iron/gold/copper blocks, so the raw-only metal rule cannot be bypassed through crafting. Raw-metal blocks remain enabled as listed above.

Ore blocks such as `IRON_ORE`, deepslate ores, ancient debris, and other netherite forms are disabled by default. Raw ore items such as `RAW_IRON` are distinct from mined ore blocks. Administrators can configure additional vanilla materials subject to the refined-metal exclusions. Quartz and amethyst blocks are not assumed to be reversible storage forms.

Example: deposit 25 raw iron for 50.00 G, then withdraw one diamond for 50.00 G. Alternatively, deposit one raw gold for 5.00 G and withdraw five redstone. Unspent fractional currency stays in the purse.

### Exchange rules

- The server creates withdrawal items and consumes deposited items; there is no finite shop inventory or requirement that someone previously deposited that resource.
- Each resource has independent `deposit-enabled`, `withdraw-enabled`, `deposit-price`, and `withdraw-price` settings. Names, menu ordering, and material aliases are configurable.
- For a resource enabled in both directions, its withdrawal price must be greater than or equal to its deposit price.
- Listed reversible storage blocks derive both prices from nine base items. Independent block overrides are rejected to prevent a crafting/exchange loop.
- Withdrawal quantities are whole item counts. `max` means the minimum allowed by available balance, inventory capacity, and the transaction limit; display the result before confirmation.
- Exchanges accept only ordinary vanilla items. Reject renamed items, lore, enchantments, custom persistent data, or other non-default item components. Never silently turn a custom item into generic currency. Banknotes use a separate redemption path.
- These rules prevent direct GoldBag round-trip profit at unchanged prices. They do not balance farms, villager trades, custom recipes, or other plugins. The catalog is intentionally server-configurable; emeralds can be disabled independently.
- Rate changes affect future transactions only. Balances and issued banknote values are not rescaled. Existing quotes expire on a catalog reload.

## 5. Player experience

### Main menu

`/goldbag`, `/gb`, and `/purse` open a chest-style menu with Balance, Deposit, Withdraw Resources, Pay Player, Create Banknote, and Top Balances. Labels distinguish withdrawing resources from creating a banknote.

Default access is by command. An optional sneak-right-click shortcut with raw gold can be enabled; it must not consume the item and must ignore duplicate off-hand interaction events.

### Deposit

1. Show eligible resources in the player's inventory and their deposit values.
2. Let the player select a resource and quantity, or select all eligible inventory resources.
3. Show the exact items consumed, currency credited, and resulting balance.
4. On confirmation, revalidate inventory, prices, permissions, and account state, then complete the exchange.

Use a selection menu: items remain in the player's inventory until confirmation. Menu icons are display items, not storage slots. Closing or cancelling a preview changes nothing. Deposit-all examines the main 36 inventory slots, not armor, the off-hand, cursor items, or nested containers.

### Withdrawal

Show a paginated catalog with prices and affordable quantities. Provide quantity buttons for 1, 16, 64, and max, plus an exact quantity command. Confirm the item count, total cost, and resulting balance. Insufficient funds or capacity rejects the whole request; do not scatter overflow items on the ground. Commands use the same preview/confirmation workflow as the menu for physical exchanges.

### Payments and banknotes

- Pay an online or offline player who already has a GoldBag account. Resolve names using recorded identities; ambiguous names require a UUID. Self-payments are rejected. Sender debit and recipient credit commit together.
- A banknote represents a fixed amount deducted from its creator's purse. Any player holding it can redeem it. Each note has a random unique ID stored in persistent item data and a corresponding database record. The database supplies its amount and redemption status; display text is cosmetic.
- Issue notes only when there is inventory capacity. Right-click redeems a valid note once. Copies of the same note ID cannot redeem again, including concurrent attempts. Forged names/lore and unknown IDs give no credit.
- Lost or destroyed notes are not automatically refunded because they may have been traded. Administrator recovery requires an audited action that invalidates the original note before any replacement or credit.
- GUI text entry for amounts and recipients is private, cancellable, and expires after 60 seconds. Recheck funds when executing, not only when asking for input.

### Commands and permissions

| Command | Behavior | Permission |
|---|---|---|
| `/goldbag` | Open menu | `goldbag.use` |
| `/goldbag balance [player]` | View own balance; others require additional permission | `goldbag.balance`, `goldbag.balance.others` |
| `/goldbag rates [material]` | Show deposit and withdrawal prices | `goldbag.use` |
| `/goldbag deposit <material> <count>` or `deposit all` | Preview deposit | `goldbag.deposit` |
| `/goldbag withdraw <material> <count|max>` | Preview resource withdrawal | `goldbag.withdraw` |
| `/goldbag confirm` / `/goldbag cancel` | Confirm/cancel the current physical exchange quote | Same permission as the quoted operation |
| `/goldbag pay <player|uuid> <amount>` | Transfer currency | `goldbag.pay` |
| `/goldbag note <amount>` | Preview banknote creation | `goldbag.note` |
| Right-click banknote | Redeem note | `goldbag.note` |
| `/goldbag top [page]` | Balances, ten players per page | `goldbag.top` |
| `/goldbag admin <give|take|set> <player|uuid> <amount> <reason>` | Adjust an existing account | `goldbag.admin.balance` |
| `/goldbag reload` | Validate and apply prices, messages, and UI settings | `goldbag.admin.reload` |
| `/goldbag storage status` | Show active backend, health, schema, and pending operations | `goldbag.admin.storage` |
| `/goldbag storage export` | Create a versioned JSON export in the plugin data folder | `goldbag.admin.storage` |

Ordinary use permissions are granted by default; others' balances and administrator permissions default to operators. All entry points recheck permissions. Console can run administration, rates, top, storage, and targeted balance queries; player inventory operations require a player. Administrator `take` cannot overdraw an account.

Retain `/balance`, `/money`, `/pursetop`, and `/withdraw <amount>` as optional compatibility aliases. The last retains its old banknote meaning. Translate `/purse give|take|set` to administrator balance actions, requiring a reason in the new version. Legacy permission grants `goldpurse.use` and `goldpurse.admin` remain compatibility grants, with explicit modern permission denials taking precedence. Document potential command conflicts; `/goldbag ...` is the canonical entry point.

## 6. Storage and configuration

### Selected storage

Use SQLite only. There is no backend selector, MySQL driver, connection pool, database credential configuration, or cross-backend migration utility. JSON is an export/import format, not the live balance store. The database file is not intended for hand editing.

SQLite is a local file database with no separate server. Use `plugins/GoldBag/goldbag.db`, with any SQLite journal/WAL companion files managed by the database. Resource configuration stays human-readable. See [SQLite application-file use](https://www.sqlite.org/whentouse.html) and [Paper database guidance](https://docs.papermc.io/paper/dev/using-databases/).

### Configuration shape

Illustrative `config.yml`:

```yaml
config-version: 1
storage:
  file: goldbag.db                # SQLite; changing path requires restart
currency:
  name: Gold
  symbol: G
  max-balance: "1000000000.00"
exchange:
  quote-timeout-seconds: 30
  max-items-per-transaction: 2304
  allow-creative: false
  allow-spectator: false
menu:
  sneak-right-click-raw-gold: false
banknotes:
  enabled: true
compatibility:
  legacy-aliases: true
```

Illustrative entry in `resources.yml` (all enabled defaults come from the catalog above):

```yaml
resources:
  raw_iron:
    material: RAW_IRON
    deposit-enabled: true
    withdraw-enabled: true
    deposit-price: "2.00"
    withdraw-price: "2.00"
    storage-block: RAW_IRON_BLOCK
    items-per-block: 9
```

Validate the full configuration before activation: material IDs on the running server, duplicate mappings, supported items, prohibited refined-metal items, prices, precision, derived block prices, numeric bounds, and storage path. Invalid startup configuration disables GoldBag with an actionable error. Invalid reload keeps the previous configuration. Storage path, currency scale, and database schema changes require restart; reject them during reload.

### Persistence contract

- Create the SQLite file and schema automatically on first startup. Package a SQLite driver compatible with the supported Java range; do not assume every supported Spigot/Paper version bundles a usable driver.
- Store accounts, immutable transaction records, issued notes, pending inventory operations, and schema version. Use database constraints and transactions to enforce invariants.
- An account contains UUID, last known name, integer balance, revision, and timestamps.
- Each operation has a unique ID, type, actor, affected accounts, exact deltas, before/after balances, time, reason, and applicable material/count/catalog revision. Reusing an operation ID must return the original result without applying it again.
- Payments have one operation with two account entries. Notes persist ID, amount, issuer, issue time, status, and redemption details. Pending inventory operations also store the intended item change, inventory evidence, and recovery status.
- Perform database and file I/O away from the game thread. Inventory reads and writes occur on the server thread. Use bounded queues and timeouts; return a busy message when overloaded. Paper explains this scheduling split in its [scheduler guidance](https://docs.papermc.io/paper/dev/scheduler/).
- On database failure, stop mutations and show temporary unavailability. Never interpret a failed read as a zero balance or silently replace a damaged database with an empty one.
- Enforce a single active server owner for each database. A second instance must refuse to enable mutations. Keep the database on local storage rather than a network share.
- Export accounts, ledger, notes, schema metadata, and pending operations as versioned JSON. Encode integer currency values as decimal strings for lossless interchange.
- Provide offline JSON import with dry-run reporting for restoring or moving this version's data. Require maintenance mode, a consistent backup, and an empty destination. Verify IDs, row counts, balances, note liabilities, and unresolved operations before activating the destination. Keep the source unchanged for rollback; do not merge live datasets.
- Back up SQLite using a consistent database snapshot or a cleanly stopped server. Never copy only the main database file while an active WAL contains uncheckpointed changes. Moving a clean data directory to a new host must preserve all GoldBag account and note identities.

## 7. Correctness and recovery

All currency changes must be durable, auditable, and protected against duplicate execution. Balance checks and updates happen in the same database transaction. Serialize operations affecting the same account and use a consistent lock order for payments.

Physical inventory changes and a database commit are not one atomic transaction. A database transaction alone is not a guarantee against duplication or item loss across a hard crash. The implementation must use a durable operation journal and explicitly handle this boundary:

1. Validate and quote without moving items; allow only one active quote per player.
2. On confirmation, reserve the account operation, record its item and balance intent durably, and temporarily guard relevant inventory interactions.
3. Revalidate on the game thread immediately before changing items. Include clicks, shift-clicks, dragging, hotbar swaps, off-hand swaps, drops, and duplicate event callbacks in the guard.
4. Apply the inventory step and persist operation progress. Complete the associated balance/note mutation once, then release the guard and report success.
5. On known failure before any item change, cancel without charging. On a later known failure, compensate only when the recorded state proves what occurred.
6. If a crash, disconnect, death, timeout, or lost database response makes the outcome uncertain, retain the pending record and block further GoldBag mutations for affected accounts until reconciled. Do not automatically repeat an item delivery or refund based only on a missing success response.

Restart recovery must process pending records before releasing affected accounts. Proven incomplete steps can be completed or compensated idempotently; ambiguous inventory outcomes require an operator report and an audited resolution. A timeout must not release an account while a late asynchronous callback could still apply its operation. Correct recovery must not roll back unrelated player inventory changes.

For note redemption, the unredeemed-to-redeemed transition and purse credit commit atomically. Remaining physical copies are invalid after that transition. Menus are identified by plugin-owned inventory holders/session IDs, never by title strings or icon lore.

Database-only operations such as payments can be fully atomic. The first release must document the remaining hard-crash limits of vanilla inventory persistence and demonstrate that uncertain physical operations are surfaced for recovery rather than blindly replayed.

## 8. Internal design

Keep these responsibilities separate:

| Component | Responsibility |
|---|---|
| Configuration and resource catalog | Validated, immutable price snapshots; aliases and block relationships |
| Economy service | Exact money arithmetic, balance rules, payments, and administrator changes |
| Exchange coordinator | Quotes, inventory checks, serialization, journal stages, and compensation |
| Banknote service | Issue/redeem identities and enforce one-time redemption |
| SQLite repository | Transactional operations and queries, isolated from gameplay rules |
| Server compatibility layer | Version-dependent API access for menus, item validation, and event handling |
| Commands and menus | Input, permission checks, previews, and messages; call services |
| Recovery and data tools | Backups, JSON exports/imports, pending-operation inspection and audited resolution |

Keep business rules separate from SQLite queries and server-version differences without building an unused multi-backend framework. Cache catalog data in memory; never reload the price file per click. Treat the database as authoritative for money, and never overwrite newer balances from a stale cache. Package dependencies so the default installation needs only the plugin JAR and a supported server.

## 9. Installation and compatibility documentation

The owner is starting a new server, so first-release installation assumes a fresh GoldBag data directory. There is no legacy database importer. Preserve familiar command aliases and original resource values as described above, without adding a dependency on the original plugin.

- Recommend Paper for the owner's server and provide installation instructions for both tested Paper and Spigot builds: stop server, place the appropriate GoldBag JAR in `plugins`, start server, then review generated settings and prices.
- Document which Java runtime and GoldBag artifact to use for each tested server range. Explain that the plugin does not install into Fabric's `mods` folder, vanilla servers, or Minecraft Realms.
- Include a local test-server setup guide so exchanges can be tried before choosing a hosting provider. Server provisioning and purchasing hosting are separate tasks, not plugin implementation requirements.
- Include backup/restore instructions and explain that server-version compatibility does not guarantee that a newer Minecraft world can be downgraded. Treat server/world downgrades separately from GoldBag database schema compatibility.
- Keep SQLite schema upgrades versioned, with a backup before migration. An older GoldBag version encountering a newer schema must refuse mutations rather than guess at compatibility.
- Legacy banknotes from the hand-written plugin contain no trusted unique issuance record and are not redeemable in this release.

## 10. Acceptance criteria

The first release is ready when these checks pass on SQLite and across every server/version combination advertised in the compatibility matrix. Run the full economy and recovery suite once per distinct compatibility implementation, and startup, catalog, exchange, note, and inventory-interaction checks on each advertised combination:

1. A fresh installation creates SQLite storage and a zero-balance account without external database setup. Restart preserves balances, notes, and transaction history.
2. Depositing 25 raw iron credits exactly 50.00 G; withdrawing one diamond returns the purse to zero and gives exactly one ordinary diamond.
3. Depositing one raw gold permits five redstone; ten coal credit exactly 2.00 G. Block/item conversions preserve their configured nine-to-one value.
4. Cancelled or expired previews change nothing. A catalog reload invalidates pending quotes, and failed reload leaves the active catalog intact.
5. Unsupported/custom items and smelted ingots are untouched. Ingot withdrawals and catalog entries enabling excluded refined-metal forms are rejected. Deposit-all consumes only eligible resources from the designated inventory slots.
6. Insufficient balance, a full inventory, excessive amounts, malformed input, and permission failures leave inventory and balances unchanged.
7. Repeated confirmation, concurrent payment/withdrawal attempts, and all supported inventory interaction paths cannot apply the same transaction twice or overdraw an account.
8. Payments to known offline players work and preserve total currency. Name changes retain the same account.
9. Creating a note deducts its value once. Redemption credits once. Renamed paper, edited display values, copied note IDs, and concurrent redemptions cannot create currency.
10. Simulated database failures and server stops at every journal stage produce either a proven final state or an explicit unresolved operation. Recovery never blindly redelivers or refunds uncertain items.
11. SQLite backup/restore and JSON export/import preserve account values, transaction IDs, issued-note state, and recovery records. Failed import leaves the source usable.
12. A second server cannot mutate the same database. Storage failure never creates an empty replacement economy.
13. On a documented reference machine, simulate 100 active players and 20 economy operations per second for ten minutes. No database/file I/O executes on the game thread; target p95 game-thread GoldBag work below 2 ms per operation and p95 end-to-end completion below 500 ms with healthy local SQLite storage. Publish measured results, not an untested performance claim.
14. The complete default item catalog is available at the oldest supported version and remains usable on every advertised version. Unsupported versions produce a clear diagnostic rather than partially enabling exchanges. No required feature depends on a Paper-only API when running on Spigot.

Deliverables: plugin JAR (or clearly labeled compatibility JARs only if needed), generated default configuration/catalog/messages, installation and command documentation, SQLite backup/restore and recovery instructions, a tested compatibility matrix, a local test-server guide, and automated economy/storage tests plus in-game interaction checks.
