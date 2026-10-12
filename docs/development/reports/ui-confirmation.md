# UI confirmation flow

Date: 2026-10-11

The menu flow now opens a confirmation inventory after every successful deposit, deposit-all, withdrawal, and banknote quote. The confirmation inventory is a 27-slot menu with a green `LIME_WOOL` Confirm button at slot 11, a paper transaction summary at slot 13, and a red `RED_WOOL` Cancel button at slot 15. The summary includes operation kind, actual material/resource (including every material in deposit-all payloads), item or banknote count, amount, balance before, and balance after using the configured currency symbol. The existing `/goldbag confirm` and `/goldbag cancel` commands remain available for scripts and older clients.

`MenuHolder` stores the exact quote UUID, catalog revision, and balance snapshot. Confirmation clicks require the holder quote to still be current and to match the active catalog revision, so a replacement quote, reload, or expired quote cannot be confirmed from a stale inventory. Quote removal is identity-checked before execution, which also makes duplicate clicks harmless. Closing a confirmation removes only the quote represented by that holder; closing an older menu cannot cancel a replacement quote. Menu transitions and closes requested from `InventoryClickEvent` are scheduled for the next tick. Deposit pagination now handles the visible next-page arrow, and quantity menus expire after a catalog reload. Main menu deposit and withdrawal buttons enforce their permissions before opening a menu. Chat prompt validation runs synchronously through the shared parser so malformed input keeps the prompt active; transaction dispatch, exact-count quote creation, and prompt messages are scheduled on the main server thread. The command confirm/cancel fallback closes only the matching confirmation inventory on the next tick, leaving unrelated menus untouched.

## Verification

The new `MenuConfirmationTest` was written first. The focused red runs were:

```text
mvn -B -pl goldbag-plugin -am -Dtest=MenuConfirmationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The first run failed at test compilation because `CONFIRM`, quote identity, confirmation matching, and summary APIs did not exist. The follow-up red run for the transaction-limit clamp failed because `depositMaxCount` did not exist. After implementation, the same command passed all 6 tests, including replacement-close/duplicate-removal, material-aware summaries, and Max clamping.

The focused plugin reactor test was then run with:

```text
mvn -B -pl goldbag-plugin -am -DskipTests=false test
```

Core (12), storage (17), and all 49 plugin tests passed, including the concurrently edited command tests.

In-game inventory opening and closing still require a live Paper/Minecraft smoke pass. The code remains Java 16 bytecode compatible and uses Bukkit 1.17 API materials for the buttons.
