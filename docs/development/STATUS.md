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
| T4 Bukkit plugin: lifecycle, commands, menus, exchange coordinator | Running | Luna `/root/plugin`: `goldbag-plugin/src/**` | T1, T2 public API | `reports/T4-plugin.md` |
| T4a Offline restore helper | Complete; review clear | Luna `/root/offline_import`: new `cli/**` source/test packages, backup/restore guide | T2 public API | `reports/T4-offline-import.md` |
| T4b Inventory plans and evidence | Running | Luna `/root/inventory_adapter`: `InventoryAdapter.java`, its new tests | T4 integration | `reports/T4-inventory.md` |
| T5 Independent review and integration fixes | Waiting | Luna reviewer; fixes routed to owners | T1–T4 | `reports/T5-review.md` |
| T6 Server validation, packaging, final checkpoint | Waiting | Controller + Luna verification | T5 | `reports/T6-validation.md` |

## Latest checkpoint

- Remote backup: through `7367d29` on `origin/codex/goldbag-rebuild`; main unchanged. Later working-tree edits still need a checkpoint/push.
- T1 complete at `d1b5253`: 12 focused tests, review clear. T3 complete at `82306c4`: helper fixture checks and review clear.
- T2 complete at `91b5b7d`: 17 focused tests and module verification; final independent review clear. Schema 2 and the public storage API are stable.
- T4a complete at `7a313b2`: offline dry-run/restore CLI, five focused tests, independent review clear (`reports/T4-offline-review.md`). No live import or destination overwrite.
- T4 plugin checkpoint `7367d29`: operation-owned confirmation tokens, permission/game-mode revalidation, menus, quotes, commands, and offline import guidance. GitHub Java 17/21 Linux verification passed (run `34006088585`). Earlier WIP `079950a` captured a transient guard compile error that this checkpoint fixes.
- A second usage interruption preserved uncommitted inventory-plan, executor, coordinator, shutdown, and test changes. User requested continuation; the two original Luna workers have resumed those existing files. New tests may not compile until their implementations are finished; do not delete them or restart completed tasks.
- Main plugin worker owns `plugin/**` except `InventoryAdapter.java` and its dedicated tests, which belong to `/root/inventory_adapter`. It must wire the immutable inventory plan into physical operations after the helper is ready. Controller alone stages/commits.
- Executor/coordinator work still needs focused verification of shutdown ordering, queued futures, callback exceptions, timeouts, late outcomes, and quarantine. Exact before/after slot evidence still needs helper verification and integration.
- `/root/review_player_flows` reviews only command/menu/config/permission/quote flows at frozen `7367d29`. It does not review actively changing executor or inventory helpers. Findings go to `reports/T4-player-flows-review.md`.
- Next controller step: checkpoint recovered WIP; collect focused helper/coordinator results; route player-flow findings; integrate and independently review remaining fixes; run full reactor and packaged-JAR checks. Exact Minecraft server compatibility remains unverified.

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
