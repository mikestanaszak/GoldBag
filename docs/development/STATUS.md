# GoldBag implementation status

Plan: `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`

Branch: `codex/goldbag-rebuild`; original code preserved in Git history and currently outside Maven source roots.

## Task board

| Task | State | Owner/files | Depends on | Resume report |
|---|---|---|---|---|
| T0 Build foundation and durable handoff | Complete | Controller: root/module POMs, AGENTS, RESUME, STATUS | None | This file |
| T1 Exact money, resource catalog, configuration | Running | Luna `/root/core`: `goldbag-core/src/**` | T0 | `reports/T1-core.md` |
| T2 SQLite economy, notes, journal, exports | Running | Luna `/root/storage`: `goldbag-storage/src/**` | T0 | `reports/T2-storage.md` |
| T3 Build automation and operator documentation | Running | Luna `/root/operations`: `.github/**`, `scripts/**`, `docs/operations/**` | T0 | `reports/T3-operations.md` |
| T4 Bukkit plugin: lifecycle, commands, menus, exchange coordinator | Waiting | Luna: `goldbag-plugin/src/**` | T1, T2 | `reports/T4-plugin.md` |
| T5 Independent review and integration fixes | Waiting | Luna reviewer; fixes routed to owners | T1–T4 | `reports/T5-review.md` |
| T6 Server validation, packaging, final checkpoint | Waiting | Controller + Luna verification | T5 | `reports/T6-validation.md` |

## Latest checkpoint

- Existing repository fetched into this workspace; switched to a new development branch. No original branch changed.
- Java 21 and Maven 3.9.11 available. Original repository has no build definition or tests, so no baseline test suite exists.
- Foundation checkpoint: `a7bcfcd` (design, task plan, Maven foundation, AGENTS, RESUME).
- Remote backup: `a7bcfcd` pushed to `origin/codex/goldbag-rebuild`; main unchanged. Later edits are not backed up until another push.
- Next controller step: collect T1–T3 reports and focused test evidence, checkpoint and review each, then dispatch T4 against actual interfaces. Resume existing files after interruption; do not restart completed tasks.

## Decisions and boundaries

- Ruling: use the existing repo on a fresh development branch, retaining old source outside new Maven roots — preserves history and avoids accidental compilation of the old plugin — cost if wrong: source layout can be revised without losing history.
- Ruling: tracked reports and task board remain permanently, overriding disposable skill scratch cleanup — user specifically wants resumable progress — cost: a small amount of maintained documentation.
- Ruling: independent modules may be implemented concurrently in disjoint file sets; controller serializes Git commits — satisfies parallel-work request without shared-index races — cost: task completion waits briefly for controller checkpoint.
- Paper/Spigot direction accepted by the user's instruction to implement after reviewing the revised spec. Full catalog requires Minecraft 1.17 or later. No actual server compatibility claim exists yet.
- Server startup requiring acceptance of third-party terms is a manual operator step unless already authorized separately.

## Verification

- `mvn -B validate`: PASS for all four reactor projects on Java 21 / Maven 3.9.11. This validates build structure, not plugin behavior.
- Spigot API 1.17 dependency successfully resolved from the official repository.
- No plugin artifact or server compatibility is validated yet.
