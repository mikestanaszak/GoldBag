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

## Round-one fix-only re-review (`bf5e6dd`)

Verdict: the six original findings are addressed in the changed paths, but this fix round has three new import-integrity findings that should be fixed before T2 integration.

### Addressed findings

- Signed transfer, debit adjustment, and lower `SET_BALANCE` exports now parse and round-trip. The added signed-operation test covers these cases.
- Import now replays each account's revision ledger, checks contiguous revisions and final balance/revision, and validates operation-specific entry cardinality and note lifecycle relationships. The forged balance/revision, missing-entry, and forged-note tests cover the original monetary and note-integrity gaps.
- `StoreFingerprint` uses typed length-prefixed encoding, and the null/literal-null and separator collision cases now reject UUID reuse.
- Schema v2 refuses unversioned nonempty files and incompatible shapes. The controller-authorized rejection of the unreleased v1 prototype is consistent with this repository's no-lived-dataset state.
- The main mutation and import validators are multiline and named-helper based. Some low-level SQL helper methods remain dense, but the original state-machine readability problem is materially reduced and no new behavior was found there.

### Open findings

#### [P1] Imported UUIDs are validated but not canonicalized before insertion

`StoreJson.java:81-89,120-133` parses UUIDs for validation and uses canonical `UUID.toString()` keys for some maps, but `SqliteStore.java:321-327` inserts the original JSON strings. A valid uppercase UUID therefore passes import but is stored as uppercase text; runtime queries use lowercase `UUID.toString()`. A focused repro imported a zero-balance account with an uppercase ID, then `account(lowercaseUuid)` returned empty while name lookup could still see the row. The account is effectively inaccessible to normal mutations, and the same raw/canonical mismatch can affect operation, entry, pending, and note references.

Normalize every UUID field to `UUID.fromString(value).toString()` before validation/insertion, or reject noncanonical spellings. Add an uppercase-ID export/import test and verify account, operation, pending, and note lookups after restore.

#### [P1] Import allows duplicate active reservations for one note

`StoreJson.java:330-338` requires note existence for note pending rows but does not enforce at most one active (`PREPARED`/`APPLYING`) pending row per note, and it does not require a `NOTE_ISSUE` pending operation to own the note's `issueOperation`. A focused repro duplicated a valid pending `NOTE_REDEEM` operation under a new operation ID; import accepted both rows and `pending().size()` became 2. This bypasses the runtime `notePending` reservation check and can leave one redemption stuck APPLYING after the other redeems the note.

Track active note IDs while validating import, require the issue pending row to match `note.issueOperation`, and add a SQLite partial unique index for active `pending_operations.note_id` values (or equivalent transactional enforcement). Add a cross-account duplicate-redemption import test.

#### [P2] `SET_BALANCE` operation deltas are not checked against their entries

`StoreJson.java:195-203` validates that a set amount is nonnegative, while `StoreJson.java:264-270` checks the entry's final balance but never requires `operation.delta == entry.delta`. A focused repro changed a valid set operation's exported delta from 100 to 999; import accepted it even though the account ledger entry remained correct. The monetary total survives, but the immutable operation journal no longer records the exact delta promised by the storage contract.

Require the set operation delta to equal its entry delta (and retain the existing after-balance check), with a regression test that tampers only this field.

Focused fix-round evidence: the worker reports `mvn -B -f goldbag-storage/pom.xml test` and `verify` passing all 12 tests. The additional JShell repros above were run against the built classes. No source changes, Git operations, or broad suite reruns were performed by this re-review; only this report was written.
