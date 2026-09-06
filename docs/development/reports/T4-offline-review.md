# T4 offline import review

Review target: `4ab5862..7a313b2`, limited to `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/cli/OfflineImport.java`, its focused tests, and `docs/operations/backup-and-restore.md`. The review was checked against the offline JSON import, dry-run, recovery, and backup requirements in the accepted specification and the T4a plan section.

Verdict: pass for the scoped offline import implementation. No concrete findings remain in the reviewed diff.

The helper validates the export through `SqliteStore.importJson` in a temporary database before any destination publication. The temporary store is closed before publication; its database, WAL/SHM companions, and lock placeholder are cleaned on validation failure, dry-run completion, and failed publication. Validation errors therefore do not leave a destination database, and a successful dry run does not create the requested destination or its sidecars.

The destination path is checked before work begins and again immediately before publication while the normal destination `.lock` is held. Existing database files, SQLite sidecars, and symlink/redirected path components are refused. Publication uses a same-directory hard link, whose no-replace behavior makes a destination appearing between the checks fail without replacing the file. There is no rename fallback that could overwrite an appearing destination. The source is opened read-only, bounded to 256 MiB while streaming, and never written by the helper. The focused race test confirms that a destination appearing at the publication hook is preserved byte-for-byte.

The report includes schema, row, balance, note-liability, and unresolved-operation counts. The operation and note states shown in the report are derived from the validated export, while the storage validator remains authoritative for IDs, ledger relationships, balances, note ownership, and pending-operation consistency. The documentation correctly requires maintenance mode, an absent/empty destination, a consistent source export, review of the dry-run output, and post-restore recovery checks. It also documents retained lock placeholders and the hard-link filesystem requirement.

## Evidence

- The worker recorded `mvn -B -pl goldbag-plugin -am "-Dtest=OfflineImportTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passing all five focused tests at checkpoint `7a313b2`.
- The five tests cover valid dry-run destination noncreation, successful restore and source preservation, invalid input cleanup, existing-destination refusal, and the publication-boundary no-replace race.
- A fresh rerun during this review reached plugin test compilation but was blocked by unrelated in-progress `InventoryAdapterTest` references to the not-yet-integrated `InventoryAdapter.Plan` API. No `OfflineImport` compilation error was reported.
- No source, Git, or nested worker files were changed by this review; this report is the only review artifact.
