package io.github.mikestanaszak.goldbag.cli;

import io.github.mikestanaszak.goldbag.storage.SqliteStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineImportTest {
    private static final long MAX = 100_000_000_000L;

    @Test
    void validExportCanBeDryRunWithoutCreatingDestination() throws Exception {
        Path root = Files.createTempDirectory("goldbag-offline-import-");
        try {
            Path source = writeExport(root);
            Path destination = root.resolve("restored.db");
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            int status = OfflineImport.run(new String[]{source.toString(), destination.toString(), "--validate-only"},
                    new PrintStream(output, true, StandardCharsets.UTF_8));

            assertEquals(0, status);
            assertFalse(Files.exists(destination));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("VALID"));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("balance=12.34"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void validExportRestoresToNewDestinationAndPreservesSource() throws Exception {
        Path root = Files.createTempDirectory("goldbag-offline-import-");
        try {
            Path source = writeExport(root);
            byte[] original = Files.readAllBytes(source);
            Path destination = root.resolve("restored.db");

            int status = OfflineImport.run(new String[]{source.toString(), destination.toString()},
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

            assertEquals(0, status);
            assertTrue(Files.isRegularFile(destination));
            assertArrayEquals(original, Files.readAllBytes(source));
            try (SqliteStore restored = new SqliteStore(destination, MAX)) {
                assertEquals(1234L, restored.account(UUID.fromString("11111111-1111-1111-1111-111111111111")).orElseThrow().balance());
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void invalidExportLeavesNoDestination() throws Exception {
        Path root = Files.createTempDirectory("goldbag-offline-import-");
        try {
            Path source = root.resolve("invalid.json");
            Files.writeString(source, "{\"schemaVersion\":999}", StandardCharsets.UTF_8);
            Path destination = root.resolve("restored.db");

            int status = OfflineImport.run(new String[]{source.toString(), destination.toString()},
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

            assertEquals(2, status);
            assertFalse(Files.exists(destination));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void existingDestinationIsRefusedAndSourceIsUnchanged() throws Exception {
        Path root = Files.createTempDirectory("goldbag-offline-import-");
        try {
            Path source = writeExport(root);
            byte[] original = Files.readAllBytes(source);
            Path destination = root.resolve("already-there.db");
            Files.writeString(destination, "keep me", StandardCharsets.UTF_8);

            int status = OfflineImport.run(new String[]{source.toString(), destination.toString()},
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

            assertEquals(2, status);
            assertEquals("keep me", Files.readString(destination, StandardCharsets.UTF_8));
            assertArrayEquals(original, Files.readAllBytes(source));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void destinationAppearingAtPublicationIsNeverReplaced() throws Exception {
        Path root = Files.createTempDirectory("goldbag-offline-import-");
        try {
            Path source = writeExport(root);
            Path destination = root.resolve("restored.db");
            int status = OfflineImport.run(new String[]{source.toString(), destination.toString()},
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                    () -> {
                        try {
                            Files.writeString(destination, "appeared during publication", StandardCharsets.UTF_8);
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });

            assertEquals(2, status);
            assertEquals("appeared during publication", Files.readString(destination, StandardCharsets.UTF_8));
            assertTrue(Files.exists(destination.resolveSibling("restored.db.lock")));
        } finally {
            deleteTree(root);
        }
    }

    private static Path writeExport(Path root) throws Exception {
        Path sourceDatabase = root.resolve("source.db");
        UUID account = UUID.fromString("11111111-1111-1111-1111-111111111111");
        try (SqliteStore source = new SqliteStore(sourceDatabase, MAX)) {
            source.ensureAccount(account, "Alice");
            source.adjust(UUID.fromString("22222222-2222-2222-2222-222222222222"), null, account,
                    1234L, "fixture");
            Files.writeString(root.resolve("export.json"), source.exportJson(), StandardCharsets.UTF_8);
        }
        return root.resolve("export.json");
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }
}
