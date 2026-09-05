package io.github.mikestanaszak.goldbag.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

final class StoreSchema {
    static final int VERSION = 2;

    private StoreSchema() {}

    static void initialise(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        boolean hasMeta = tableExists(connection, "schema_meta");
        Integer version = hasMeta ? readVersion(connection) : null;
        if (!hasMeta && hasAnyUserTable(connection)) {
            throw new IllegalStateException("Refusing unversioned nonempty SQLite database; back it up and migrate explicitly");
        }
        if (version != null && version.intValue() != VERSION) {
            throw new IllegalStateException("Unsupported GoldBag SQLite schema version " + version + "; expected released prototype version " + VERSION);
        }
        if (hasMeta && version == null) {
            throw new IllegalStateException("GoldBag schema metadata is missing schema_version");
        }
        boolean fresh = !hasMeta && version == null;
        if (!fresh) validateShape(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, name TEXT NOT NULL, balance INTEGER NOT NULL CHECK(balance >= 0), revision INTEGER NOT NULL CHECK(revision >= 0), updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operations (op_id TEXT PRIMARY KEY, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, actor_id TEXT, target_id TEXT, from_id TEXT, to_id TEXT, player_id TEXT, note_id TEXT, amount INTEGER, delta INTEGER, payload TEXT, reason TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL, resolved_at INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operation_entries (op_id TEXT NOT NULL REFERENCES operations(op_id), account_id TEXT NOT NULL REFERENCES accounts(id), before_balance INTEGER NOT NULL, after_balance INTEGER NOT NULL, delta INTEGER NOT NULL, account_revision INTEGER NOT NULL CHECK(account_revision >= 1), PRIMARY KEY(op_id, account_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operation_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, op_id TEXT NOT NULL REFERENCES operations(op_id), actor_id TEXT, action TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS notes (note_id TEXT PRIMARY KEY, amount INTEGER NOT NULL CHECK(amount > 0), status TEXT NOT NULL CHECK(status IN ('RESERVED','ISSUED','REDEEMED','CANCELLED')), issuer_id TEXT NOT NULL REFERENCES accounts(id), issue_op TEXT NOT NULL REFERENCES operations(op_id), redeem_op TEXT REFERENCES operations(op_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_operations (op_id TEXT PRIMARY KEY REFERENCES operations(op_id), player_id TEXT NOT NULL REFERENCES accounts(id), kind TEXT NOT NULL CHECK(kind IN ('DEPOSIT','WITHDRAW','NOTE_ISSUE','NOTE_REDEEM')), amount INTEGER NOT NULL CHECK(amount > 0), payload TEXT, note_id TEXT REFERENCES notes(note_id), state TEXT NOT NULL CHECK(state IN ('PREPARED','APPLYING','COMPLETED','CANCELLED')), created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accounts_leaderboard ON accounts(balance DESC, id ASC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON pending_operations(player_id, state)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_status ON notes(status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_operation_entries_account ON operation_entries(account_id)");
            statement.executeUpdate("INSERT OR IGNORE INTO schema_meta(key,value) VALUES('schema_version','2')");
        }
        validateShape(connection);
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static Integer readVersion(Connection connection) throws SQLException {
        if (!tableExists(connection, "schema_meta")) return null;
        try (var statement = connection.prepareStatement("SELECT value FROM schema_meta WHERE key='schema_version'")) {
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                try { return Integer.valueOf(rows.getString(1)); }
                catch (NumberFormatException e) { throw new IllegalStateException("Invalid GoldBag schema version metadata", e); }
            }
        }
    }

    private static boolean hasAnyUserTable(Connection connection) throws SQLException { return hasAnyUserTableExcept(connection, null); }
    private static boolean hasAnyUserTableExcept(Connection connection, String excluded) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rows.next()) if (excluded == null || !excluded.equals(rows.getString(1))) return true;
            return false;
        }
    }

    private static void validateShape(Connection connection) throws SQLException {
        requireColumns(connection, "accounts", Set.of("id", "name", "balance", "revision", "updated_at"));
        requireColumns(connection, "operations", Set.of("op_id", "kind", "fingerprint", "actor_id", "target_id", "from_id", "to_id", "player_id", "note_id", "amount", "delta", "payload", "reason", "state", "created_at", "resolved_at"));
        requireColumns(connection, "operation_entries", Set.of("op_id", "account_id", "before_balance", "after_balance", "delta", "account_revision"));
        requireColumns(connection, "notes", Set.of("note_id", "amount", "status", "issuer_id", "issue_op", "redeem_op"));
        requireColumns(connection, "pending_operations", Set.of("op_id", "player_id", "kind", "amount", "payload", "note_id", "state", "created_at"));
    }

    private static void requireColumns(Connection connection, String table, Set<String> required) throws SQLException {
        Set<String> actual = new HashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) actual.add(rows.getString("name"));
        }
        if (!actual.containsAll(required)) throw new IllegalStateException("GoldBag schema table " + table + " has an incompatible shape");
    }
}
