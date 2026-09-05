# Recovery of uncertain operations

Inventory changes and a database commit cannot be one atomic transaction in a vanilla server. GoldBag therefore records a durable operation before changing physical items. A pending `APPLYING` operation means the outcome may be uncertain; it is not permission to retry or refund automatically.

## Operator procedure

1. Stop new exchanges for the affected account and preserve the server logs, database, and inventory evidence. Do not delete pending rows or replace the database with an empty file.
2. Run `/goldbag storage status` and `/goldbag recovery list`. Record each operation ID, player UUID, kind, amount, intended item change, and current state.
3. Inspect the player's inventory on the server thread or from an operator-approved backup. Check ordinary item counts, banknote persistent IDs, disconnect/death timing, and any server crash evidence.
4. Decide whether the physical step definitely happened, definitely did not happen, or remains ambiguous. An ambiguous result must stay quarantined until evidence resolves it.
5. Use `/goldbag recovery resolve <operation-id> apply <reason>` only when the evidence proves the physical step happened and the database action should be completed. Use `cancel <reason>` only when the operation is still cancellable or evidence proves no physical change occurred.
6. Keep the exact reason in the audit log. Recheck the account and pending list after resolution before releasing the player.

Resolution changes database accounting; it does not silently restore inventory snapshots or undo unrelated player actions. A late callback or a second server process must not be allowed to race the resolution. If the database is unhealthy, stop mutations and preserve the evidence until storage is available.

## Banknotes

The database is authoritative for note amount and redemption status. A renamed paper item, edited lore, or copied persistent ID cannot create credit. A lost note is not automatically refunded because it may have been traded. Any replacement or recovery requires an audited action that invalidates the original note first.

## Evidence to retain

Retain the GoldBag database and its journal companions, the JSON export if one exists, server log excerpts, operation IDs, plugin/server/Java versions, player UUIDs, and a timestamped description of the physical inventory state. Do not edit the database by hand.
