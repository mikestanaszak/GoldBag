# T7 README review

## Scope

Reviewed the revised `README.md` against the repository's current plugin descriptor, command parser/command handler, default resource and config files, Maven packaging, packaged verifier, CI workflow, and operations documentation. This was a bounded documentation review; no source or README edits were made.

## Checks performed

- `Get-Content -Raw README.md`
- Read `docs/operations/installation.md`, `commands-and-permissions.md`, `compatibility.md`, `backup-and-restore.md`, and `recovery.md`.
- Read `goldbag-plugin/src/main/resources/plugin.yml`, `config.yml`, and `resources.yml`, plus `goldbag-core/src/main/resources/defaults/resources.yml`.
- Read `CommandParser.java` and searched command handling/tests for deposit, withdrawal, confirmation, banknote, storage export, and restart behavior.
- Read the root and plugin POMs, `scripts/Invoke-GoldBagBuild.ps1`, `scripts/verification/PackagedJarVerification.java`, `scripts/server-tests/README.md`, and `.github/workflows/ci.yml`.
- Verified the README local links and diff whitespace independently (controller report: all 10 local links resolved; `git diff --check` passed).

## Findings

No actionable factual errors or unusable setup instructions found. The README's version/status, exact Paper smoke claim, 69 Java tests/five client lifecycle tests/16 player checks, default prices and 18 enabled catalog entries, command examples, Java bytecode/build requirements, packaged verification claims, backup/recovery cautions, and compatibility limitations agree with the inspected implementation and operations evidence.

## Remaining work

No README changes requested from this review. Root/controller should integrate this report with the README update and complete the existing PR workflow.

## Integration API

Report file: `docs/development/reports/T7-readme-review.md`.
Controller owns staging, commits, PR integration, and merge operations.
