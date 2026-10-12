# Commands and permissions

`/goldbag` is the canonical command. The plugin's runtime help and generated messages are authoritative if a later implementation changes a command. The table records the approved interface and required permission nodes.

| Command | Purpose | Permission |
| --- | --- | --- |
| `/goldbag`, `/gb`, `/purse` | Open the purse menu | `goldbag.use` |
| `/goldbag help` | List commands and permitted administrator operations | `goldbag.use` |
| `/goldbag balance [player]` | View a balance; another player needs the second node | `goldbag.balance`, `goldbag.balance.others` |
| `/goldbag rates [material]` | Show exchange prices | `goldbag.use` |
| `/goldbag deposit <material> <count>` or `deposit all` | Preview a deposit | `goldbag.deposit` |
| `/goldbag withdraw <material> <count\|max>` | Preview a resource withdrawal | `goldbag.withdraw` |
| Green Confirm / red Cancel button | Complete or discard the displayed quote | Permission for the quoted operation |
| `/goldbag cancel` | Discard the active quote | Permission for the quoted operation |
| `/goldbag pay <player\|uuid> <amount>` | Transfer Gold to an existing account | `goldbag.pay` |
| `/goldbag note <amount>` | Preview a banknote | `goldbag.note` |
| Right-click a banknote | Redeem a banknote once | `goldbag.note` |
| `/goldbag top [page]` | Show ten balances per page | `goldbag.top` |
| `/goldbag admin <give\|take\|set> <player\|uuid> <amount> <reason>` | Audited balance adjustment | `goldbag.admin.balance` |
| `/goldbag reload` | Validate and apply reloadable settings | `goldbag.admin.reload` |
| `/goldbag storage status` | Show SQLite health and pending-operation count | `goldbag.admin.storage` |
| `/goldbag storage export` | Write a versioned JSON export in the plugin data directory | `goldbag.admin.storage` |
| `/goldbag storage import` | Show offline restore instructions; does not import on a running server | `goldbag.admin.storage` |
| `/goldbag recovery list` | List unresolved physical operations | `goldbag.admin.storage` |
| `/goldbag recovery resolve <op> <apply\|cancel> <reason>` | Audited resolution after checking the physical inventory | `goldbag.admin.storage` |

Normal use permissions are granted by the server owner. Administrator permissions default to operators. Explicit modern permission denial takes precedence over legacy grants. Console may run administration, rates, top, storage, and targeted balance queries; inventory operations require a player.

Optional compatibility aliases are `/balance`, `/money`, `/pursetop`, and `/withdraw <amount>`. The last alias retains its old banknote meaning. `/purse give|take|set` maps to administrator balance actions and requires a reason. Aliases can conflict with other plugins, so use `/goldbag ...` when diagnosing command routing.

Creative and spectator players are denied exchanges and banknote operations by default. A reload must validate the complete new configuration before activating it; a failed reload keeps the active configuration.

## Menu flow

Deposit and Withdraw Resources open catalog menus with previous/next arrows when needed. Choose an item, then 1, 16, 64, Max, or an exact count entered privately in chat. Deposit All previews all eligible main-inventory resources within the transaction limit. A successful preview opens a confirmation menu containing the exact resources, quantity, value, and balance before/after. Click green Confirm to execute, red Cancel to discard, or close the menu to cancel. Items and balance change only after confirmation. Expired, replaced, or reloaded previews cannot be confirmed from an old menu; repeated clicks do not execute twice.

Create Banknote asks for an amount and opens the same confirmation menu. Pay Player asks for the recipient and amount; a valid payment transfers immediately. Enter `cancel` to leave either chat prompt. Malformed inputs leave the prompt open so they can be corrected.

The previous `/goldbag confirm` command remains a compatibility fallback for scripts; players do not need to type it. Both fallback commands close a displayed confirmation. Tab completion includes permitted verbs, enabled resources, item counts, online recipients and administrator/storage/recovery subcommands. Optional legacy commands can conflict with Essentials and other plugins; use canonical `/goldbag` forms when checking routing.

To restore exported data, stop the server and use the packaged offline tool with a new destination database:

```sh
java -cp GoldBag-X.Y.Z.jar io.github.mikestanaszak.goldbag.cli.OfflineImport goldbag-export.json restored.db --validate-only
java -cp GoldBag-X.Y.Z.jar io.github.mikestanaszak.goldbag.cli.OfflineImport goldbag-export.json restored.db
```

Replace `X.Y.Z` with the installed version. `/goldbag storage import` prints guidance only and never modifies live data.
