# T4 player and admin flow review (frozen `7367d29`)

Scope: static review of `CommandParser`, `GoldBagCommand`, `MenuService`,
`MenuHolder`, `PermissionService`, `QuoteBook`, `PluginConfig`, plugin
resources, and the command/menu/quote methods in `GoldBagPlugin` at commit
`7367d29`. This review does not assess `InventoryAdapter`,
`ExchangeCoordinator`, `StorageExecutor`, or storage shutdown behavior. No
reactor build was run; the workspace had unrelated active edits, so all line
references below come from `git show 7367d29:<path>`.

## Findings

### P1 — Main-menu clicks bypass balance and leaderboard permissions

Files/lines: `GoldBagPlugin.java:202-205`, `GoldBagPlugin.java:515-516`.

The command dispatcher protects `/goldbag balance` with
`goldbag.balance` and `/goldbag top` with `goldbag.top`, but the main-menu
click handler calls `showBalance` and `showTop` directly without checking
either node. A player who has `goldbag.use` but is explicitly denied
`goldbag.balance` or `goldbag.top` can open `/goldbag`, click Balance or Top
Balances, and receive the protected result. The same bypass is reachable from
the raw-gold shortcut because it opens the same menu.

Reproduction: grant a player `goldbag.use`, set explicit `goldbag.balance`
and/or `goldbag.top` to false, open `/goldbag`, click slot 10 or slot 22, and
observe the balance/leaderboard response. Expected: the click is rejected with
the corresponding permission failure and no protected data is shown.

### P1 — A quote request already in flight can publish after reload using the old price

Files/lines: `GoldBagPlugin.java:161-173`, `GoldBagPlugin.java:285-297`,
`GoldBagPlugin.java:301-322`, `GoldBagPlugin.java:355-367`.

The deposit amount is calculated before the asynchronous account read, while
the completion callback stores the quote with whatever catalog revision exists
at callback time. If the account read is delayed, `/goldbag reload` can swap in
a new catalog and invalidate quotes, then the old callback can put a quote with
the new revision but the old amount and payload. Confirmation accepts it
because `confirm` only compares the stored revision with the current one. The
same timing applies to deposit-all; withdrawal computes its amount from a
resource object captured before the read and can also complete after reload.

Reproduction: start `deposit raw_iron 1` (or `deposit all`) while delaying its
storage account callback; reload with a changed deposit price; release the old
callback; then run `/goldbag confirm`. The operation is accepted at the
pre-reload price even though the reload was supposed to expire pending quotes.
Capture the catalog revision/config snapshot when the request starts and reject
the callback if it is no longer active, or invalidate the callback by request
identity before publishing a quote.

### P1 — Startup validation accepts `AIR` and other non-item materials

File/lines: `PluginConfig.java:56-66`.

Runtime validation only checks `Material.matchMaterial(resource.material())`.
It does not reject materials that are not usable inventory items, including
`AIR` (and block-only materials where an item stack cannot be created). The
core catalog therefore accepts a configuration such as
`material: AIR`; startup reports success, while a withdrawal path later calls
`new ItemStack(material, count)` in `GoldBagPlugin.java:449-452` and can fail or
present a non-actionable menu. The configuration contract requires applicable
materials to be rejected before activation with an actionable startup error.

Reproduction: add a resource with `material: AIR`, restart, and observe that
GoldBag enables; invoke `/goldbag withdraw air 1` or open the withdrawal menu.
Expected: startup disables GoldBag while identifying the unsupported
non-item material.

### P2 — `/goldbag rates` without a material contradicts the catalog behavior

File/lines: `GoldBagCommand.java:72`; the null-capable implementation is
`GoldBagPlugin.java:517-521`.

`GoldBagPlugin.showRates` already has a branch to list every catalog resource
when its material is null, but the command dispatcher prevents that branch and
instead sends `Use /goldbag rates <material>.` The required no-argument form
must display the catalog.

Reproduction: run `/goldbag rates` as a player with `goldbag.use`. Expected:
all enabled resources and both prices are listed; actual: only the usage hint
is sent.

### P2 — GUI Close controls do not close or cancel their menus

Files/lines: `MenuService.java:53-55`, `MenuService.java:69-72`,
`MenuService.java:84-87`, `MenuService.java:98-100`, and
`GoldBagPlugin.java:202-229`.

Deposit, withdraw, top, and quantity screens render a `Close` barrier, but the
click handler has no branch for the barrier slots (49, 22, or 26). The event is
cancelled and the inventory remains open. A player must press the client close
key to trigger `InventoryCloseEvent`; the visible cancel control is inert.

Reproduction: open any of those screens and click its displayed Close item.
Expected: the menu closes and any preview remains unchanged; actual: nothing
happens. Add explicit close handling for each screen and keep the existing
quote cancellation-on-close behavior.

### P2 — Withdraw and top pagination advances even when no next page exists

Files/lines: `GoldBagPlugin.java:216-222` and `MenuService.java:69-70`,
`MenuService.java:84-85`.

The click handler opens `page + 1` whenever slot 53 (withdraw) or slot 26
(top) is clicked, without checking that the holder actually rendered a next
arrow. Empty slots are still cancellable click targets, so a player can
advance indefinitely through empty pages after the last page.

Reproduction: open the last withdrawal/top page, click the empty next-arrow
slot, and repeat. Expected: no navigation when no next page exists; actual:
empty page inventories continue to open. Gate the handler on the same
`has-next` condition used by the renderer.

### P2 — Configured messages are validated and reloaded but never displayed

Files/lines: `PluginConfig.java:19-23`, `PluginConfig.java:54`, and
`GoldBagPlugin.java:284-323`, `GoldBagPlugin.java:411-489`,
`GoldBagPlugin.java:533`.

`messages.yml` is parsed into the active snapshot and `message(key, fallback)`
exists, but no plugin flow calls it. User-facing responses throughout quote,
confirmation, failure, balance, and storage paths are hardcoded. Editing
`messages.yml` and running `/goldbag reload` therefore reports success while
the configured strings have no effect, contrary to reload applying messages.

Reproduction: change `messages.yml`'s `balance` or `storage-unavailable` value,
reload, then use `/goldbag balance` or trigger storage unavailability. The
hardcoded English response is still sent. Route user-facing common responses
through the active message snapshot, with placeholder substitution where
needed.

### P2 — Recovery listing omits the player and physical evidence needed for a safe decision

File/lines: `GoldBagCommand.java:127-129`.

`recovery list` prints only operation ID, kind, amount, and state, although
`SqliteStore.Pending` also carries `playerId` and the payload containing the
material/note and slot evidence. An operator cannot identify the affected
player or inspect the recorded physical evidence from the command output before
choosing `apply` or `cancel`, encouraging an unsafe resolution by operation ID
alone.

Reproduction: leave an APPLYING/PREPARED physical operation pending and run
`/goldbag recovery list`. Expected: each row identifies the player and exact
recorded evidence needed for reconciliation; actual: those fields are absent.

### P2 — Namespaced legacy aliases are detected for disabling but cannot be translated when enabled

File/lines: `GoldBagCommand.java:43-57`.

`isLegacyAlias` strips a namespace (`plugin:balance`) before checking
`legacy-aliases`, but `parseAlias` compares the unstripped label. With legacy
aliases enabled, `/goldbag:balance` and `/goldbag:withdraw 2.50` do not enter
the alias translators and fall through to `CommandParser.parse(args)`, yielding
the main menu or an unknown-verb error. With aliases disabled they are rejected,
so the same namespaced entry points behave inconsistently with their
unnamespaced forms.

Reproduction: run a namespaced legacy command through Bukkit's command
dispatcher, for example `/goldbag:balance` or `/goldbag:withdraw 2.50`, with
`legacy-aliases: true`. Expected: the same balance or banknote behavior as the
unnamespaced alias; actual: the label is not translated. Normalize the label
once and use that value for both gating and translation.

### P2 — `/goldbag cancel` has no quoted-operation permission check

File/lines: `GoldBagCommand.java:78-79`; quote permission checks are otherwise
in `GoldBagPlugin.java:411-423`.

The command dispatcher lets any player invoke `cancel`, and `MenuService.cancel`
removes the current quote without checking the permission associated with its
kind. The specification assigns `confirm` and `cancel` the same permission as
the quoted operation. This is a direct command/GUI permission contract gap
(even though cancellation does not debit money).

Reproduction: create a deposit or withdrawal quote while permitted, explicitly
deny the corresponding modern permission, then run `/goldbag cancel`. Expected:
the operation-specific permission failure is returned and the quote remains
available until expiry; actual: the quote is removed.

### P2 — Banknotes in the off-hand cannot be redeemed

File/lines: `GoldBagPlugin.java:240-257`.

The interaction handler returns immediately unless `event.getHand()` is
`EquipmentSlot.HAND`. A valid trusted note held in the off-hand therefore never
reaches redemption, despite the requirement that right-clicking a valid
banknote redeems it. The raw-gold shortcut can keep its main-hand-only behavior
to avoid duplicate off-hand events, but note redemption should accept the event
hand and guard/consume that exact slot.

Reproduction: place a valid issued note in the off-hand and right-click. Expected:
one redemption; actual: no cancellation, journal operation, or credit.

### Withdrawn — Deposit selection silently drops configured resources after the first 45

File/lines: `MenuService.java:41-55`.

The deposit screen collects every eligible resource but stops rendering at slot
45 with no page controls. The configuration explicitly permits additional
vanilla resources, so a catalog with more than 45 eligible entries makes later
resources inaccessible through the selection menu (even though deposit-all and
commands may still see them).

Disposition: the finding is unreachable under the current implementation. The
deposit eligibility scan is limited to the player's 36 main inventory slots,
and the catalog rejects duplicate material mappings, so one inventory cannot
contain more than 36 distinct eligible resource materials. No implementation
change is required for this checkpoint.

## Final disposition at frozen checkpoint `904e67f`

The scoped player-flow fixes were re-reviewed against the findings above. The
controller reports `mvn -B verify` passing all 63 tests at this checkpoint; the
dispositions below are based on the frozen source diff and focused tests. The
InventoryAdapter and physical-plan implementation are intentionally excluded
from this review.

- **Main-menu permission bypass — fixed.** Main-menu Balance and Top Balances
  now check their operation permissions before reading data, and the public
  `showBalance`/`showTop` methods repeat the checks for other entry points.
- **Stale quote after reload — fixed.** `QuoteRequestBook` binds each async
  account read to the player, request UUID, and catalog revision. Reload clears
  requests and invalidates the catalog before delayed callbacks can publish.
  Newer requests, close, quit, confirm, and cancel invalidate older requests.
- **Non-item/AIR configuration — fixed.** Runtime material validation now
  requires a Bukkit item and rejects AIR; `PluginConfigTest` covers AIR.
- **Argumentless rates — fixed.** The dispatcher now passes a null material to
  the catalog-listing branch.
- **GUI close controls and top pagination — fixed.** Close slots call
  `closeInventory`, and top next-page handling is gated by the holder's
  `hasNext` state. Deposit paging was added as well. The previously reported
  deposit-over-45 case remains withdrawn because the reachable eligibility set
  is bounded by 36 main slots and unique material mappings.
- **Configured messages unused — fixed for the configured common keys.** The
  balance, busy, storage-unavailable, permission, and cancelled keys are now
  resolved through the active configuration and placeholder substitution.
- **Recovery evidence — fixed.** Recovery listing now exposes player UUID,
  note ID, operation state, and the recorded evidence payload.
- **Namespaced aliases — fixed.** Alias gating and translation both use the
  normalized label after stripping the namespace.
- **Cancel permission — fixed.** Cancellation derives the required permission
  from the current quote kind before removing it; no-quote cancellation only
  clears a stale request token.
- **Off-hand note interaction wiring — fixed.** The interaction handler accepts
  both event hands, retains the raw-gold shortcut's main-hand-only behavior,
  maps off-hand redemption to slot 40, and revalidates that exact hand and note
  identity. InventoryAdapter plan internals remain covered by the separate
  physical review.

The scoped review leaves one residual P2 finding at `904e67f`, documented
below.

### Residual P2 — Pagination state remains incorrect for withdrawal and full top pages

`MenuService.openWithdraw` at frozen lines 64-78 renders a next arrow when
there are more than 45 withdrawal resources, but constructs `MenuHolder` with
the legacy constructor that sets `hasNext` to `false`. `GoldBagPlugin.onClick`
at lines 238-239 now requires `holder.hasNext()` before opening the next page,
so a configured withdrawal catalog larger than one page cannot be navigated.

Top pagination has the opposite boundary problem: `MenuService.openTop` at
lines 81-93 sets `hasNext` solely from `accounts.size() == 10`. A final page
containing exactly ten accounts therefore renders a next arrow even when no
additional account exists, and the click opens an empty page.

Reproduction: configure more than 45 withdrawal-enabled resources, open
Withdraw, and click the rendered next arrow at slot 53. Expected: page 2
opens; actual: the click is cancelled and the page remains open. The original
unreachable deposit-over-45 disposition is unaffected because withdrawal
catalog pagination is not limited by the player's 36 inventory slots. For the
top case, create exactly ten accounts, open Top Balances, and click its next
arrow; expected: no next arrow or a bounded end state, actual: an empty page.
