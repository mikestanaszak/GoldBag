# T1 core progress

Scope: `goldbag-core/src/**` and this report. No Bukkit dependency.

## Progress

- Read the approved plan and spec sections 4 and 6.
- Added behavior-first JUnit coverage for exact money parsing, default catalog values, aliases, derived blocks, validation, and settings parsing.
- RED evidence: `mvn -B -f goldbag-core/pom.xml test` failed during test compilation because `Money`, `Catalog`, and `Settings` were not yet present (23 missing-symbol errors).
- Implemented the exact public API, overflow-safe cent arithmetic, immutable catalog lookup, default resource prices, YAML duplicate/unknown-key validation, prohibited-material checks, derived nine-item storage blocks, and settings parsing.
- Packaged `defaults/config.yml`, `defaults/resources.yml`, and `defaults/messages.yml`.
- GREEN evidence: `mvn -B -f goldbag-core/pom.xml test` passed 10 tests with 0 failures and 0 errors on 2026-09-05.
- Re-ran the same focused command after the controller updated SnakeYAML from 2.4 to 2.7; it again passed 10 tests with 0 failures and 0 errors.
- Round-one review regression RED: after adding tests for the amended `Settings` display fields, strict scalar typing, unsafe database paths, explicit null sections, and the reversible-block whitelist, the focused compile failed because `currencyName()` and `currencySymbol()` were not yet part of `Settings`.
- Applied the accepted review fixes: only the approved reversible 9:1 storage-block pairs are accepted; prices, identifiers, display fields, and database filenames require YAML strings; settings retain `currencyName` and `currencySymbol` while preserving the old nine-argument constructor; explicit null sections and rooted/path database filenames are rejected.
- The review's quartz/amethyst flag finding was intentionally not applied: the approved table's “Not enabled” applies to storage blocks, while the resource rows remain enabled as directed by the controller.
- Round-one GREEN evidence: `mvn -B -f goldbag-core/pom.xml test` passed 12 tests with 0 failures and 0 errors on 2026-09-05.

## Verification

Focused test command: `mvn -B -f goldbag-core/pom.xml test`

Result: BUILD SUCCESS; 12 tests run, 0 failures, 0 errors.

## API and integration notes

The implementation will provide the exact public signatures in T1. Catalog aliases and storage-block metadata remain internal so the `Resource` record stays unchanged.

Current integration API:

- `Money.parse`, `Money.positive`, and `Money.format` use integer cents and reject negative, over-precise, exponent, and overflowing input.
- `Catalog.defaults` contains ten configured base resources and eight derived storage blocks. `Catalog.load(Reader)` accepts the documented `resources:` YAML shape and rejects duplicate/unknown keys, prohibited refined materials, bad storage pairings/ratios, and invalid prices.
- `Settings.defaults` and `Settings.load(Reader)` accept the documented nested config, retain validated `currencyName`/`currencySymbol` fields, reject explicit null sections and unsafe/non-string database filenames, and keep a nine-argument compatibility constructor defaulting to Gold/G.

Remaining risk: material existence against a running Bukkit server is intentionally outside core because T1 has no Bukkit dependency; the plugin must perform runtime material validation during startup/reload.
