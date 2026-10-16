# GoldBag development and recovery

Read `RESUME.md`, `docs/development/STATUS.md`, and the current task brief before editing.
The accepted specification is `docs/superpowers/specs/2026-09-05-goldbag-design.md`.
The executable task plan is `docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`.

## Current follow-up (2026-10-12, batch chest)

The owner found immediate per-stack deposits laggy and requested a true editable deposit chest followed by one Deposit button. This supersedes the earlier instant-conversion gesture UX. Work on `codex/batch-deposit-chest` from fresh main. Staging has no per-stack economy transaction or invalid-item chat. Sell eligible staged items in one journaled batch, return unsold items on close/switch, and preserve durable evidence for interrupted storage. Keep the approved resource values. Queue rapid withdrawals safely and clear queued requests when their screen closes.

v2.1.0 from PR #4 is published and installed; PR #5 recorded its release evidence. PRs #6/#7 merged and v2.2.1 is published/installed with the batch chest, queued withdrawals and approved values. See batch-release-deployment.md for exact evidence and remaining manual flows. Standing workflow authorization covers review, passing-check integration, release and appropriate local deployment/restart. Keep the Spigot 1.17/Java 16 baseline and exact integer cents.

## Working agreement

- Use [goldbag-workflow](docs/skills/goldbag-workflow/SKILL.md) and only the relevant capability. These versioned skills are canonical; maintain byte-identical personal copies under `~/.codex/skills/`.
- The current chat owns ordinary work inline. Optional independent workers use the latest host Sol/Luna by risk as defined in the development skill, isolated context and bounded tasks; no mandatory handoff or nested agents. Material transaction/runtime changes retain independent correctness review.
- Preserve Git history and unrelated work. Use a suitable feature branch from the verified current default branch; historical task branch names above are resumption evidence, not instructions for new tickets.
- Explicit integration authorization recorded 2026-09-06: the user requested creating a PR to the default branch and merging it once the work is done. Complete outstanding fixes/reviews/checks, then merge that PR into the verified default branch (`main`) while preserving history.
- Workers own only the files named in their brief. No nested subagents. No edits to another worker's files without coordinating with the controller.
- Keep one compact task evidence record (PR for small work; `docs/development/reports/` for runtime/recovery work). Worker returns changed paths, exact checks/revision, findings and remaining limits; save resumable evidence before long checks. Do not duplicate the record per worker.
- Controller owns shared build files, task board, integration, and Git commits. Workers must not stage or commit while running concurrently: shared index operations are serialized by the controller.
- Save source changes incrementally. Make small scoped checkpoint commits after each meaningful completed chunk, including reports. Incomplete work may be checkpointed only when clearly labeled WIP and described in STATUS.
- No MySQL, ingot exchange, interest, or Fabric implementation. All values are integer cents. Use the user-approved October 12 resource values; preserve configured custom rates outside explicitly authorized rebalancing.
- Target Bukkit/Spigot public API 1.17 with Java 16-compatible bytecode; current development JDK is 21. Dependencies must remain compatible with the target runtime. Exact server versions are supported only after testing.
- Use focused tests first, then full reactor/package verification for runtime integration. Reuse evidence for the unchanged tested tree/environment; rerun invalidated checks after changes or failures. Docs/skills-only edits use frontmatter, affected links, behavior scenarios and `git diff --check`, without server operations. Do not claim in-game behavior or compatibility from compilation alone.
- Do not accept Minecraft's EULA for the user or buy/provision external hosting. Local server preparation may download artifacts and prepare instructions without accepting terms.
- Explicit exception recorded 2026-09-06: the user instructed "Set EULA to true and also test the server." The controller may set `eula=true` and run the isolated local validation server; public deployment and hosting purchases remain outside this authorization.
- Preserve pending transaction evidence. Database commit and Minecraft inventory persistence are separate; uncertain operations must be quarantined, not guessed at.

## Resume safety

Never start over because conversation context is absent. Inspect STATUS, reports, `git status`, and recent commits. Completed tasks stay completed unless a concrete failure requires a fix. If a worker was interrupted, inspect its files and report and resume only the remaining steps. Local files and commits survive usage exhaustion; uncommitted edits also survive but require review. Automatic continuation after limits reset is not assumed.
