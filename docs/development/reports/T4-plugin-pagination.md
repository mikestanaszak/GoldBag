# T4 pagination fix handoff

Scope: `MenuService.java`, `MenuHolder.java`, focused menu behavior tests, and
this report. The two residuals are withdrawal holders rendering a next arrow
while `hasNext` is false, and leaderboard pages using `accounts.size() == 10`
without knowing whether another row exists. `GoldBagPlugin.java` is owned by
the physical worker and will not be edited here.

Planned fix: pass the withdrawal renderer's actual next-page state into its
holder, and make top-page rendering use a real lookahead result while
preserving the existing `openTop(Player, List<Account>, int)` API. Add behavior
tests for 45/46 withdrawal resources and leaderboard totals of 10, 20, and a
partial final page. No Git staging or commit is performed by this worker.

## Implementation and verification

Withdrawal holders now receive the same `hasNextResourcePage` result used by
the renderer. Top rendering performs a bounded storage lookahead for
`page + 1` before creating the holder, so exact 10- and 20-account totals do
not expose a false next arrow while partial pages do.

`MenuPaginationTest` covers 45/46 resource entries and top totals of 10, 20,
and 11 accounts. The focused Maven run reached the shared plugin compile but
was blocked by unrelated active `GoldBagPlugin.java` signature mismatches. An
isolated Java 16 compile and JUnit-method run passed both pagination tests.
