# GoldBag UI and command audit

Date: 2026-10-11

Scope: `CommandParser`, `GoldBagCommand`, `plugin.yml`, `MenuService`,
`MenuHolder`, `GoldBagPlugin` menu/event handlers, command documentation, and
the in-progress clickable confirmation change. The command and menu workers
own production source; this report is review-only.

## Coverage matrix

| Entry point | Permission / sender | Preview and commit path | UI or command gap |
| --- | --- | --- | --- |
| `/goldbag`, `/gb`, `/purse` | `goldbag.use` or legacy use; player for menu | Main menu | These are the canonical menu aliases and remain available when compatibility aliases are disabled, per the accepted spec. |
| `balance [player]` and `/balance`, `/money` | Own balance node; other balance node; console allowed | Async account read | Alias behavior and target permission filtering are aligned. |
| `rates [material]` | `goldbag.use`; console allowed | Synchronous catalog display | Unknown material reports parser/catalog error. |
| `deposit <material> <count>` / `deposit all` | Player plus deposit node | Quote, confirmation inventory, guarded coordinator | Summary includes resource identity and deposit-all breakdown. |
| `withdraw <material> <count\|max>` | Player plus withdraw node | Quote, confirmation inventory, guarded coordinator | Summary identifies the material. |
| `pay <player\|uuid> <amount>` | Player plus pay node | Async recipient lookup then atomic transfer | Main-menu prompt is private and cancellable; malformed retry input stays in the prompt. |
| `note <amount>` | Player plus note node | Quote, confirmation inventory, guarded issue | Summary identifies banknote value and configured currency symbol. |
| `/withdraw <amount>` legacy | Player plus note node | Legacy alias maps to note quote | Must be unavailable when compatibility aliases are disabled. |
| Confirmation button / `/goldbag confirm` | Quoted operation permission | Quote identity + catalog revision + coordinator | UI button is the primary path; typed command remains a documented compatibility fallback. |
| Cancel button / `/goldbag cancel` / closing confirmation | Quoted operation permission | Removes only matching quote | Closing confirmation is identity-safe. A stale menu must close without consuming a replacement quote. |
| `top [page]` / `/pursetop` | Top node; console prints rows | Async page read and paginated menu | Page navigation and page completion exist. |
| `admin give\|take\|set ... reason` / `/purse ...` | Admin balance node; console allowed | Async lookup then transactional adjustment | Help and subcommand/online-player completion are permission-filtered. |
| `reload` | Admin reload node | Async candidate config then quote invalidation | Help exposed to authorized admins. |
| `storage status/export/import` | Admin storage node; console allowed | Async pending read / export write; import guidance | `import` is documented as an offline CLI instruction. |
| `recovery list/resolve` | Admin storage node; console allowed | Async pending inspection / audited resolution | Help and mode/reason completion are exposed to authorized admins. |

## Concrete blockers and findings

### P1 — confirmation did not show what would be exchanged (resolved)

The first clickable-confirmation draft displayed only operation kind, generic
count, amount, and before/after balances. The current implementation includes
the material and expands each `material=count` deposit-all payload, and passes
configured currency formatting into the summary. Unit coverage exercises the
payload summary. A live menu smoke remains useful for rendering, but no
source-level blocker remains here.

### P1 — concurrent draft had a compile break (resolved)

During the audit's first focused reactor run, the clickable UI branch called an
undefined `closeNextTick(Player)` at seven locations in `GoldBagPlugin`; Maven
failed plugin compilation with seven missing-symbol errors. The method was
added during the concurrent edit round. The final focused reactor and the
controller's full clean verification now compile and pass; no open compile
finding remains.

### P2 — completion and descriptor help are incomplete (resolved)

The current implementation provides permission-aware top-level and subcommand
completion, including enabled materials, online players, admin verbs, storage,
and recovery forms. `plugin.yml`, command help, and the operations documentation
now advertise the supported command families. No completion or descriptor gap
remains in the reviewed source.

### P2 — storage import is an accepted guidance command (resolved)

`CommandParser` accepts `/goldbag storage import`; dispatch tells the operator
to stop the server and invoke the offline CLI. The command table, README, help,
and parser usage string now document this guidance behavior and the valid
`status|export|import` forms. It is no longer an undocumented no-op or a
contradictory usage path.

### P2 — balance target completion is broader than command permission (resolved)

`/goldbag balance <player>` correctly rejects a sender without
`goldbag.balance.others` (or the admin fallback), and `onTabComplete` now
filters online-player suggestions behind that same target permission. The
`pay` target list remains available to senders with the pay permission.

### P2 — confirmation command decision must be explicit (resolved)

The UI flow opens a clickable confirmation inventory for all four quote types.
`/goldbag confirm` and `/goldbag cancel` remain as documented compatibility
fallbacks for command-only/scripted flows, while primary help and completion
keep the fallback hidden so the normal path is clickable.

The fallback path now closes only the matching confirmation holder on the next
tick and uses the quoted operation ID, so cancel/confirm cannot consume or close
a replacement quote owned by the same player.

## Verification

The pre-change parser/menu/unit suite was green in the saved status. A focused
reactor run during concurrent UI edits reached core/storage tests but failed at
plugin compilation on the missing `closeNextTick` method above. After that
method was added, the same command passed: core 12, storage 17, plugin 50,
zero failures. The final controller verification also reports 79 Java tests
passing and packaged verification passing. The native 1.21.11 harness reached
and exercised clickable confirmation menus through deposit, withdrawal, and
banknote flows, then stopped during the copied-banknote cross-player replay
check with B at G5.00 instead of G15.00; no confirmation-menu failure was
reported before that stop. The runtime result remains a separate integration
follow-up; all source-level findings in this audit are resolved.

Recommended final commands after the concurrent edits settle:

```text
mvn -B -pl goldbag-plugin -am test
git diff --check
```

The report intentionally leaves all production files untouched.
