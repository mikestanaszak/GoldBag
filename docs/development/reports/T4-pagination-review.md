# T4 pagination independent review

Scope: frozen commit `51238d8` (`5b2efb8..51238d8`), limited to
`MenuService`, `MenuHolder`, and `MenuPaginationTest`. Production code was
reviewed read-only; no staging or commit was performed.

## Disposition: clear

The withdrawal renderer and holder use the same `hasNextResourcePage(page,
total)` calculation. It correctly hides the next arrow at 45 entries, shows
it for 46 entries, and continues to handle later page boundaries. The holder
also carries that result, and the click handler gates navigation on
`holder.hasNext()`, so a stale or forged next-page click cannot open a page
past the rendered resource list.

Leaderboard rendering now performs a storage lookahead for `page + 1` before
opening the menu. The worker callback only reads `SqliteStore.top`; the
success callback is routed through `GoldBagPlugin.submit` back to the server
thread before `renderTop` accesses `Player`, Bukkit inventories, or menu
holders. An empty lookahead suppresses the next arrow, while a nonempty page
enables it. Exact 10- and 20-account totals therefore terminate on their last
full page, and partial totals such as 11 expose exactly one next page.

The extra lookahead means a transient leaderboard read failure reports the
existing storage-unavailable message and does not render that page. This is
consistent with the storage failure rule and does not create an item or money
correctness problem. The general menu callback can still arrive after a
player closes or changes menus while a read is pending, but that behavior
predates this change and is not a pagination-specific defect.

## Verification

Ran:

`mvn -B -pl goldbag-plugin -am '-Dtest=MenuPaginationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Result: PASS, 2 tests, 0 failures, with core, storage, and plugin modules
building successfully.
