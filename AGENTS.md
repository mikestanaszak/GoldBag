# GoldBag development and recovery

Read `RESUME.md`, `docs/development/STATUS.md` and the current task before editing. The accepted
specification is `docs/superpowers/specs/2026-09-05-goldbag-design.md`; the original task plan is
`docs/superpowers/plans/2026-09-05-goldbag-rebuild.md`.

Use the shared `goldbag` skill (build and release commands, installation, hard safety rules) with
`mike-delivery`, from `mikestanaszak/agent-skills` (`~/Documents/ChatGPT/agent-skills`), installed
for Claude Code and Codex.

## Working agreement

- Mike's standing authorization covers review, merge after passing checks, the semantic-release
  publish, and installing the release on the local `maya-funtimes` server with a restart. Narrower
  instructions (review only, draft, no merge, "I will restart") win.
- The current session owns a task end to end and self-reviews its own diff; use a separate
  reviewer or sub-agent only when Mike asks.
- Work on a feature branch from the verified current `main`; preserve Git history and unrelated
  work. Keep one compact evidence record: the PR for small work, `docs/development/reports/` for
  runtime or recovery work. Make small checkpoint commits after each meaningful chunk.
- The player flow is the batch deposit chest with one Deposit button (v2.2.1); don't reintroduce
  the per-stack instant conversion or `/goldbag confirm`. Keep the approved October 12 resource
  values and any configured custom rates.
- No MySQL, ingot exchange, interest or Fabric. All values are integer cents.
- Target the Bukkit/Spigot 1.17 public API with Java 16-compatible bytecode (develop on JDK 21).
  Exact server versions are supported only after testing.
- Run focused tests first, then `mvn -B clean verify` for runtime integration. Docs-only edits need
  link checks and `git diff --check`, not server operations. Don't claim in-game behavior or
  compatibility from compilation alone.
- Accept the Minecraft EULA only on isolated local validation servers (allowed since 2026-09-06);
  never buy or provision hosting.
- Database commits and Minecraft inventory saves are separate steps. Keep pending transaction
  evidence; quarantine uncertain operations instead of guessing.

## Resume safety

Never start over because conversation context is missing. Inspect STATUS, reports, `git status`
and recent commits. Completed tasks stay completed unless a concrete failure requires a fix. Local
files and commits survive usage limits; uncommitted edits survive too but need review.
