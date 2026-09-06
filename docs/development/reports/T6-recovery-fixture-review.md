# T6 recovery fixture review

Scope: `scripts/server-tests/ServerRecoveryFixture.java` only. This review
does not cover the plugin recovery commands, the player harness, or the
server lifecycle scripts.

## Disposition

No P1/P2 defect found. The fixture is safe for the intended stopped-server
test sequence.

`ServerRecoveryFixture` requires the preparation marker and the existing
GoldBag database before opening anything. It opens that database through the
production `SqliteStore`, which acquires the database's sibling `.lock` file
in its constructor and releases the lock, connection, and channel through the
try-with-resources close. A running GoldBag process owns the same lock, so the
fixture fails rather than sharing or racing the live store. The fixture is
therefore correctly scoped to a stopped server.

Both deterministic account IDs are checked before either account is created.
This prevents a second invocation from reseeding either named fixture and
also prevents a partial second fixture when one account already exists. The
deterministic operation IDs are passed through the normal `prepare` path,
which rejects an existing operation ID rather than overwriting it.

The two records are valid `DEPOSIT` operations for 200 cents, transition from
`PREPARED` to `APPLYING`, and block only their synthetic accounts. Their
payloads explicitly state that no physical inventory changed and identify the
expected operator outcome: `GBRecoveryApply` should resolve with `apply` and
reach a 200-cent balance; `GBRecoveryCancel` should resolve with `cancel` and
remain at a zero balance. The payload is printed through the normal recovery
listing, so the evidence and intended outcomes remain visible during the
actual console test.

## Verification

- Reviewed the helper against `SqliteStore` lock acquisition, `prepare`,
  `markApplying`, `resolve`, and schema constraints.
- `javac -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar -d
  C:\Users\mfsta\AppData\Local\Temp\goldbag-recovery-fixture-review
  scripts/server-tests/ServerRecoveryFixture.java` passed with exit code 0.
  No server runtime was started or changed by this review; the controller owns
  the stopped-server runtime check after the player harness completes.

No implementation change is requested from this review.
