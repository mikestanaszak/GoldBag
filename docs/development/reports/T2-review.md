# T2 storage review

Review target: `.superpowers/sdd/2026-09-05-goldbag-rebuild/review-380c9d8..946b731.diff` (commit `946b731`), against T2 in the approved plan and the storage, persistence, correctness, recovery, and acceptance sections of the approved spec.

Verdict: request changes before integration. The transactional account and pending-operation paths have the intended serialized state machine, outgoing reservation, note-ID exclusion, replay checks, and rollback structure. The import validator currently permits monetary and journal inconsistencies, and the request fingerprint encoding does not guarantee that a reused operation ID with a different request is rejected.

## Findings

### [P1] Valid transfers and negative adjustments cannot be restored from JSON

`goldbag-storage/src/main/java/io/github/mikestanaszak/goldbag/storage/StoreJson.java:88-95` validates both `operation.amount` and `operation.delta` with the unsigned `amount(...)` parser. `SqliteStore.transfer` stores a negative delta for the sender (`SqliteStore.java:117`), and a negative `adjust` stores a negative operation amount and delta (`SqliteStore.java:103`). Therefore an ordinary export containing a transfer, debit adjustment, or balance decrease is rejected as `Invalid operation delta` or `Invalid operation amount`; the acceptance requirement to preserve transaction IDs and balances across export/import fails. The focused JShell repro created two accounts, transferred 2000 cents, exported, and observed `IllegalArgumentException / Invalid operation delta` on import.

Use the signed parser for delta and define a consistent signed/unsigned rule for the operation amount field (or validate it by operation kind). Add round-trip tests for a transfer and a negative adjustment/set operation.

### [P1] Import accepts forged account balances without recomputing the ledger

`StoreJson.java:79-101` checks individual IDs and `after - before == delta`, but never checks that entries cover the required finalized operations, that each entry's `before` continues from the account's previous balance, or that the final entry balance equals the imported account balance. `SqliteStore.java:174-180` then inserts the account rows as authoritative. A minimal export containing one account at 999 cents and no operations or entries is accepted and creates a 999-cent balance; the focused JShell repro confirmed this. The same gap permits a forged note liability or an operation with missing/irrelevant entries.

This is required by the spec's import checks for row counts, balances, note liabilities, and unresolved operations, and is the known gap in `T2-storage.md`. Before inserting, validate operation-kind/state/entry cardinality and semantics, replay each account's entries in deterministic operation order from zero, and require the resulting balance and revision to match the account row. Validate issued/reserved/redeemed note relationships and liabilities against the corresponding journal operations. Reject any finalized monetary operation with missing or extra entries.

### [P1] Import admits semantically invalid note pending rows that fail during recovery

`StoreJson.java:103-119` validates that a referenced note exists only when `pending.note` is non-null; it does not require a note ID for `NOTE_ISSUE` or `NOTE_REDEEM`, nor does it verify that the issue/redeem operation is the matching `PREPARE` kind and state. It also accepts `REDEEMED` notes without a redemption operation and `ISSUED`/`RESERVED` notes whose issue operation does not establish that state. A crafted import with a `NOTE_REDEEM` pending row and a null note is accepted, `markApplying` succeeds, and `complete` then throws a `NullPointerException` at `SqliteStore.java:170` after the recovery state has become APPLYING. The account remains blocked by the unusable pending row.

Require non-null note IDs for both note kinds, require the corresponding note row and operation kind/state, and enforce the full status transition (`RESERVED`/`ISSUED`/`REDEEMED`/`CANCELLED`) before insertion. Add malformed note and pending import cases that must fail during validation and leave the destination empty. Add database foreign keys/checks for `notes.redeem_op` and `pending_operations.note_id` where the schema can express them.

### [P2] Operation fingerprints collide for distinct requests

`SqliteStore.java:222` serializes values by concatenating `toString()` values with a separator and represents null as the literal text `<null>`. A `DEPOSIT` prepared with `payload == null` and the same operation ID, player, amount, and note ID as a second request with `payload.equals("<null>")` produces the same SHA-256 input. The second call returns the original pending record, silently treating a different request as an idempotent replay; embedded separator characters create similar field-boundary collisions. The focused JShell repro printed `first payload=null, second payload=null` instead of rejecting the second request, violating the T2 contract that UUID reuse with a different request is an error.

Encode each field with an explicit type and length (or use a binary canonical encoder) before hashing. Preserve the exact null/string distinction and add collision regression tests, including a separator in payload and the literal `<null>`.

### [P2] An existing unversioned database is silently adopted as schema version 1

`StoreSchema.java:16-40` creates `schema_meta` before checking the version and inserts `schema_version=1` whenever the key is absent. It does not distinguish a brand-new empty SQLite file from an existing database that already contains GoldBag tables but no version metadata. The focused repro created a pre-existing `accounts` table and row without `schema_meta`; `SqliteStore` accepted it and reported the row as live storage. That bypasses the schema guard for an unknown/legacy layout and can mutate data whose constraints and table semantics were never validated.

Only stamp version 1 for an empty database. If application tables or rows exist without a version record, refuse startup with an actionable schema diagnostic; for a versioned database, validate the required table/column/constraint shape before enabling mutations.

### [P2] Transactional state-machine code is compressed beyond safe reviewability

The public mutation paths in `SqliteStore.java:96-146` and the import validator in `StoreJson.java:79-119` put multiple validation, SQL, state-transition, and error branches on single long lines and nested lambdas. This makes it difficult to audit the exact rollback boundary and state invariants, and it contributed to the missed import and fingerprint cases above. Reformat the transaction bodies and extract named validators/transition helpers before T4 integrates against this API; keep the behavior unchanged while adding the regression tests above.

## Verified behavior and evidence

- The worker-reported `mvn -B -f goldbag-storage/pom.xml verify` passed 6 tests with 0 failures and packaged the storage JAR. No full reactor suite was rerun for this bounded review.
- Focused JShell checks against the built classes reproduced: transfer export/import rejection on a negative operation delta; forged nonzero account balance accepted with no ledger; null-note `NOTE_REDEEM` pending accepted and later failing in `complete`; payload `null` and payload `"<null>"` being treated as the same fingerprint; and an unversioned pre-existing `accounts` table being adopted.
- Pending account blocking, outgoing affordability reservation, note-ID exclusivity, PREPARED/APPLYING transitions, audited resolution replay, and SQL rollback structure were otherwise consistent with the T2 contract in the reviewed code.
- No source changes or Git operations were made by this review. The only file written is this report. Fixes should add focused storage tests for every finding before integration.
