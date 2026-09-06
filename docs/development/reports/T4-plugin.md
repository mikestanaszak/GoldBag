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
- Added true multi-resource `deposit all` plans (material/count payload evidence), deposit-all menu action, quantity-one withdrawal menu previews with paging, configured sneak/raw-gold shortcut, fixed-path `storage import` integration, and `ExchangeCoordinator.execute` with main-thread/inventory ports.
- Added private Bukkit conversation prompts with 60-second timeout and local echo disabled for menu payment/banknote entry, and moved reload file parsing/validation to the bounded storage executor before applying the immutable snapshot on the main thread.
- Current focused reactor checkpoint: `mvn -B -pl goldbag-plugin -am test` passed core 12, storage 17, and plugin 14 tests (including four offline-import tests, the two real-SQLite coordinator execution tests, and the two-player quote revision regression).
- Added holder-backed quantity previews with 1/16/64/max buttons and a private exact-count prompt for both deposit and withdrawal menus. Menu selections route through the same quote and durable coordinator paths as commands.
- Wired `ExchangeCoordinator.execute` into deposit, withdrawal, note issue, and note redemption; the plugin now uses the production main-thread/inventory ports rather than a separate hand-rolled journal sequence. The coordinator tests cover pre-APPLYING changed inventory cancellation and post-APPLYING physical failure quarantine with real SQLite.
- Added inventory interaction guards for right-click, pickup, drag/click, drop, swap-hand, and guarded player paths; runtime config validates known storage block materials too. Storage executor shutdown now drains queued work for orderly disable.

## Verification

- Current focused verification: `mvn -B -pl goldbag-plugin -am test` passes core 12, storage 17, and plugin 14 tests (parser: 3, quote book: 3, state machine: 2, real-SQLite coordinator execution: 2, offline import: 4).
- Current full verification: `mvn -B verify` passes core 12, storage 17, and plugin 14 tests and rebuilds `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; the shade step emits the existing META-INF/MANIFEST overlap warning.
- Boundary hardening applied: startup stays unavailable until the asynchronous pending-operation health read succeeds and synchronous enable failures explicitly disable the plugin after cleanup; creative/spectator defaults gate quotes, confirmations, issuance, and redemption; confirmation rechecks the quoted permission and consumes the quote once; per-player operation tokens prevent duplicate confirmations or late callbacks from releasing another operation's inventory guard; both coordinator main-thread readiness checks revalidate online/dead state, game mode, permission, catalog revision, and physical inventory evidence; strict SnakeYAML message parsing rejects missing, duplicate, malformed, non-string, or empty message files; legacy command labels honor `legacy-aliases` while `/gb` remains available; storage futures have a 10-second deadline; export JSON and file writing run on the storage worker; live plugin import was removed in favor of the offline `OfflineImport` CLI, with `/goldbag storage import` directing operators to it.
- After the boundary hardening batch, `mvn -B -pl goldbag-plugin -am package -DskipTests` passed and rebuilt the shaded plugin. A subsequent focused reactor test was blocked by a concurrent T2 restore test failure (`uppercaseIdentityFieldsAreCanonicalizedOnRestore`); T4 source was not involved.
- Current-source integration fixes applied: per-player quotes no longer advance the catalog revision; menu holders retain page, displayed resource IDs, and revision; canonical `/goldbag withdraw` requires material plus count while standalone legacy `/withdraw <amount>` maps to a note; rates list all resources when no material is supplied; preview messages include exact item detail and resulting balance; export writes on the storage worker; live import delegates to the offline CLI.
- Durable physical evidence added: deposit, withdrawal, note issue, and note redemption PREPARE payloads now include deterministic main-36-slot `before=` and expected `after=` snapshots alongside the operation intent, so recovery inspection can identify the affected inventory state without relying on mutable Bukkit objects.
- Boundary review disposition: all findings applicable to the current source are addressed: coordinator readiness revalidates online/dead/inventory state immediately before each physical callback, operation-token guards release only their own operation, notes compare by PDC UUID rather than object identity, startup health is gated by the pending read, reload parsing is asynchronous, the executor drains on disable, messages are strict, and legacy alias disabling preserves canonical `/gb`.
- `mvn -B -pl goldbag-plugin -am package -DskipTests` compiled all 12 plugin source files (including the separately owned offline CLI) and produced `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar` with the current sibling modules.
- After the concurrent T2 edit temporarily broke reactor compilation, direct Java 16 compilation of all plugin sources against the last built core/storage classes and Spigot 1.17 API also passed (`goldbag-plugin/target/manual-classes`).
- One intermediate reactor run was blocked by concurrent T2 changes; storage round-two fixes are now integrated. The current focused and full reactor checks are green.

## Public integration decisions

- Uses only the published `Settings`, `Catalog`, `Resource`, `Money`, and `SqliteStore` APIs. No storage schema or JSON internals are referenced.
- `SqliteStore.prepare` payloads carry material/count evidence; `markApplying` is persisted before physical inventory mutation; completion is asynchronous. If a late callback or backend error occurs after physical mutation, the account remains blocked and the operation ID is reported for operator recovery.
- Console storage/admin commands use a stable synthetic audit UUID for recovery resolution; inventory commands require a player. Export always writes `goldbag-export.json` beneath the plugin data directory.

## Remaining work / risks

- No live Bukkit server smoke test has been run; EULA acceptance remains an operator action. Inventory event coverage and menu clicks need server validation.
- `/goldbag deposit all` creates one exact multi-resource quote and durable payload; the deposit menu also exposes both per-resource and all-eligible previews.
- Withdraw menu item clicks launch quantity previews with 1/16/64/max and private exact quantity input; canonical commands remain available for direct use.
- Menu payment and banknote amount entry use a private 60-second conversation with local echo disabled.
- Sneak-right-click raw-gold shortcut opens the main menu, does not consume raw gold, and ignores off-hand duplicate callbacks.
- Runtime reload file parsing and validation run on the bounded storage executor; only immutable snapshot activation and user messaging return to the main thread.
- The current plugin test suite uses pure classes; Bukkit event and inventory behavior remain unverified until a compatible server harness is available.
- InventoryAdapter evidence/plan hardening is owned by the parent-assigned worker; after that worker publishes its compatible helper API, GoldBagPlugin wiring should be reviewed against the immutable plan before integration is finalized. I have not edited InventoryAdapter.java.
- Quantity menus and conversation prompts still need a live Bukkit smoke check; the pure/coordinator coverage is green. No server smoke is claimed because accepting the Minecraft EULA remains an operator action.
