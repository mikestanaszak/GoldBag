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
| T4 Bukkit plugin: lifecycle, commands, menus, exchange coordinator | Implemented; final reviews paused at usage limit | Luna `/root/plugin`: `goldbag-plugin/src/**` | T1, T2 public API | `reports/T4-plugin.md` |
| T4a Offline restore helper | Complete; review clear | Luna `/root/offline_import`: new `cli/**` source/test packages, backup/restore guide | T2 public API | `reports/T4-offline-import.md` |
| T4b Inventory plans and evidence | Implemented; accessor fix review pending | Luna inventory/helper workers: `InventoryAdapter.java`, its tests | T4 integration | `reports/T4-inventory.md` |
| T5 Independent review and integration fixes | Paused at usage limit | Luna player-flow and physical-flow reviewers | T1–T4 | `reports/T4-player-flows-review.md`, `reports/T4-physical-review.md` |
| T6 Server validation, packaging, final checkpoint | Automated verification passing; server checks pending | Controller | T5 | `reports/T6-validation.md` |

## Latest checkpoint

- Implementation source: `904e67f`, pushed to `origin/codex/goldbag-rebuild`; main unchanged. Final report/doc updates will be included in the next documentation checkpoint.
- Controller `mvn -B verify` passes 63 tests (12 core, 17 storage, 34 plugin). Java 17/21 Linux CI also passed at `904e67f` (run `34007572136`).
- Both packaged-JAR checks pass: relocated libraries/native SQLite/restart persistence, and offline CLI dry-run/restore preserving balances, issued notes, pending recovery evidence, and source JSON.
- Development artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; SHA-256 `F9B0F5F1ED777EB77E52A0A24C51B5B0272537039E6FF34D2AD8E8CFAA728388`.
- T1, T2, T3, and offline restore implementation/review are complete. SQLite has 17 focused tests; no open findings remain in its completed review scope.
- T4 implementation fixes are checkpointed, including exact inventory plans and hand slots, quote request cancellation/reload protection, menu permissions and controls, messages, recovery evidence, lifecycle ownership, and callback cleanup.
- Two final Luna re-reviews stopped at the usage limit before returning a disposition: `/root/review_player_flows` and `/root/review_physical_transactions`. Reported retry time: September 6, 2026, 2:31 AM; do not assume automatic continuation or current availability.
- Resume briefs are in `reports/T5-review.md`. Re-review only those latest fixes, route concrete residuals to owners, and preserve completed work. Do not silently substitute a different model.
- Source and tests survived each interruption and are committed. No source edits remain to reconstruct. The remaining work is independent review disposition, any concrete residual fixes, and later exact server-build validation.
- No live Minecraft server has been started or EULA accepted. Compatibility remains unverified; this is a development build.

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

- Latest full reactor: PASS, 63 tests on Java 21; Java 17/21 Linux CI also PASS.
- Latest shaded package: 712 base classes, all major version at most 60 (Java 16). This is a header check, not proof of behavior on Java 16 or Minecraft.
- Packaged SQLite persistence and offline restore smoke: PASS; artifact hash above. Detailed evidence is in `reports/T6-validation.md`.
- Final independent fix reviews and actual server checks are still pending. Do not treat compilation or test doubles as a server compatibility certificate.
