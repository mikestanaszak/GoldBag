# Resume GoldBag

Repository: https://github.com/mikestanaszak/GoldBag

Development branch: `codex/goldbag-rebuild`. Original base: `45c3492`.

1. Read `AGENTS.md` and `docs/development/STATUS.md`.
2. Inspect `git status --short` and `git log -8 --oneline`.
3. Read the report for the next unfinished task in `docs/development/reports/` and its task section in the plan.
4. Reuse existing files; do not reimplement completed tasks. Run the listed focused check to establish current state.
5. Assign independent remaining tasks to **gpt-5.6-luna** subagents. Respect file ownership. The controller alone commits.
6. Save reports and update STATUS after each checkpoint. Run `mvn -B verify` before declaring an integrated build passing.

Suggested continuation request:

> Continue GoldBag from RESUME.md using 5.6 Luna subagents. Preserve completed work, resume unfinished tasks from the saved reports, and keep checkpointing progress.

Usage exhaustion stops active work; it does not delete the repository, task reports, or commits. Work does not automatically restart when usage resets. Reopen this project and use the continuation request above. Remote branch backup status is recorded in STATUS; do not assume an unpushed local commit exists on GitHub.

All implementation and scoped reviews are complete. Final verification: 69 Java tests, five Node tests, 16 actual-player checks plus clean restart, notes, recovery, and restore. Integration is [PR #1](https://github.com/mikestanaszak/GoldBag/pull/1) into default branch main; GitHub is authoritative for its check/merge status. The user explicitly authorized merging that PR after checks. Do not restart completed development or consumed test fixtures. The local Paper server is stopped with eula=true saved. Additional versions and extended fault validation remain documented future work.
