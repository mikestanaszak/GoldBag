# T5 integration review and continuation

Current implementation checkpoint: `904e67f` on `codex/goldbag-rebuild`.

## Cleared work

- T1 core/configuration: independent review clear, 12 tests.
- T2 SQLite journal/notes/restore: independent review clear after scoped fixes, 17 tests.
- T3 operations tooling: independent review clear.
- T4a offline restore: independent review clear at `7a313b2`, five tests plus packaged CLI smoke.
- Inventory main-slot plans were reviewed at `05e7ab4`. The hand-slot extension at `77b417f` was reviewed with one accessor-only finding, fixed at `ac4d5bd`; final fix disposition remains pending.

## Implementation fixes awaiting final independent disposition

The original plugin worker completed and checkpointed fixes for the player-flow and physical-flow reports. Controller verification at `904e67f` passed all 63 tests. This does not substitute for the requested independent review.

Both final **gpt-5.6-luna** reviewers stopped with a usage-limit error before returning a disposition. They reported retry availability at September 6, 2026, 2:31 AM; actual account availability must be checked when resuming. No automatic restart is assumed. Do not silently substitute another model.

Resume only these two bounded reviews:

1. `/root/review_player_flows`: review current `904e67f` player-flow fixes against `reports/T4-player-flows-review.md` and its frozen `7367d29` references. Scope: commands, menus, permissions, strict item configuration, configured messages, recovery output, alias normalization, quote request identity/revision/cancel/reload behavior, and off-hand interaction wiring. Exclude `InventoryAdapter`, which this worker helped implement. The >45 eligible-deposit finding was withdrawn because eligibility is limited to 36 main slots with unique material mappings.
2. `/root/review_physical_transactions`: review the four `05e7ab4` findings against `904e67f`: retained SQLite/executor ownership across re-enable, exact note capacity planning before acquiring a guard, completion-scheduler cleanup of matching tokens, and UUID/name snapshots before worker calls. Also verify the `ac4d5bd` fix to off-hand `beforeEvidence()`/`afterEvidence()`. Existing coordinator sequencing and completed storage/CLI internals need no broad re-review.

Append dispositions to the existing reports. If an old agent cannot resume, use a fresh isolated Luna agent with the same bounded brief. Route concrete residual fixes to their original owner, add focused regressions, checkpoint, and re-review only those fixes. Do not restart completed modules or throw away interrupted edits.

## Verification and release limits

- Controller `mvn -B verify`: PASS, 63 tests (12 core, 17 storage, 34 plugin).
- GitHub Linux Java 17 and 21 verification: PASS at `904e67f`, run `34007572136`.
- Packaged dependency/SQLite restart smoke and offline CLI dry-run/restore smoke: PASS. Issued notes, pending evidence, balances, and source preservation were checked.
- Artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; SHA-256 `F9B0F5F1ED777EB77E52A0A24C51B5B0272537039E6FF34D2AD8E8CFAA728388`.
- No live Minecraft server has been started, no EULA accepted, and no exact server version is certified. This is a development build. Use the operations guide for later server validation.

After review clears, finish the task board/checklists and report the tested development build. Real server compatibility still requires exact server-build evidence and the operator's own EULA decision.
