# GoldBag implementation status

Plan: `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`

Branch: `codex/goldbag-rebuild`; original code preserved in Git history and currently outside Maven source roots.

## Task board

| Task | State | Owner/files | Depends on | Resume report |
|---|---|---|---|---|
| T0 Build foundation and durable handoff | Complete | Controller: root/module POMs, AGENTS, RESUME, STATUS | None | This file |
| T1 Exact money, resource catalog, configuration | Complete; review clear | Luna `/root/core`, reviewer `/root/review_core`: `goldbag-core/src/**` | T0 | `reports/T1-core.md`, `reports/T1-review.md` |
| T2 SQLite economy, notes, journal, exports | Final residual fix committed; narrow re-review | Luna `/root/storage`, reviewer `/root/review_storage`: `goldbag-storage/src/**` | T0 | `reports/T2-storage.md`, `reports/T2-review.md` |
| T3 Build automation and operator documentation | Complete; review clear | Luna `/root/operations`, reviewer `/root/review_operations`: `.github/**`, `scripts/**`, `docs/operations/**` | T0 | `reports/T3-operations.md`, `reports/T3-review.md` |
| T4 Bukkit plugin: lifecycle, commands, menus, exchange coordinator | Running | Luna `/root/plugin`: `goldbag-plugin/src/**` | T1, T2 public API | `reports/T4-plugin.md` |
| T4a Offline restore helper | Running | Luna `/root/offline_import`: new `cli/**` source/test packages, backup/restore guide | T2 public API | `reports/T4-offline-import.md` |
| T5 Independent review and integration fixes | Waiting | Luna reviewer; fixes routed to owners | T1–T4 | `reports/T5-review.md` |
| T6 Server validation, packaging, final checkpoint | Waiting | Controller + Luna verification | T5 | `reports/T6-validation.md` |

## Latest checkpoint

- Existing repository fetched into this workspace; switched to a new development branch. No original branch changed.
- Java 21 and Maven 3.9.11 available. Original repository has no build definition or tests, so no baseline test suite exists.
- Foundation checkpoint: `a7bcfcd` (design, task plan, Maven foundation, AGENTS, RESUME).
- Remote backup: through `d52c26c` pushed to `origin/codex/goldbag-rebuild`; main unchanged. Later edits are not backed up until another push.
- Core checkpoint: `d1b5253`, 12 focused tests passed, including current SnakeYAML 2.7. Independent scoped review clear; original quartz/amethyst finding withdrawn by reviewer.
- Tooling checkpoint: `82306c4`, controlled PowerShell fixture checks passed. Independent scoped review clear; no real Minecraft server has been started.
- SQLite fixes checkpoint: `bf5e6dd`, 12 focused tests and module verify reported passing; public API unchanged. Independent re-review cleared the original six findings and found three restore gaps: canonical UUID spelling, unique active note reservations/issue ownership, and SET_BALANCE operation delta consistency. Original Luna storage worker is fixing these with regressions.
- Plugin WIP checkpoint: `d52c26c` preserved recovered source and actual coordinator-execution tests. GitHub Linux verification passed on Java 17/21 (run `34005252139`). Fresh verification is required after ongoing edits.
- T4 early boundary review saved in `reports/T4-boundary-review.md`: 13 findings against the older `65dd1b7` snapshot. Original Luna plugin worker is assessing each against current edits and fixing remaining issues alongside menu completion. `/gb` remains a canonical alias when optional legacy aliases are disabled.
- Additional controller observations are durable in `reports/T4-integration-notes.md`. The offline CLI worker owns only the new `io/github/mikestanaszak/goldbag/cli/**` production/test packages; the plugin worker retains `plugin/**` and replaces live-import behavior with offline guidance. No concurrent worker may stage or commit.
- Storage checkpoint `91b5b7d` contains the final active-redemption restore-state fix; implementer reports 17 focused tests and module verification passing. Narrow independent re-review is pending.
- T4 safety/menu changes are being saved as WIP before completion. Remaining checks include executor shutdown/deadlines, operation-owned guards, exact inventory evidence, and fresh independent plugin review. WIP packaging is not a server release.
- Usage interruption recovery: T4 plugin implementation, T2 scoped re-review, and T4 boundary review restarted from their existing files and reports. T1/T3 remain complete and were not restarted. T4 report has some stale remaining-work bullets; implementer is reconciling them with actual source before its final checkpoint.
- Core fixes: whitelist reversible blocks, quoted decimal settings and strict scalar types, simple DB filenames, and retain currency display fields. Tooling fix: reject junction/symlink ancestors before copying files.
- Next controller step: checkpoint and re-review T2 round 2 fixes; complete T4 quantity/menu flows, offline import, and actual coordinator tests; resolve boundary-review findings; run full integration verification and fresh T4 review. Resume existing files after interruption; do not restart completed tasks.

## Decisions and boundaries

- Ruling: use the existing repo on a fresh development branch, retaining old source outside new Maven roots — preserves history and avoids accidental compilation of the old plugin — cost if wrong: source layout can be revised without losing history.
- Ruling: tracked reports and task board remain permanently, overriding disposable skill scratch cleanup — user specifically wants resumable progress — cost: a small amount of maintained documentation.
- Ruling: independent modules may be implemented concurrently in disjoint file sets; controller serializes Git commits — satisfies parallel-work request without shared-index races — cost: task completion waits briefly for controller checkpoint.
- Ruling: keep quartz and amethyst shards enabled — reviewer misread "Not enabled" in the spec's storage-block column as applying to the resource itself; approved prices and default catalog include both resources — cost if wrong: default flags can be changed before release. Ask scoped reviewer to correct this finding.
- Ruling: append currencyName/currencySymbol to Settings and restrict DB path to a simple filename — resolves an omitted API field and prevents accidental path escape while preserving configurable display names — cost: a small API adjustment before plugin integration.
- Ruling: permit storage schema 2 with per-account entry revisions; reject unreleased prototype schema 1 with an explicit diagnostic — deterministic ledger validation cannot infer ordering from timestamps or random UUIDs, and the user has no live server data — cost if wrong: prototype data requires a separately validated migration, not silent adoption.
- Paper/Spigot direction accepted by the user's instruction to implement after reviewing the revised spec. Full catalog requires Minecraft 1.17 or later. No actual server compatibility claim exists yet.
- Server startup requiring acceptance of third-party terms is a manual operator step unless already authorized separately.

## Verification

- `mvn -B validate`: PASS for all four reactor projects on Java 21 / Maven 3.9.11. This validates build structure, not plugin behavior.
- Spigot API 1.17 dependency successfully resolved from the official repository.
- Full `mvn -B verify`: PASS on Java 21, 18 tests total (12 core + 6 storage). GitHub Linux builds passed on Java 17 and 21 at the storage checkpoint.
- Standalone shaded-JAR smoke: PASS for relocated core/YAML/Gson loading, native SQLite, account restart persistence, and JSON export/import. Temporary source `.runtime/ShadedSmoke.java` is a local verification aid, not production code.
- Shading reports overlapping module descriptors, manifests, and SLF4J license resources; package cleanup remains for T6. No game-facing plugin implementation or server compatibility is validated yet; current packaged artifact is not a server release.
