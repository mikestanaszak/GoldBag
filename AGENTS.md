# GoldBag development and recovery

Read `RESUME.md`, `docs/development/STATUS.md`, and the current task brief before editing.
The accepted specification is `docs/superpowers/specs/2026-09-05-goldbag-design.md`.
The executable task plan is `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`.

## Current follow-up (2026-10-12)

The user requests immediate chest-style deposits and direct withdrawals without confirmation or quantity screens. Update the open UI PR #4 on `codex/ui-confirmation-experience`; its prior confirmation UX is superseded for resources. Banknotes may retain their existing confirmation. The user approved applying the revised resource table; see the accepted specification amendment and resource-value-review report.

The GoldBag workflow skill records later standing authorization for implementation, review, passing-check integration, semantic release and appropriate local `maya-funtimes` installation/restart. It supersedes historical PR-only instructions below. Keep the Spigot 1.17/Java 16 baseline; use Java 25 for Paper 26.3 runtime validation.

## Working agreement

- The user explicitly requests **gpt-5.6-luna** subagents for parallel implementation. Use that model for implementation and review agents; do not silently upgrade it. Use isolated context and bounded tasks.
- Preserve the original Git history. Work on `codex/goldbag-rebuild`; do not push or merge `main`.
- Explicit integration authorization recorded 2026-09-06: the user requested creating a PR to the default branch and merging it once the work is done. Complete outstanding fixes/reviews/checks, then merge that PR into the verified default branch (`main`) while preserving history.
- Workers own only the files named in their brief. No nested subagents. No edits to another worker's files without coordinating with the controller.
- Workers write their own tracked report in `docs/development/reports/`. Save it before long checks and before finishing; include exact commands, outcomes, remaining work, and integration API details.
- Controller owns shared build files, task board, integration, and Git commits. Workers must not stage or commit while running concurrently: shared index operations are serialized by the controller.
- Save source changes incrementally. Make small scoped checkpoint commits after each meaningful completed chunk, including reports. Incomplete work may be checkpointed only when clearly labeled WIP and described in STATUS.
- No MySQL, ingot exchange, interest, or Fabric implementation. All values are integer cents. Use the user-approved October 12 resource values; preserve configured custom rates outside explicitly authorized rebalancing.
- Target Bukkit/Spigot public API 1.17 with Java 16-compatible bytecode; current development JDK is 21. Dependencies must remain compatible with the target runtime. Exact server versions are supported only after testing.
- Use focused tests first, then full reactor verification at integration. Do not claim in-game behavior or compatibility from compilation alone.
- Do not accept Minecraft's EULA for the user or buy/provision external hosting. Local server preparation may download artifacts and prepare instructions without accepting terms.
- Explicit exception recorded 2026-09-06: the user instructed "Set EULA to true and also test the server." The controller may set `eula=true` and run the isolated local validation server; public deployment and hosting purchases remain outside this authorization.
- Preserve pending transaction evidence. Database commit and Minecraft inventory persistence are separate; uncertain operations must be quarantined, not guessed at.

## Resume safety

Never start over because conversation context is absent. Inspect STATUS, reports, `git status`, and recent commits. Completed tasks stay completed unless a concrete failure requires a fix. If a worker was interrupted, inspect its files and report and resume only the remaining steps. Local files and commits survive usage exhaustion; uncommitted edits also survive but require review. Automatic continuation after limits reset is not assumed.
