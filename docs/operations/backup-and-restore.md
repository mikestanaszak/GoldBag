# Backup and JSON restore

GoldBag stores live balances in SQLite. JSON is an export and restore format, not a second live backend. Keep backups private because exports contain account names, balances, transaction records, notes, and recovery evidence.

## Clean SQLite backup

1. Announce maintenance and stop the Minecraft server cleanly.
2. Confirm the process has exited and released GoldBag's database ownership. The adjacent `.lock` file may remain as an empty lock-file placeholder after a clean close; do not delete it to force access.
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
4. Run the standalone offline validator from the built plugin JAR. The destination parent directory must already exist, the destination database must be absent, and neither path may contain a symlink. For a build named `GoldBag-2.0.0-SNAPSHOT.jar`, use:

   ```text
   java -cp GoldBag-2.0.0-SNAPSHOT.jar io.github.mikestanaszak.goldbag.cli.OfflineImport export.json data/goldbag-restored.db --validate-only
   ```

   The command validates the schema, IDs, row relationships, balances, note liabilities, and pending operations by importing into an isolated temporary SQLite database. It prints account, journal, entry, note, pending, audit, total-balance, note-liability, and unresolved-operation counts. The default maximum account balance is `100000000000` cents; override it only when the destination is configured with the same limit:

   ```text
   java -cp GoldBag-2.0.0-SNAPSHOT.jar io.github.mikestanaszak.goldbag.cli.OfflineImport export.json data/goldbag-restored.db --validate-only --max-balance-cents 100000000000
   ```

5. Review the dry-run report. If it succeeds, stop the destination server and perform the restore with the same paths, omitting `--validate-only`:

   ```text
   java -cp GoldBag-2.0.0-SNAPSHOT.jar io.github.mikestanaszak.goldbag.cli.OfflineImport export.json data/goldbag-restored.db
   ```

   The helper never overwrites an existing destination. It validates into a temporary database, closes it, acquires the destination's normal `.lock` ownership file, checks that the database and SQLite sidecars are absent, and publishes with a no-replace hard link. Invalid input leaves no destination database; the source export is not modified. Exports are bounded to 256 MiB while streaming. A dry run creates no files at the requested destination, but it does use a temporary SQLite file that is cleaned up. Hard-link publication must be supported by the destination filesystem; the helper refuses an unsafe rename fallback.

6. Restart the destination and check balances, notes, transaction IDs, and recovery records before allowing players back in. Unresolved `PREPARED` or `APPLYING` operations remain quarantined and require the normal recovery procedure; the tool does not guess whether physical inventory changes occurred.

An import failure must leave the destination unchanged and the source usable. An older GoldBag build must refuse a newer schema rather than guessing. Keep the source server stopped or isolated until the restored destination has been verified.
