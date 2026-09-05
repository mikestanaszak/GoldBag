# T1 core review

Review target: `.superpowers/sdd/2026-09-05-goldbag-rebuild/review-00a814e..b1ce893.diff` (commit `b1ce893`), against T1 in the approved plan and spec sections 4 and 6.

Verdict: not ready for integration. Duplicate YAML keys, normalized ID/alias collisions, exact `Money` parsing, and checked catalog multiplication are implemented well, but the issues below affect the default economy, configurable crafting loops, storage isolation, and strict configuration behavior.

## Findings

### [P1] Default quartz and amethyst are enabled despite the approved catalog

`goldbag-core/src/main/java/io/github/mikestanaszak/goldbag/core/Catalog.java:44-45` and `goldbag-core/src/main/resources/defaults/resources.yml:65-76` set both `deposit-enabled` and `withdraw-enabled` to `true` for `QUARTZ` and `AMETHYST_SHARD`. The spec table marks both rows “Not enabled”; the defaults therefore expose transactions that must be disabled. The focused test suite only checks their prices/resource count, so it misses the behavior. Set the default flags to false and add assertions for both directions.

### [P1] Arbitrary storage-block pairings can create a crafting/exchange loop

`Catalog.java:77-83` and `176-182` accept any `<material>_BLOCK` pairing as long as the configured ratio is 9. For example, a config pairing `QUARTZ` with `QUARTZ_BLOCK` is accepted and derives a 9.00 G block price, although the vanilla recipe crafts one quartz block from four quartz. A player can craft four quartz, deposit the block for 9.00 G, and withdraw/retain equivalent quartz value, creating a +5.00 G loop. The loader needs a whitelist of the supported nine-to-one reversible pairs (or an equivalent recipe-safe validation) and tests for incompatible pairings.

### [P1] `databaseFile` accepts rooted paths that can escape the plugin data directory

`goldbag-core/src/main/java/io/github/mikestanaszak/goldbag/core/Settings.java:21-23,73` rejects only strings containing `..`. `C:/outside.db`, `/outside.db`, and similar rooted paths are accepted. If the plugin resolves this value with `dataFolder.resolve(databaseFile)`, an absolute path bypasses the data directory and can select or create an unrelated database. Validate a normalized relative path (and the intended filename/subdirectory policy) before returning settings; integration must also enforce the restart-only path rule from the spec.

### [P2] YAML numeric scalars bypass the exact no-exponent price rule

`Catalog.java:71-73,238-245` accepts `Number` values and converts them with `String.valueOf`. SnakeYAML parses `deposit-price: 1e2` as a numeric scalar whose string form is `100.0`, so the loader accepts it and charges 100.00 G; `Money.parse` never sees the exponent. The plan/spec require exact decimal input with no exponent and invalid precision rejection. Require quoted string prices (or preserve and validate the original scalar spelling) and add a scientific-notation case to tests.

### [P2] Currency display settings are accepted and then discarded (contract omission)

`Settings.java:59-64` validates `currency.name` and `currency.symbol`, but the exact T1 `Settings` record at lines 14-16 has no fields for either value, and the default messages hard-code `G`. A user can configure these keys without any observable effect. This is partly a task-contract omission because the plan's exact record excludes the fields; the controller must either extend the integration API so the plugin can honor the settings or remove/reject the accepted keys instead of silently discarding them.

### [P2] Several malformed YAML scalar types are coerced instead of rejected

`Catalog.java:238-245` accepts numeric `material` values and turns `material: 123` into a syntactically valid internal ID, deferring failure to Bukkit material validation. `Settings.java:77-81` likewise accepts numeric currency names, symbols, and storage filenames. These fields are schema strings, and T1 calls for structural validation. Require `String` for identifiers, names, symbols, and paths; keep numeric acceptance only where the schema explicitly allows a numeric value.

### [P2] Explicit null sections are treated as omitted settings

`Settings.java:108-113` returns an empty map when a section value is null. Thus `storage:` or `exchange:` silently falls back to defaults instead of being rejected as malformed configuration. The loader should distinguish an absent optional section from a present null value and reject the latter.

## Validation evidence

- `mvn -B -f goldbag-core/pom.xml test` — BUILD SUCCESS, 10 tests, 0 failures, 0 errors.
- Focused JShell checks against the built classes confirmed `Catalog.defaults().require("quartz").depositEnabled()` and the amethyst flag are `true`; a `QUARTZ`/`QUARTZ_BLOCK` nine-ratio config is accepted; `deposit-price: 1e2` is accepted as 10000 cents; and `storage.file: C:/outside.db` is accepted.
- No source or Git changes were made by this review. Material existence against a running Bukkit server remains an integration responsibility because T1 has no Bukkit dependency; that stated limitation is not itself a review finding.

