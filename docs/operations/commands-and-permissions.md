# Commands and permissions

`/goldbag` is the canonical command. The plugin's runtime help and generated messages are authoritative if a later implementation changes a command. The table records the approved interface and required permission nodes.

| Command | Purpose | Permission |
| --- | --- | --- |
| `/goldbag` | Open the purse menu | `goldbag.use` |
| `/goldbag balance [player]` | View a balance; another player needs the second node | `goldbag.balance`, `goldbag.balance.others` |
| `/goldbag rates [material]` | Show exchange prices | `goldbag.use` |
| `/goldbag deposit <material> <count>` or `deposit all` | Preview a deposit | `goldbag.deposit` |
| `/goldbag withdraw <material> <count\|max>` | Preview a resource withdrawal | `goldbag.withdraw` |
| `/goldbag confirm` / `/goldbag cancel` | Confirm or cancel the current quote | Permission for the quoted operation |
| `/goldbag pay <player\|uuid> <amount>` | Transfer Gold to an existing account | `goldbag.pay` |
| `/goldbag note <amount>` | Preview a banknote | `goldbag.note` |
| Right-click a banknote | Redeem a banknote once | `goldbag.note` |
| `/goldbag top [page]` | Show ten balances per page | `goldbag.top` |
| `/goldbag admin <give\|take\|set> <player\|uuid> <amount> <reason>` | Audited balance adjustment | `goldbag.admin.balance` |
| `/goldbag reload` | Validate and apply reloadable settings | `goldbag.admin.reload` |
| `/goldbag storage status` | Show backend, health, schema, and pending operations | `goldbag.admin.storage` |
| `/goldbag storage export` | Write a versioned JSON export in the plugin data directory | `goldbag.admin.storage` |
| `/goldbag recovery list` | List unresolved physical operations | `goldbag.admin.storage` |
| `/goldbag recovery resolve <op> <apply\|cancel> <reason>` | Audited resolution after checking the physical inventory | `goldbag.admin.storage` |

Normal use permissions are granted by the server owner. Administrator permissions default to operators. Explicit modern permission denial takes precedence over legacy grants. Console may run administration, rates, top, storage, and targeted balance queries; inventory operations require a player.

Optional compatibility aliases are `/balance`, `/money`, `/pursetop`, and `/withdraw <amount>`. The last alias retains its old banknote meaning. `/purse give|take|set` maps to administrator balance actions and requires a reason. Aliases can conflict with other plugins, so use `/goldbag ...` when diagnosing command routing.

Creative and spectator players are denied exchanges and banknote operations by default. A reload must validate the complete new configuration before activating it; a failed reload keeps the active configuration.
