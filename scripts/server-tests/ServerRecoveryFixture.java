import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Seeds two synthetic recovery cases in a stopped, prepared local test server. */
public final class ServerRecoveryFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ServerRecoveryFixture <test-server-directory>");
        Path server = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(server.resolve(".goldbag-prepared.json"))) {
            throw new IllegalArgumentException("Expected an isolated server prepared by the GoldBag helper");
        }
        Path database = server.resolve("plugins/GoldBag/goldbag.db");
        if (!Files.isRegularFile(database)) throw new IllegalArgumentException("Test database does not exist");
        String[] names = {"GBRecoveryApply", "GBRecoveryCancel"};
        try (SqliteStore store = new SqliteStore(database, 100_000_000_000L)) {
            for (String name : names) {
                if (store.account(id(name)).isPresent()) {
                    throw new IllegalStateException("Fixture already exists; inspect its state instead of reseeding: " + name);
                }
            }
            for (String name : names) {
                UUID account = id(name);
                UUID operation = id(name + "-operation");
                store.ensureAccount(account, name);
                store.prepare(operation, account, SqliteStore.Kind.DEPOSIT, 200,
                        "SYNTHETIC local test fixture; no physical inventory changed; expected "
                                + (name.endsWith("Apply") ? "apply=200 cents" : "cancel=0 cents"), null);
                store.markApplying(operation);
                System.out.println(name + " account=" + account + " operation=" + operation);
            }
        }
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("GoldBag-recovery-fixture:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
