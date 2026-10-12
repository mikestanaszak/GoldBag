# Withdrawal menu protection review

## Result

Reviewed only the isolated `/tmp/goldbag-batch-final-evidence` diff. The fix correctly prevents player-inventory transfers into the withdrawal menu while preserving ordinary local pickup/place gestures. Top withdrawal slots remain cancelled, and the existing drag handler cancels non-deposit menus, including mixed top/bottom drags.

`allowsWithdrawalInventoryClick` admits only `LEFT`/`RIGHT` clicks and rejects `MOVE_TO_OTHER_INVENTORY` and `COLLECT_TO_CURSOR`. Because the listener sets withdrawal events cancelled before applying this helper, shift-clicks (`MOVE_TO_OTHER_INVENTORY`), double/collect gestures, number-key swaps, and cursor placement into top slots cannot move player items into the menu. A normal bottom inventory left/right pickup or placement remains native. The helper is narrow enough that command withdrawals and top resource icon actions are unaffected.

## Verification

- Focused Maven test: `mvn -B -pl goldbag-plugin -am -Dtest=GoldBagPluginInteractionTest -Dsurefire.failIfNoSpecifiedTests=false test` — **5 tests passed**.
- `git diff --check` — **passed**.
- Added actual-player fixture (controller live run pending): 37 cobblestone remains in the player inventory after a withdrawal-menu shift-click; cursor placement into an empty top slot is blocked; closing the menu still leaves exactly 37 cobblestone. The new smoke assertions also confirm no staged cobblestone is present.
- Existing collect/double-click protection remains covered by the interaction helper test. Native drag behavior remains protected by the withdrawal-menu drag cancellation path.

No further scoped finding remains in this isolated protection diff. The corrected behavior should be carried into the next published patch artifact and rechecked against the same actual-player fixture after installation.
