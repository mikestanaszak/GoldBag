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


## Round 1 review remediation checkpoint

The review findings are being fixed in this working tree. Regression tests were added before implementation and initially reproduced the findings: signed transfer/debit export rejection, forged balances/revisions, malformed note pending rows, null/literal-null and separator fingerprint collisions, and unversioned database adoption.

Implemented in the current chunk:

- Schema version is now 2 for this unreleased prototype. Each operation entry stores an `account_revision`; fresh databases use the v2 shape. Existing v1 or unversioned/nonempty databases are refused with an explicit diagnostic. Required table columns are checked at startup.
- Import now accepts signed adjustment/transfer fields and recomputes every account ledger from zero in contiguous revision order, requiring final balance and revision equality. Finalized operation entry cardinality and semantics are checked.
- Note issue/redeem pending rows require note IDs and matching operations/status transitions; note references have SQLite foreign keys where feasible. Typed, length-prefixed fingerprints distinguish null, literal strings, and embedded separators.
- Public mutation paths were started on a normal multiline/named-helper layout for reviewability.

Verification checkpoint after these changes: `mvn -B -f goldbag-storage/pom.xml test` passes 11 tests, 0 failures, 0 errors. The suite includes signed round trips, forged balance/revision rejection, malformed note import rollback, fingerprint collision rejection, and unversioned database refusal.

Second remediation checkpoint: all 12 focused tests pass after adding forged note-status and missing-ledger-entry rejection. The public mutation methods and major import validators are now multiline and delegate to named validation helpers. Schema v2 startup now validates existing table shape before enabling a versioned database; only a truly empty file is initialized.

Final round 1 checkpoint: all six review findings are addressed. Signed transfer/debit/set exports round-trip; imports replay every account ledger from zero and require contiguous `account_revision` values, matching final balance/revision, and operation-specific entry cardinality; note rows and pending note operations enforce complete lifecycle relationships; fingerprints use typed length-prefixed encoding; unversioned and incompatible schema shapes are refused; and public mutation/import paths are multiline with named validators.

Final verification:

- `mvn -B -f goldbag-storage/pom.xml test` — PASS, 12 tests, 0 failures, 0 errors.
- `mvn -B -f goldbag-storage/pom.xml verify` — PASS, 12 tests, 0 failures, 0 errors; packaged `goldbag-storage-2.0.0-SNAPSHOT.jar`.
- `javap` contract audit — exact planned `SqliteStore` constructor, nested records/enums, and public methods are present; no Bukkit/core dependency added.

Schema v2 is intentional: the controller authorized rejecting the unreleased v1 prototype rather than guessing historical entry order. No Git staging or commits performed; controller owns integration.

## Round-two review remediation checkpoint

The resumed scoped review found three additional issues: UUID spellings were validated but raw strings were inserted, imported active note reservations were not unique/fully owned, and `SET_BALANCE` operation deltas were not compared with their ledger entries.

Regression tests were added first for uppercase identity fields, cross-account duplicate active redemption rows, note issue ownership, and tampered set deltas. The implementation now canonicalizes every UUID field before validation/insertion, adds a SQLite partial unique index for active note reservations, rejects duplicate active note IDs during import, binds `NOTE_ISSUE` rows to the note's `issueOperation`, and requires `SET_BALANCE` operation delta equality with its sole entry delta. The uppercase restore test also exercises operation cancellation, pending player/note lookup, and canonical account lookup after restore.

Final round-two verification:

- `mvn -B -f goldbag-storage/pom.xml test` — PASS, 16 tests, 0 failures, 0 errors.
- `mvn -B -f goldbag-storage/pom.xml verify` — PASS, 16 tests, 0 failures, 0 errors; storage JAR packaged.
- Public `SqliteStore` API remains unchanged. No Git staging or commits performed; controller owns integration.
