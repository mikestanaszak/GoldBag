# T4 offline JSON import report

Status: implemented in the assigned CLI/docs scope. No Git staging or commit performed; the controller owns integration and checkpoint commits.

Implemented:

- `io.github.mikestanaszak.goldbag.cli.OfflineImport` runs without Bukkit API calls and supports `java -cp GoldBag.jar ...OfflineImport <export.json> <new-destination.db> [--validate-only] [--max-balance-cents <cents>]`.
- The export is size-bounded at 256 MiB while streaming, read as UTF-8, and checked for a size/metadata change during the read. Source paths, destination parents, and destination paths reject symlink or redirected path components; source and destination must differ.
- Validation imports through the public `SqliteStore(Path,long).importJson` API into an isolated temporary SQLite database, so schema, ledger, relationship, pending-operation, and note validation remains owned by storage. The temporary database is closed before publication and its sidecars are cleaned up.
- Reports include schema version, account/operation/entry/note/pending/audit row counts, total account balance, active note liability (`ISSUED` plus `RESERVED` notes), and unresolved `PREPARED`/`APPLYING` operation count and amount.
- Dry runs leave the requested destination untouched. Actual restores require an absent destination and publish the validated closed database with a no-replace hard link; there is no unsafe rename fallback. The helper acquires the destination's normal `.lock` and checks the database plus SQLite sidecars, so concurrent store ownership fails and released lock placeholders remain intact. A destination that appears at publication fails rather than being overwritten.
- Invalid input is rejected before publication and temporary files are cleaned up. Source export bytes are never modified. The default maximum balance is `Settings.defaults().maxBalance()` and can be overridden explicitly.
- `docs/operations/backup-and-restore.md` documents maintenance prerequisites, exact commands, lock-file semantics, reports, limits, and unresolved-operation handling. It does not accept an EULA or start a server.

Tests written before implementation and focused verification:

- `mvn -B -pl goldbag-plugin -am "-Dtest=OfflineImportTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — PASS, 5 tests, 0 failures, 0 errors.
- Tests cover valid dry-run with no requested destination, valid restore with source preservation and reopened balance, invalid export with no destination, existing-destination refusal with source/destination preservation, and a destination appearing at the publication boundary.

Integration API notes:

- No POM or public storage changes are required. The CLI consumes only `SqliteStore`, `Settings`, and `Money` public APIs.
- The plugin command integration may call `OfflineImport.run` only from an operator-controlled maintenance path; the standalone helper remains the authoritative path for an offline destination file. A live server must be stopped and have released ownership before restoring.

Remaining validation:

- Controller should run the normal reactor verification and inspect the shaded JAR entrypoint after concurrent storage/plugin edits settle. No live Bukkit server or EULA acceptance was performed.
