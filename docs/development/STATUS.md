# GoldBag implementation status

Plan: `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`

Branch: `codex/goldbag-rebuild`; original code preserved in Git history and removed from the working tree at the user's request.

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
| T7 README follow-up | Complete; Luna review clear; PR publication tracked on GitHub | Controller: README/STATUS; Luna: review report | PR #1 merged | `reports/T7-readme-review.md` |
| T8 Remove superseded files and stale build outputs | Complete; clean build, packaged verification, and Luna review clear; included in PR #2 | Controller: legacy directories, README/STATUS, build outputs; Luna: review report | T7 | `reports/T8-cleanup.md`, `reports/T8-cleanup-review.md` |

## Batch chest follow-up (2026-10-12)

- User testing of v2.1.0 found per-stack latency and invalid-item chat while rapidly shift-clicking. The user now requests a real chest to fill before clicking one Deposit button. This supersedes instant per-gesture selling.
- Active branch `codex/batch-deposit-chest` from main after PR #5. Controller owns rendering/docs/integration; bounded Luna workers own physical transactions, harness and independent review.
- Target: native fast chest movement, staged eligible batch sale, quiet invalid-item handling, exact unsold returns on close, interruption evidence and responsive queued withdrawals. Approved rates remain unchanged. Implementation complete at `8a4acd4`: full reactor84, harness13, release5 and packaged checks PASS; 23 actual-player checks plus quit/rejoin staged return PASS. Clean no-reset restart PASS. PR #6 merged at `febce145`; release workflow38295432460 published v2.2.0. PR #7 (`132b652`) corrected partial affordability for grouped clicks; run38296302348 published v2.2.1, now installed/running healthy. Patch24 actual-player checks and same-artifact no-reset restart PASS. All4 existing balance/revision rows and approved18 rates preserved; pending0 and SQLite quick_check ok. Backup/evidence: `reports/batch-release-deployment.md`. Independent review resolved blockers; native gesture-to-snapshot abrupt-crash window is retained as a documented manual-recovery limitation.
- Reports: `batch-deposit.md`, `batch-deposit-review.md`, `batch-player-tests.md`.

## Direct chest exchange and approved prices (2026-10-12)

- PR #4 merged at `4b28e6c`; semantic-release published v2.1.0. The user superseded the older resource quantity/confirmation flow: chest shift-click/cursor/drag deposits and direct withdrawal clicks now execute journaled transactions immediately and keep the chest open.
- Withdrawal clicks request 1 / up to 16 / up to 64 with balance/capacity/transaction clamping; chest menus show refreshed balances and direct deposit/withdraw switching.
- User approved revised prices: coal 0.50, redstone 0.25, lapis 0.50, raw copper 0.50, raw iron 2.00, raw gold 5.00, diamond 50.00, emerald 2.00, quartz 1.00, amethyst shard 0.25 G. Equal deposit/withdraw values, reversible blocks 9×, existing balances preserved. Existing server resources.yml must be explicitly backed up/updated on deployment.
- Full Java 17 reactor and shaded package verification PASS (83 tests); eleven harness and five release-analysis tests PASS. Independent transaction review source-clear. Native Paper 1.21.11 build 132/Java 25 passed 20 actual-player checks and no-reset restart/note replay with approved rates. Exact-head CI and release PASS. Published v2.1.0 is active in maya-funtimes, Paper 26.3 build 177/Java 25, healthy; all four existing balance/revision snapshots preserved and unresolved operations zero. Backup and checksum are in `reports/direct-release-deployment.md`. Native drag/26.3/Geyser player gestures remain manual validation limits.
- Reports: `reports/direct-exchange.md`, `reports/direct-exchange-review.md`, `reports/direct-player-tests.md`, `reports/resource-value-review.md`.

## Clickable confirmation and experience review (2026-10-11)

- PR #3 merged during the usage pause. UI follow-up is now on `codex/ui-confirmation-experience`, based on `main` at `6c994c5`; open a separate PR, do not merge. The code was independently verified in [CI run 38191645292](https://github.com/mikestanaszak/GoldBag/actions/runs/38191645292) on the original branch, with all checks PASS.
- All deposit/withdrawal/deposit-all/banknote previews now open green Confirm / red Cancel UI with resource breakdown, configured currency and before/after balance. Typed confirmation remains a compatibility fallback rather than the player flow.
- Fixed deposit next-page navigation, stale quantity menus, identity-safe cancellation/duplicate confirmation, deferred inventory transitions, prompt validation/retry/threading, console player-only feedback, help and permission-aware completion.
- UI test-artifact preparation on Java 25 PASS: 79 Java tests (12 core, 17 storage, 50 plugin) and shaded SQLite/restore/descriptor checks. Six harness unit tests and five release-analysis checks PASS.
- Native Paper 1.21.11 build 132 / Java 25: 17 live player checks PASS, then no-reset restart balances/note/replay PASS. Final UI release also passes Paper 26.3 build 175 startup/storage/rates/reload/shutdown. 26.3 player protocol remains unverified after bridge-client disconnects; see `reports/ui-live-validation.md`.
- Independent command/UI audit has no remaining source finding after gating other-player balance suggestions. Reports: `ui-confirmation.md`, `ui-command-audit.md`, `ui-live-validation.md`.
- With the user's existing staging authorization, updated `/opt/stacks/maya-funtimes/data/plugins/GoldBag-2.0.0.jar`; SHA-256 `6dfdae69137b506b583fc1ccd0262530c3b4e3294c8ef616c579de7392a06c71`. Prior JAR preserved outside plugins at `/opt/stacks/maya-funtimes/data/plugin-backups/GoldBag-2.0.0-before-confirm-ui.jar`. The production service was not restarted and live account data was not changed. This manually prepared UI test JAR is unreleased; semantic-release will select the next minor version after published `v2.0.0` when the UI feature merges.

## Minecraft 26.3 and semantic releases (2026-10-11)

- Active branch: `codex/minecraft-26.3-semantic-release`, based on `main` at `ea464d7`. Current request is to open a PR only; historical rebuild merge authorization does not apply to this PR.
- Baseline Java 17 `mvn -B verify`: PASS. Java 25.0.4.1 `mvn -B clean verify`: PASS, 69 tests. Java 25 shaded verifier: PASS with Java 16 base bytecode retained.
- Semantic-release tooling: five commit analysis checks PASS. Disposable-copy `bash scripts/Prepare-GoldBagRelease.sh 2.0.0`: PASS, all module versions updated, full verification, descriptor/manifest agreement, JAR and checksum generated.
- Release review is clear after pinning Action SHAs and preserving the fixed build timestamp. Two release preparations produce identical hashes. Authenticated semantic-release dry-run selects `2.0.0` without publishing.
- Java 17/21/25 CI and both Node check jobs PASS at `8ff86df`, [run 38169021947](https://github.com/mikestanaszak/GoldBag/actions/runs/38169021947). See `reports/26.3-validation.md` and `reports/26.3-release-review.md`.
- Paper 26.3 build 175 startup, SQLite/catalog, console checks, clean restart and shutdown pass with the versioned release JAR; live player smoke remains blocked by missing Mineflayer protocol data. See `reports/26.3-runtime.md` for exact runtime evidence.
- Root release-tooling `npm audit` remains nonzero (13 reported vulnerabilities: 11 high, 2 moderate), including upstream `braces`/`micromatch` and bundled npm dependencies. `npm audit fix --ignore-scripts` did not eliminate them; no forced downgrade applied. These are development tooling, not shaded plugin dependencies.

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

- Ruling: the rebuild initially retained old source outside Maven roots. The user subsequently requested its removal; the original source is now retained in Git history only.
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

## Gold theme follow-up (2026-10-12)

- Branch `codex/gold-theme`: every chat line uses a gold `[GoldBag]` prefix, including legacy errors, configured text, multiline help and conversation prompts. Gold menu titles/items, yellow lore and warmer banknote/success wording preserve existing controls, rates and transactions.
- Focused theme checks2, full Java tests86, packaged verifier, harness13 and release-analysis5 PASS. Independent Luna review found the prompt bypass; fixed and review clear. Native theme probe and publication/deployment evidence are recorded in `reports/gold-theme.md`.

- Gold theme complete: PR #9 merged at `a7b33372`; release workflow38296887510 PASS; v2.3.0 published and installed/running healthy in maya-funtimes. All4 balances/revisions and settings/rates preserved, pending0, SQLite ok; full rollback backup and exact checksum in `reports/gold-theme.md`. Native Paper1.21.11 theme probe12 messages PASS;26.3/Geyser visuals remain manual.

## Plain wording correction (2026-10-12)

- Owner requested removing playful copy. `codex/plain-wording` replaces treasure/adventure/vault wording with direct instructions/results; gold styling and `[GoldBag]` remain. Full86 Java tests and packaged verification PASS. See `reports/plain-wording.md` for release/activation evidence.

- Plain wording is complete and active: PR #11 / source843ec250, v2.3.1 published by workflow38297690831 and running healthy. All4 balances/revisions and settings preserved. Backup/checksum in `reports/plain-wording.md`.
