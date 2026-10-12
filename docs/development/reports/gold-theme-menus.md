# Gold theme menu refinement

## Scope

This pass polishes the inventory menus rendered by `MenuService` while keeping the existing menu sizes, holder screens, clickable slot numbers, pagination, quote flow, and balance/economy operations unchanged.

## Visual and copy changes

- Standardized every menu title on the `GoldBag` gold title color. Existing title text and page suffixes remain compatible with callers and title-based checks.
- Standardized menu item names on gold, with gray instructional lore for quick scanning.
- Reworked the main dashboard, deposit vault, withdrawal shop, leaderboard, and confirmation copy to use short, friendly actions and clearer click instructions.
- Changed the deposit footer fill from gray glass to yellow glass for a warmer gold/yellow frame; the same footer slots remain interactive at 45, 48, 49, 50, and 52.
- Kept conventional lime confirmation and red cancellation materials so the transaction decision remains immediately recognizable.

## Compatibility notes

No transaction calculations, quote creation, account updates, inventory transfers, holder screen values, inventory sizes, or click slot mappings were changed. The refresh path still owns slot 50 on deposit and withdrawal screens, so its live balance label intentionally replaces the static helper text after the account lookup completes.

The repository's current menu tests cover holder/quote behavior rather than rendered title or lore strings. If a downstream UI test asserts an old item label (for example `Balance`, `Deposit`, or `Confirm`), update that assertion to the refined copy; title text remains `GoldBag`, `GoldBag Deposit`, `GoldBag Withdraw <page>`, `GoldBag Top <page>`, and `GoldBag Confirm`.

## Verification

Reviewed the listener's slot dispatch to confirm that all clickable slots and screen routing remain unchanged. Full Maven verification is owned by the controller after the parallel menu, chat, banknote, and test changes are combined.
