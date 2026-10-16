---
name: goldbag-integration
description: Create, review and merge GoldBag feature PRs and verify semantic-release publication on the default branch.
---

# GoldBag integration and release

Read [goldbag-workflow](../goldbag-workflow/SKILL.md) for standing authorization and narrower task scope. Inspect fresh remote default branch, PR state, head/base and checks before acting. A previous PR may have been merged by the user during a pause; do not keep updating a merged PR with unrelated follow-up work.

Commit only task-owned files using Conventional Commits. Use `feat`/`fix` for intentional minor/patch releases; use a breaking footer only for real incompatibility. Push the feature branch, create or update a focused PR describing resulting behavior and relevant validation, and attach it with the Codex artifact tool. Avoid speculative version bumps or generated dist files in source.

For normal implementation scope, resolve material review findings, reconcile current main, and wait for required checks on the exact head before merging using the repository's allowed strategy. No extra confirmation is needed under the standing authorization. For review/draft/stage-only scope, stop at the requested deliverable. Record the merge SHA.

The existing default-branch workflow owns releases through `release.config.mjs`, root lockfile and `scripts/Prepare-GoldBagRelease.sh`. Preserve pinned dependencies/actions, full commit/tag history, least-privilege token permissions, verified packaging, JAR/checksum assets and main-run concurrency behavior. Do not manually create tags or a competing release to bypass CI. Check the merged-head workflow and resulting GitHub release/assets. Documentation-only commits may correctly produce no new release; distinguish that from a failed release job.

For release-tooling changes, run commit-analysis tests and disposable-copy preparation. Dry-runs must not publish; feature-branch release jobs should be skipped. Report known tooling audit findings accurately without claiming they are shaded runtime dependencies.

Proceed to [goldbag-deployment](../goldbag-deployment/SKILL.md) for requested local installation. If CI, review or publication fails, diagnose and fix within scope; preserve a durable report when external credentials, protection rules or missing runtime capabilities prevent completion. Never report a release or merge from an expected result.
