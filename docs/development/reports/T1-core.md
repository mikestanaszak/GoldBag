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

## Verification

Focused test command: `mvn -B -f goldbag-core/pom.xml test`

Result: BUILD SUCCESS; 10 tests run, 0 failures, 0 errors.

## API and integration notes

The implementation will provide the exact public signatures in T1. Catalog aliases and storage-block metadata remain internal so the `Resource` record stays unchanged.

Current integration API:

- `Money.parse`, `Money.positive`, and `Money.format` use integer cents and reject negative, over-precise, exponent, and overflowing input.
- `Catalog.defaults` contains ten configured base resources and eight derived storage blocks. `Catalog.load(Reader)` accepts the documented `resources:` YAML shape and rejects duplicate/unknown keys, prohibited refined materials, bad storage pairings/ratios, and invalid prices.
- `Settings.defaults` and `Settings.load(Reader)` accept the documented nested config, including currency display name/symbol as validated known fields while returning the exact contract record fields.

Remaining risk: material existence against a running Bukkit server is intentionally outside core because T1 has no Bukkit dependency; the plugin must perform runtime material validation during startup/reload.
