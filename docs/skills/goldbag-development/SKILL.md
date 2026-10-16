---
name: goldbag-development
description: Implement and verify GoldBag economy, menus, commands, Minecraft compatibility and build changes, including isolated player smoke tests.
---

# GoldBag development and verification

Use [goldbag-workflow](../goldbag-workflow/SKILL.md) for project context and authorization. Work on a feature branch from fresh default-branch state; preserve unrelated changes and Git history. Resume an appropriate existing branch instead of replacing it. Read the accepted design and relevant operations docs before changing economy behavior.

## Product and runtime constraints

- Follow the current accepted chest interaction in AGENTS/STATUS; the batch deposit chest supersedes older per-stack/typed confirmation flows. Where a preview remains, show exact resource/quantity, currency, amount and balances with UI controls. Validate affected permissions, navigation, quantity limits and console feedback; do not reintroduce `/goldbag confirm` as the normal player flow.
- Preserve integer cents, original configured rates, SQLite journal/evidence, unique banknote redemption and duplicate/stale quote rejection. Database commits and Minecraft inventory saves are separate; quarantine uncertain operations instead of guessing balances. MySQL, interest, ingot exchange and Fabric are outside established scope unless the user requests a scope change.
- Keep public Spigot 1.17 API and Java 16 bytecode unless a demonstrated compatibility problem requires a change. Paper 26.3 runtime testing requires Java 25; compiler/API targets and server runtime are separate decisions. Inspect actual current versions rather than assuming a snapshot number.
- Bukkit inventory transitions should be deferred appropriately; chat input must schedule server/inventory work on the main thread. Closing an old UI must not cancel a replacement quote.

## Verification

For runtime changes, run focused regressions for meaningful changed behavior, then `mvn -B clean verify` and the packaged-JAR verifier documented in the repo. Use current CI's supported JDK matrix. Root `npm ci` and `npm test` verify release analysis; inspect `scripts/server-tests/README.md` for harness unit and player checks when relevant. Use available runtime tooling rather than hardcoded temporary paths.

For release preparation, use `scripts/Prepare-GoldBagRelease.sh` in a disposable clean copy so version rewriting does not dirty the feature branch. Verify descriptor/manifest agreement, bytecode, shaded SQLite/natives and artifact checksum. Never install a plain unshaded module JAR.

Isolated local validation servers may use the previously authorized `eula=true`. Keep fixtures, OP grants, inventory clearing and reset scripts on isolated loopback servers, never production. Verify exact server build/JDK, startup, storage/rates/reload, affected actual-player flows, clean restart persistence and note replay where applicable. Unsupported Mineflayer protocols or bridge disconnects are test limitations: Paper 1.21.11 player checks do not certify native 26.3 player flows. Record what passed and what remains manual.

Keep ordinary work inline. Optional bounded independent workers use the host's latest Luna low/medium for mechanical or local work, Sol medium for normal complex work, and Sol high for transaction/recovery review or ambiguous concurrency; xhigh needs interacting failure modes. Reuse the known host model list; do not spawn solely to change the inline model. Give workers isolated context (`fork_turns="none"`), relevant paths, owned files and acceptance; no nested delegation. One writer owns shared build/index/commits. Use completion events or bounded waits and one task evidence record; inspect saved diffs/evidence after interruption instead of restarting research. Reuse checks for the unchanged tested tree/environment. Independently review material transaction/runtime changes before integration. Continue through [goldbag-integration](../goldbag-integration/SKILL.md) within the requested scope.
