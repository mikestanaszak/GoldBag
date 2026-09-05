# Backup and JSON restore

GoldBag stores live balances in SQLite. JSON is an export and restore format, not a second live backend. Keep backups private because exports contain account names, balances, transaction records, notes, and recovery evidence.

## Clean SQLite backup

1. Announce maintenance and stop the Minecraft server cleanly.
2. Confirm the process has exited and that no GoldBag database lock remains.
3. Copy the complete GoldBag data directory, including the SQLite database and any `-wal` or `-shm` companions, to a dated backup location. Preserve file names and permissions.
4. Record the GoldBag version, server build, Java runtime, and backup timestamp.
5. Test that the backup can be read before relying on it.

Never copy only the main `.db` file while a server is active. A clean stop or a database-native consistent snapshot is required. World backups and GoldBag database backups are separate; a world downgrade is not implied by a database restore.

## JSON export

With the server running and storage healthy, an operator can run `/goldbag storage export`. The plugin writes a versioned export under its data directory. Treat the generated file as sensitive and copy it away only after the command reports success. Amounts are encoded as decimal strings so integer cents survive transport.

## Restore into an empty destination

1. Stop the destination server and make a backup of its complete data directory.
2. Confirm that the destination GoldBag store is empty and that the destination server is in maintenance mode. Do not merge two live economies.
3. Transfer the export through a trusted channel and verify its checksum.
4. Use the plugin's offline import entrypoint when provided by the installed build. It must validate the schema version, IDs, row relationships, balances, note liabilities, and pending operations before writing.
5. Review the dry-run report, then perform the import only after validation succeeds.
6. Restart the destination and check balances, notes, transaction IDs, and recovery records before allowing players back in.

An import failure must leave the destination unchanged and the source usable. An older GoldBag build must refuse a newer schema rather than guessing. Keep the source server stopped or isolated until the restored destination has been verified.
