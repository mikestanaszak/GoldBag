# T4 controller integration notes

These are current-source observations sent to the original Luna plugin worker during resumed implementation. They supplement the early boundary review against `65dd1b7`; they are not a completed review or a release certificate.

## Required dispositions before T4 completion

- Quote creation currently advances the shared catalog revision. Alice's quote is invalidated when Bob requests one. Only catalog activation/reload should change that revision; add a two-player regression.
- `MenuService.openTop` must retain the requested page in its holder. Menu clicks must resolve the resource actually displayed, with catalog revision checks, rather than indexing a newly recomputed eligibility list.
- Move JSON export file writing into the background task, not the main-thread completion callback.
- Replace live import with the offline CLI. A separate Luna worker owns new `cli/**` source/test packages and the backup/restore document. Plugin command may explain offline usage.
- Every exchange preview must identify exact intended items, currency change, and resulting balance. Deposit-all needs the per-resource list, not only a total item count.
- `/goldbag rates` with no material should show the catalog; storage status should identify SQLite health, schema, and pending operations.
- `/gb` is a canonical alias and stays available when optional legacy aliases are disabled. Legacy names, including namespaced forms, must honor the setting.
- Only standalone legacy `/withdraw <amount>` creates a banknote. Canonical `/goldbag withdraw` requires resource and count; translate the legacy label to the note command before canonical parsing.
- Persist before/after slot evidence for physical operations, rather than only a material/count or `command` string. Recovery output must expose the player and evidence required to resolve the operation.
- Actual coordinator execution tests must cover delayed callbacks, state changes between prepare/apply, failure quarantine, copied-note redemption, and shutdown/timeout behavior. State-machine-only tests do not demonstrate integration safety.
- Explicitly disable on synchronous startup failure. Inspection of the target Spigot 1.17 `JavaPluginLoader.enablePlugin` bytecode (`javap -c`) confirms it catches/logs exceptions from `setEnabled(true)` without resetting the enabled flag. Merely rethrowing from `onEnable` is insufficient; this corrects the early boundary review's alternative suggestion.

The primary boundary report additionally tracks startup failure/health handling, guard release and logout races, game-mode and confirmation permissions, pristine metadata acceptance, strict message reloads, orderly executor shutdown, bounded deadlines, and stable note-slot identity. Workers should record fixes and remaining limitations in their own reports. Final independent review must use a fresh checkpoint.
