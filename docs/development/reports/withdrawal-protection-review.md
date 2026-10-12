# Withdrawal menu protection review

## Result

Reviewed only the isolated `/tmp/goldbag-batch-final-evidence` diff. The fix correctly prevents player-inventory transfers into the withdrawal menu while preserving ordinary local pickup/place gestures. Top withdrawal slots remain cancelled, and the existing drag handler cancels non-deposit menus, including mixed top/bottom drags.

`allowsWithdrawalInventoryClick` admits only `LEFT`/`RIGHT` clicks and rejects `MOVE_TO_OTHER_INVENTORY` and `COLLECT_TO_CURSOR`. Because the listener sets withdrawal events cancelled before applying this helper, shift-clicks (`MOVE_TO_OTHER_INVENTORY`), double/collect gestures, number-key swaps, and cursor placement into top slots cannot move player items into the menu. A normal bottom inventory left/right pickup or placement remains native. The helper is narrow enough that command withdrawals and top resource icon actions are unaffected.

## Verification

- Focused Maven test: `mvn -B -pl goldbag-plugin -am -Dtest=GoldBagPluginInteractionTest -Dsurefire.failIfNoSpecifiedTests=false test` — **5 tests passed**.
- `git diff --check` — **passed**.
- Actual-player fixture PASS on the protection snapshot (controller full25-check run): 37 cobblestone remains in the player inventory after a withdrawal-menu shift-click; cursor placement into an empty top slot is blocked; closing the menu still leaves exactly 37 cobblestone. The new smoke assertions also confirm no staged cobblestone is present.
- Existing collect/double-click protection remains covered by the interaction helper test. Native drag behavior remains protected by the withdrawal-menu drag cancellation path.

No further scoped finding remains in this isolated protection diff. The corrected behavior should be carried into the next published patch artifact and rechecked against the same actual-player fixture after installation.

The gold-theme PR #9 merged during validation; controller merged it into this branch cleanly and retains the bottom transfer guard. Combined build/live verification follows before integration.

## Combined controller evidence

Merged protection source via PR #8 at `7f80e5d3134bc2e9d3ec07c00fb302cb0059ecae`, preserving gold-theme PR #9. All exact-head Java17/21/25, harness and release checks passed. Combined full reactor87 tests and packaged verifier passed; isolated Paper1.21.11 build132/Temurin Java25.0.4.1+1 full harness **25 actual-player checks PASS**, including37 cobblestone retained after shift/cursor attempts and close. Snapshot SHA-256 `f19aa4bed1a248bed788a0042899b6b9b624aafce9f12bf7f3718f210e34ef00`; log `/tmp/goldbag-withdraw-protection-combined-player.log`. Native top/mixed drag and Paper26.3/Geyser gesture behavior remain manual.

The user confirmed they already restored the lost items for the affected player. No additional refund, inventory grant or production fixture was performed by this task. The first publication run38297605514 skipped because a subsequent plain-wording merge advanced main. The latest combined source `843ec250872081a71fca84ee61d124b1cd4bcd03` includes the protection and completed release run38297690831, publishing [v2.3.1](https://github.com/mikestanaszak/GoldBag/releases/tag/v2.3.1).

Final verification: published checksum and shaded verifier PASS; a direct check of the published JAR's withdrawal guard rejects SHIFT_LEFT, SHIFT_RIGHT and MOVE_TO_OTHER_INVENTORY while allowing local pickup. Published/installed SHA-256 `efeb8a36c8e58b92dbec18909eb972395031a2d5489f1128b0740bae3741080a`. The shared plain-wording deployment had already installed the identical JAR; this task verified activation and did not restart it again. Backup `/opt/stacks/maya-funtimes/data/plugin-backups/2026-10-12-plain-wording-2.3.1` preserves the previous2.3.0 JAR and stopped plugin data.

GoldBag2.3.1 enabled on Paper26.3 build177 `fbec96a`/Java25.0.4.1+1; RCON version/rates confirmed, container running/healthy. All4 account balance/revision rows match the consistent pre-activation backup; pending0; quick_check ok. State `/tmp/goldbag-withdraw-protection-deployment.json`. Same protection snapshot no-reset restart also PASS (saved balances/note and copied-note replay rejection), log `/tmp/goldbag-withdraw-protection-restart.log`.
