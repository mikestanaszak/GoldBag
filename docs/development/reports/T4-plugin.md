# T4 Bukkit plugin progress

Scope: `goldbag-plugin/src/**` and this report. No Git staging or commits performed; the controller owns shared build files and checkpoints.

## Completed milestones

- Added test-first pure command parsing for canonical commands, strict whole-number counts, exact money amounts, administrative reasons, and the legacy `/withdraw <amount>` banknote alias. `CommandParser` rejects malformed input before any storage call.
- Added `QuoteBook`, a one-active-quote-per-player registry with UUID quote IDs, expiry, catalog revision invalidation, and a testable clock.
- Added `ExchangeCoordinator.StateMachine` tests for PREPARED -> APPLYING -> COMPLETED and PREPARED -> CANCELLED, plus an asynchronous coordinator that invokes only the public `SqliteStore` pending-operation API.
- Added bounded single-thread `StorageExecutor`; storage tasks never perform Bukkit inventory reads or writes.
- Added immutable plugin configuration loading from generated files, including runtime Bukkit `Material` validation. Failed reload leaves the active snapshot unchanged; database filename and maximum balance changes require restart; successful reload expires quotes.
- Added lifecycle startup/shutdown, default file generation, SQLite opening/ownership handling, join account initialization, pending-operation warning, and orderly executor/store close.
- Added canonical `plugin.yml` commands/permissions and compatibility command wiring. Modern explicit permission denial wins over legacy `goldpurse.*` grants.
- Added holder-backed main/deposit/withdraw/top menus, plain-item inventory adapter limited to the main 36 slots, exact deposit/withdraw previews, capacity checks, durable PREPARED/APPLYING/complete sequencing, guarded inventory mutation, banknote PDC IDs, one-time note redemption, recovery list/resolve, JSON export, balances, rates, payments, admin balance changes, and leaderboard queries.
- Generated plugin resources: `config.yml`, `resources.yml`, `messages.yml`, and `plugin.yml`.
- Added paged withdrawal menu holders and click-to-preview behavior, legacy `/purse give|take|set` translation, and nonnegative admin `set` amount parsing.

## Verification

- `mvn -B -pl goldbag-plugin -am test` passed the plugin's 7 focused tests (parser: 3, quote book: 2, coordinator state machine: 2) before the concurrent T2 hardening edits changed storage tests.
- `mvn -B -pl goldbag-plugin -am package -DskipTests` compiled all 11 plugin source files and produced `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar` with the current sibling modules.
- After the concurrent T2 edit temporarily broke reactor compilation, direct Java 16 compilation of all plugin sources against the last built core/storage classes and Spigot 1.17 API also passed (`goldbag-plugin/target/manual-classes`).
- A later reactor test run was blocked by concurrent T2 changes outside this scope: storage tests currently report `StoreJson` immutable-list sorting, unresolved pending export/import validation, and note foreign-key setup failures. T4 source was not changed in response.

## Public integration decisions

- Uses only the published `Settings`, `Catalog`, `Resource`, `Money`, and `SqliteStore` APIs. No storage schema or JSON internals are referenced.
- `SqliteStore.prepare` payloads carry material/count evidence; `markApplying` is persisted before physical inventory mutation; completion is asynchronous. If a late callback or backend error occurs after physical mutation, the account remains blocked and the operation ID is reported for operator recovery.
- Console storage/admin commands use a stable synthetic audit UUID for recovery resolution; inventory commands require a player. Export always writes `goldbag-export.json` beneath the plugin data directory.

## Remaining work / risks

- No live Bukkit server smoke test has been run; EULA acceptance remains an operator action. Inventory event coverage and menu clicks need server validation.
- Deposit-all currently opens the selection menu so the player chooses an eligible resource; a single multi-resource durable plan is not yet implemented.
- Withdraw menu item clicks currently close the menu but do not launch a quantity preview; command workflow is complete.
- GUI payment/amount text entry is represented by command input; no conversation-based private text prompt is implemented.
- Sneak-right-click raw-gold shortcut is configured but not yet wired.
- Runtime reload reads files on the command thread; this is administrative configuration I/O and should be moved to a bounded task if reload latency matters.
- The current plugin test suite uses pure classes; Bukkit event and inventory behavior remain unverified until a compatible server harness is available.
