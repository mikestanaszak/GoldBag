package io.github.mikestanaszak.goldbag.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class StoreSchema {
    static final int VERSION = 1;

    private StoreSchema() {}

    static void initialise(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        Integer version = null;
        try (var statement = connection.prepareStatement("SELECT value FROM schema_meta WHERE key='schema_version'")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) version = Integer.valueOf(rs.getString(1));
            }
        }
        if (version != null && version.intValue() != VERSION) {
            throw new IllegalStateException("Unsupported GoldBag SQLite schema version " + version + " (expected " + VERSION + ")");
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, name TEXT NOT NULL, balance INTEGER NOT NULL CHECK(balance >= 0), revision INTEGER NOT NULL CHECK(revision >= 0), updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operations (op_id TEXT PRIMARY KEY, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, actor_id TEXT, target_id TEXT, from_id TEXT, to_id TEXT, player_id TEXT, note_id TEXT, amount INTEGER, delta INTEGER, payload TEXT, reason TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL, resolved_at INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operation_entries (op_id TEXT NOT NULL REFERENCES operations(op_id), account_id TEXT NOT NULL REFERENCES accounts(id), before_balance INTEGER NOT NULL, after_balance INTEGER NOT NULL, delta INTEGER NOT NULL, PRIMARY KEY(op_id, account_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS operation_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, op_id TEXT NOT NULL REFERENCES operations(op_id), actor_id TEXT, action TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS notes (note_id TEXT PRIMARY KEY, amount INTEGER NOT NULL CHECK(amount > 0), status TEXT NOT NULL, issuer_id TEXT NOT NULL REFERENCES accounts(id), issue_op TEXT NOT NULL REFERENCES operations(op_id), redeem_op TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_operations (op_id TEXT PRIMARY KEY REFERENCES operations(op_id), player_id TEXT NOT NULL REFERENCES accounts(id), kind TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount > 0), payload TEXT, note_id TEXT, state TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accounts_leaderboard ON accounts(balance DESC, id ASC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON pending_operations(player_id, state)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notes_status ON notes(status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_operation_entries_account ON operation_entries(account_id)");
            statement.executeUpdate("INSERT OR IGNORE INTO schema_meta(key,value) VALUES('schema_version','1')");
        }
    }
}
