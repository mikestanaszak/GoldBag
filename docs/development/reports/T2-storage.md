# T2 storage progress report

Status: implementation in progress; facade and focused behavior tests are present.

Implemented in `goldbag-storage/src/main/java/io/github/mikestanaszak/goldbag/storage/`:

- `SqliteStore` exact public API with serialized synchronized calls, SQLite schema initialization/version guard, adjacent process/file lock, durable accounts, operation journal, entries, pending operations, notes, and audit rows.
- Exact integer-cent arithmetic with overflow and configured maximum-balance checks; transfer/adjust/set idempotency uses SHA-256 request fingerprints and replays finalized receipts.
- Pending `DEPOSIT`, `WITHDRAW`, `NOTE_ISSUE`, and `NOTE_REDEEM` reservations. Account blocking, outgoing affordability reservations, note-ID uniqueness, `PREPARED` -> `APPLYING` -> `COMPLETED` state transitions, audited cancellation/resolution, and one-time note redemption are transactionally enforced.
- `StoreJson` versioned export/import with amounts encoded as decimal strings, structural/ID/state/range validation, empty-destination restriction, and transaction rollback on insert failure.

Behavior checks in `src/test/java/.../SqliteStoreTest.java` cover transfer atomicity and replay/fingerprint mismatch, insufficient funds, pending blocking/completion and restart durability, note issue/redemption and duplicate redemption, export/import, malformed import rollback, and second-owner rejection.

Verification checkpoint:

- Command: `mvn -B -f goldbag-storage/pom.xml test`
- Outcome: PASS; 4 tests, 0 failures, 0 errors (Java 16 release compilation; SQLite JDBC 3.53.4.0 from controller POM).

Hardening completed: maximum-balance enforcement now covers all balance-changing paths; import validates operation/pending relationships and journal states; constructor failure releases file ownership; audited cancellation and resolution replay the same request without mutating balances.

Final focused checkpoint:

- Command: `mvn -B -f goldbag-storage/pom.xml test`
- Outcome: PASS; 6 tests, 0 failures, 0 errors (Java 16 release compilation; SQLite JDBC 3.53.4.0 from controller POM).
- Added coverage for APPLYING operator resolution replay and unresolved pending export/import restore with account blocking.
- Command: `mvn -B -f goldbag-storage/pom.xml verify`
- Outcome: PASS; the same 6 tests passed and `goldbag-storage-2.0.0-SNAPSHOT.jar` was packaged.

Remaining integration risk: the Bukkit consumer is not yet implemented, so plugin-side scheduling and inventory evidence must continue to honor the `PREPARED`/`APPLYING`/`pending()` recovery contract. No Git staging or commits performed; controller owns integration.

Known scope gap: import validates IDs, relationships, states, amounts, and row consistency, but does not independently recompute a complete historical balance from every journal entry; SQLite account rows remain authoritative after a validated restore.
