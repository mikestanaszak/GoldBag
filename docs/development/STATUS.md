# GoldBag implementation status

Plan: `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`

Branch: `codex/goldbag-rebuild`; original code preserved in Git history and currently outside Maven source roots.

## Task board

| Task | State | Owner/files | Depends on | Resume report |
|---|---|---|---|---|
| T0 Build foundation and durable handoff | Complete | Controller: root/module POMs, AGENTS, RESUME, STATUS | None | This file |
| T1 Exact money, resource catalog, configuration | Complete; review clear | Luna `/root/core`, reviewer `/root/review_core`: `goldbag-core/src/**` | T0 | `reports/T1-core.md`, `reports/T1-review.md` |
| T2 SQLite economy, notes, journal, exports | Complete; review clear | Luna `/root/storage`, reviewer `/root/review_storage`: `goldbag-storage/src/**` | T0 | `reports/T2-storage.md`, `reports/T2-review.md` |
| T3 Build automation and operator documentation | Complete; review clear | Luna `/root/operations`, reviewer `/root/review_operations`: `.github/**`, `scripts/**`, `docs/operations/**` | T0 | `reports/T3-operations.md`, `reports/T3-review.md` |
| T4 Bukkit plugin: lifecycle, commands, menus, exchange coordinator | Complete; review clear | Luna workers: completion cleanup and pagination | T1, T2 public API | `reports/T4-plugin.md`, `reports/T4-plugin-pagination.md` |
| T4a Offline restore helper | Complete; review clear | Luna `/root/offline_import`: new `cli/**` source/test packages, backup/restore guide | T2 public API | `reports/T4-offline-import.md` |
| T4b Inventory plans and evidence | Complete; review clear | Luna inventory/helper workers: `InventoryAdapter.java`, its tests | T4 integration | `reports/T4-inventory.md` |
| T5 Independent review and integration fixes | Complete; review clear | Luna player-flow and physical-flow reviewers | T1–T4 | `reports/T4-player-flows-review.md`, `reports/T4-physical-review.md` |
| T6 Server validation, packaging, final checkpoint | Complete; reviews and local smoke clear | Luna automation worker + controller | T5 | `reports/T6-validation.md`, `reports/T6-automation.md` |

## Latest checkpoint

- Final implementation: runtime source `37fb85f`, reproducible packaging `63331a0`, reviewed harness fixes `fa378ef`. Integration is [PR #1](https://github.com/mikestanaszak/GoldBag/pull/1) into default branch `main`; GitHub records its check and merge status. The user authorized merging after checks, preserving the original history and checkpoint commits.
- Controller `mvn -B verify`: PASS, 69 tests (12 core, 17 storage, 40 plugin) on Java 21.0.11/Maven 3.9.11/Windows. Packaged verifier PASS. GitHub Linux Java 17/21 Maven and packaged checks PASS at final test checkpoint `5ffc16d`, [run 34063224382](https://github.com/mikestanaszak/GoldBag/actions/runs/34063224382).
- Current artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; adjacent `.sha256`. SHA-256: `C48C54EC4037C8777B32FEC2F5017B00A69200306D95C417A95C5845D9785535`.
- User explicitly authorized `eula=true` and local server testing on 2026-09-06. Paper 1.21.11 build 132 on Windows/Java 21 passed startup, all 18 resources, 16 actual-player checks, restart balances/note persistence and copied-note replay, invalid/valid reload, synthetic recovery apply/cancel/quarantine, actual server export/restore, and clean shutdown.
- Server testing found and fixed banknote air interaction filtering; the three new regression tests and independent Luna review are clear. Core/storage/operations/plugin/offline-restore and recovery-fixture reviews remain complete.
- Server directory: `C:\Users\mfsta\Documents\ChatGPT\GoldBag-local-tests\paper-1.21.11-132`. It is STOPPED, loopback port 25575 has no listener, and `eula=true` remains saved. Database final balances: SmokeA=G90.00, SmokeB=G15.00, RecoveryApply=G2.00, RecoveryCancel=G0.00; unresolved operations zero. Worlds/logs/database are preserved outside Git.
- The final Luna harness review resumed and cleared both test-tool fixes: clearing both inventories and reliable client cleanup/exact-note selection. Five focused Node tests and the repeated 16-player-check run passed. No open review finding remains. Incremental packaging was also fixed and independently reviewed at `63331a0`; repeat packaging now produces identical bytes.
- Exact Paper smoke evidence is in `reports/T6-server-validation.md` and `reports/T6-player-server-tests.md`. Other versions/platforms and extended hard-crash/disconnect/death/protection-plugin scenarios remain unverified. This is a development build with an exact-server smoke pass, not a broad compatibility certificate.
## Decisions and boundaries

- Ruling: use the existing repo on a fresh development branch, retaining old source outside new Maven roots — preserves history and avoids accidental compilation of the old plugin — cost if wrong: source layout can be revised without losing history.
- Ruling: tracked reports and task board remain permanently, overriding disposable skill scratch cleanup — user specifically wants resumable progress — cost: a small amount of maintained documentation.
- Ruling: independent modules may be implemented concurrently in disjoint file sets; controller serializes Git commits — satisfies parallel-work request without shared-index races — cost: task completion waits briefly for controller checkpoint.
- Ruling: keep quartz and amethyst shards enabled — reviewer misread "Not enabled" in the spec's storage-block column as applying to the resource itself; approved prices and default catalog include both resources — cost if wrong: default flags can be changed before release. The scoped reviewer withdrew the finding.
- Ruling: append currencyName/currencySymbol to Settings and restrict DB path to a simple filename — resolves an omitted API field and prevents accidental path escape while preserving configurable display names — cost: a small API adjustment before plugin integration.
- Ruling: permit storage schema 2 with per-account entry revisions; reject unreleased prototype schema 1 with an explicit diagnostic — deterministic ledger validation cannot infer ordering from timestamps or random UUIDs, and the user has no live server data — cost if wrong: prototype data requires a separately validated migration, not silent adoption.
- Paper/Spigot direction accepted by the user's instruction to implement after reviewing the revised spec. Full catalog requires Minecraft 1.17 or later. Exact Paper 1.21.11 build 132 now has a recorded smoke pass; other builds remain unverified.
- Server startup requiring acceptance of third-party terms is a manual operator step unless already authorized separately.

## Verification

- Full reactor: PASS, 69 tests; five Node lifecycle/identity tests also PASS. Java 17/21 Linux CI: PASS, including packaged runtime checks.
- Shaded artifact: 712 base classes, maximum major 60 (Java 16),20 native SQLite entries, expected descriptor/manifest/relocated libraries, no bundled Bukkit classes.
- Packaged SQLite restart/restore and actual server-export restore: PASS.
- Paper 1.21.11 build 132 actual smoke: PASS; see detailed server/player reports. Clean shutdown and released database ownership confirmed.
- All requested implementation and scoped reviews are complete. Extended compatibility/fault scenarios remain unverified and are documented future validation. User explicitly authorized creating a PR to the default branch and merging after checks; See PR #1 for the authoritative integration result. The source, tests, and review handoff contain no unfinished implementation task.
