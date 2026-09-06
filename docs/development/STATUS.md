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
| T6 Server validation, packaging, final checkpoint | Automated checks complete; local server tests running | Luna automation worker + controller | T5 | `reports/T6-validation.md`, `reports/T6-automation.md` |

## Latest checkpoint

- Implementation fixes: `5b2efb8` and `51238d8`; independent fix reviews: `7df6a6b`; packaged verification automation: `39c93b7`. All are pushed to `origin/codex/goldbag-rebuild`; main is unchanged. Final handoff documentation follows in a documentation-only commit.
- Controller `mvn -B clean verify`: PASS, 66 tests (12 core, 17 storage, 37 plugin), zero failures/errors/skips on Java 21.0.11 / Maven 3.9.11 / Windows.
- GitHub Linux Java 17 and 21: both Maven and the new packaged verifier PASS at `39c93b7`, run [34008700654](https://github.com/mikestanaszak/GoldBag/actions/runs/34008700654). Each job retained the canonical shaded JAR, checksum, and test reports.
- Development artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; adjacent `.sha256` file. SHA-256: `43377E3C0C2E8AAB9F5AB22F4DD65A38B0353ED48363683CEFBC2F0ACBB4AE0E`.
- All scoped core, storage, operations, plugin, inventory, offline restore, and final automation reviews are clear. The last cleanup and pagination fixes were independently reviewed by the other Luna worker; see `T4-completion-review.md`, `T4-pagination-review.md`, and `T6-automation-review.md`.
- The user-requested automatable work is complete. Future builds can repeat the package checks using `pwsh -File scripts/Invoke-GoldBagBuild.ps1`; CI runs the same verifier on Java 17/21. Completed work must not be restarted after an interruption.
- On 2026-09-06 the user explicitly authorized setting EULA to true and server testing. Paper 1.21.11 build 132 is now running in an isolated loopback-only directory. Startup enabled GoldBag with 18 resources; actual player and lifecycle checks are underway. See `reports/T6-server-validation.md` and `reports/T6-player-server-tests.md`. No other server version is certified.
- This is a tested development artifact, not a verified Minecraft server release. Do not merge main or publish a compatibility claim without actual server evidence.
## Decisions and boundaries

- Ruling: use the existing repo on a fresh development branch, retaining old source outside new Maven roots — preserves history and avoids accidental compilation of the old plugin — cost if wrong: source layout can be revised without losing history.
- Ruling: tracked reports and task board remain permanently, overriding disposable skill scratch cleanup — user specifically wants resumable progress — cost: a small amount of maintained documentation.
- Ruling: independent modules may be implemented concurrently in disjoint file sets; controller serializes Git commits — satisfies parallel-work request without shared-index races — cost: task completion waits briefly for controller checkpoint.
- Ruling: keep quartz and amethyst shards enabled — reviewer misread "Not enabled" in the spec's storage-block column as applying to the resource itself; approved prices and default catalog include both resources — cost if wrong: default flags can be changed before release. The scoped reviewer withdrew the finding.
- Ruling: append currencyName/currencySymbol to Settings and restrict DB path to a simple filename — resolves an omitted API field and prevents accidental path escape while preserving configurable display names — cost: a small API adjustment before plugin integration.
- Ruling: permit storage schema 2 with per-account entry revisions; reject unreleased prototype schema 1 with an explicit diagnostic — deterministic ledger validation cannot infer ordering from timestamps or random UUIDs, and the user has no live server data — cost if wrong: prototype data requires a separately validated migration, not silent adoption.
- Paper/Spigot direction accepted by the user's instruction to implement after reviewing the revised spec. Full catalog requires Minecraft 1.17 or later. No actual server compatibility claim exists yet.
- Server startup requiring acceptance of third-party terms is a manual operator step unless already authorized separately.

## Verification

- Full clean reactor: PASS, 66 tests. Java 17/21 Linux CI: PASS, including packaged runtime checks.
- Shaded artifact: 712 base classes, maximum major 60 (Java 16), 20 native SQLite entries, required descriptor/manifest/relocated libraries, and no bundled Bukkit classes. Header inspection is not proof of all Java 16 or Minecraft behavior.
- Packaged SQLite restart and offline validation/restore: PASS, preserving balances, issued notes, pending evidence, and source bytes. Details are in `reports/T6-validation.md`.
- No open findings remain in the completed scoped reviews. Actual server compatibility remains unverified.
