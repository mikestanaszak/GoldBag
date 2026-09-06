# T6 actual-player harness independent review

Scope: `scripts/server-tests/player-smoke.js`,
`scripts/server-tests/package.json`, `scripts/server-tests/package-lock.json`,
`scripts/server-tests/README.md`, and the evidence in
`T6-player-server-tests.md`, at checkpoint `89be64c`. `ServerRecoveryFixture.java`
and production code were excluded. No server or client was restarted.

## Findings

### P2 — reset mode does not reset Bot B's inventory

`player-smoke.js:152-155` resets both account balances, but it never calls
`clear(b)`. The README says reset mode resets the fixture accounts and
inventories, and the comment says reruns do not depend on prior smoke state.
That is false for Bot B's inventory.

Reproduction: interrupt a full run after B has received or retained a paper
item, then rerun reset mode. At the copied-note section (`player-smoke.js:227-240`),
the command replaces B's main hand with the new copied note, but
`inventoryItems(b).find(item => item.name === 'paper')` can select the stale
paper in another slot. Mineflayer equips that stale item, so B does not redeem
the newly copied identity and the expected G15.00 assertion fails. A clean run
passes because B starts empty. Reset mode should clear both fixture inventories
after the account reset, or otherwise verify and establish an empty inventory
fixture before item assertions.

### P2 — failed connection setup can leak a Mineflayer client

`connect()` starts a bot and rejects on the spawn timeout at line 29, but it
does not destroy or quit that bot. The caller assigns `a`/`b` only after the
promise resolves (`run()` lines 146-147), so the outer `finally` cannot clean
up a bot whose connection timed out, ended before spawn, or was kicked before
assignment. A failed first connection can therefore leave a socket/event loop
alive and a failed second connection can leave the already-created second bot
outside the cleanup references. The helper should close the bot on timeout and
early terminal events, or return the bot reference through a cleanup-safe
connection wrapper before awaiting spawn.

The final `finally` block also quits A before B without an independent
`try/finally`; an exception from `a.quit()` would skip B cleanup. This is the
same cleanup failure path and should be made exception-isolated when the
connection cleanup is hardened.

## Disposition of assertions, modes, and packaging

The recorded evidence substantiates the successful claims: the full run
reports 16 actual-player checks on Paper 1.21.11 build 132, and the no-reset
run checks the persisted G85/G15 balances, copies the persisted note, credits
A to G90 once, rejects B's replay, and disconnects both clients cleanly.
The no-reset branch bypasses both reset commands as intended; it correctly
depends on the documented fixture left by the full run.

Assertions use live chat, inventory state, window-open events, and balance
responses. The main/off-hand note checks verify physical consumption and
balances; the copied-note sequence verifies one-credit behavior across two
clients. Fixed sleeps make the smoke sensitive to unusually slow local
servers, but the successful evidence was obtained on the documented server
and the timeout is configurable through `GOLDBAG_TIMEOUT_MS`.

The pinned `mineflayer` 4.38.0 dependency and `npm ci` setup instructions are
consistent. The script defaults to loopback `127.0.0.1:25575` and exposes host,
port, bot names, version, timeout, and restart mode through environment
variables. The README still invokes a machine-specific `C:\nvm4w\nodejs\node.exe`
path despite declaring Node 22+ as the requirement; using `node` from PATH
would make the documented command portable. This is documentation portability
rather than a server-behavior defect.

## Verification

No live rerun was performed per the review brief. Reviewed the recorded
16-check full-run result and the clean no-reset restart result in
`T6-player-server-tests.md`; inspected the current harness, lockfile, and
README against those claims. No production code was changed.
