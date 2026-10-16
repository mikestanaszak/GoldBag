---
name: goldbag-workflow
description: Maintain, fix, review and release Mike's GoldBag Minecraft plugin, including its personal skills and maya-funtimes Dockge installation.
---

# GoldBag workflow

Repository: https://github.com/mikestanaszak/GoldBag. Discover an existing checkout from the current project before cloning another. The known project mirror contains `GoldBag/`; synced `sources/` files are read-only. Read repo `AGENTS.md` and task-relevant source/docs. On resumption, use `RESUME.md`, current `docs/development/STATUS.md` entries and linked reports to finish only remaining work; do not reload every historical report. Check current GitHub state; cached PR/release history is not authoritative.

## Standing authorization

On 2026-10-11 Mike requested: “Make it a skill to do all of this, I don’t want to approve anything. Make skills similar to tee time dashboard.” For requested GoldBag implementation work, this authorizes feature-branch implementation, proportionate tests and review, commits/pushes, PR creation, merge after passing checks, existing semantic-release publication, and appropriate installation and Minecraft-service restart in the local `maya-funtimes` Dockge stack. Carry this work through without repeated confirmation questions. This later instruction supersedes the earlier PR-only/no-merge restriction for normal requested implementation work. Creating these skills alone is not a request to merge an existing PR or restart a server.

Honor narrower instructions such as review-only, draft, no-merge, stage-only or “I will restart.” Authorization covers GoldBag and its existing local installation; it does not authorize messages to others, unrelated stacks, purchases, destructive data resets or infrastructure replacement. Report actual blockers; do not invent approval gates or bypass enforced tool approvals. If a required operation cannot be performed, preserve the work and state the exact gap.

## Capabilities

- Personal skill creation or maintenance: use the host's `skill-creator` skill, validate frontmatter, metadata and links, and scenario-check material authorization/recovery changes. Keep skill-only requests within the requested skill files unless repository integration is also requested.
- Implementation, compatibility and player experience: [goldbag-development](../goldbag-development/SKILL.md).
- PR review, merge and semantic release: [goldbag-integration](../goldbag-integration/SKILL.md).
- Artifact staging, Minecraft restart and runtime validation: [goldbag-deployment](../goldbag-deployment/SKILL.md).

Maintain these four skill directories in repo `docs/skills/`; install byte-identical personal copies after validation, preserving unrelated installed customizations. Ordinary tasks stay inline; load only relevant skills/references. Small prose edits need owner review, link/frontmatter and diff checks, without application builds or server operations. Save durable task evidence in repo STATUS/reports, including source SHA, tested artifact hash, PR/release and installed state. Report source integration, published release and running server state separately.
