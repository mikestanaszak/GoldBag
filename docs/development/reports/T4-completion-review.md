# T4 completion-guard review

Scope: frozen completion-guard fix `5b2efb8`, limited to `GoldBagPlugin` and
`GoldBagPluginCompletionTest`. Pagination and the InventoryAdapter changes are
outside this review.

## Review result

No remaining P1/P2 defect was found in the reviewed completion path.

`redeem`, `executeDeposit`, `executeWithdrawal`, and `issueNote` each capture
the player UUID before creating the asynchronous coordinator operation. The
captured UUID is passed to the coordinator and to `finishOperation`; all four
`whenComplete` callbacks therefore avoid reading `Player.getUniqueId()` on the
storage worker. `finishOperation` uses that immutable UUID both when the
completion runnable runs normally and when `runMain` rejects the callback.
The rejection fallback releases only the matching operation token, so a late
completion cannot clear a newer guard for the same player.

The token release helper in `GoldBagPlugin` compares the expected operation
UUID while holding the guard map lock. `GoldBagPluginCompletionTest` covers the
stale-completion case: a stale token leaves the current token installed, and
the current token is then released. Normal completion releases before either
the failure message or success callback runs, so callback message exceptions
do not retain the guard. `onQuit` and shutdown cleanup remain separate
intentional lifecycle cleanup paths.

## Verification

- Reviewed the exact production and test files with `git show 5b2efb8` and
  confirmed the same production files are unchanged at current `HEAD`.
- The commit's recorded focused command,
  `mvn -B -pl goldbag-plugin -am '-Dtest=GoldBagPluginCompletionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`,
  passed 1 test with zero failures in this review. No full reactor run was
  started for this bounded review.

## Disposition

The prior residual finding “scheduler-failure cleanup still calls
`Player.getUniqueId()` off the server thread” is fixed by `5b2efb8`. The
captured UUID now flows through every physical-operation completion callback
and both guard-release branches. No additional implementation change is
requested from this review.
