package verification;

import io.github.mikestanaszak.goldbag.cli.OfflineImport;
import io.github.mikestanaszak.goldbag.core.Catalog;
import io.github.mikestanaszak.goldbag.storage.SqliteStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Verifies the packaged GoldBag JAR without a Bukkit class path or a server.
 *
 * <p>The verifier is intentionally source-mode friendly: CI compiles this file
 * against the selected shaded artifact and runs it with that artifact only.
 */
public final class PackagedJarVerification {
    private static final int MAX_SUPPORTED_CLASS_MAJOR = 60;
    private static final String MAIN_CLASS = "io.github.mikestanaszak.goldbag.plugin.GoldBagPlugin";
    private static final long MAX_BALANCE = 100_000_000_000L;

    private PackagedJarVerification() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PackagedJarVerification <shaded-plugin.jar>");
        }
        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        require(Files.isRegularFile(jar), "Plugin JAR does not exist: " + jar);
        verifyJar(jar);
        verifyPackagedBehavior();
        System.out.println("PASS: packaged descriptor, manifest, relocations, native SQLite, bytecode, restart persistence, and offline restore");
    }

    private static void verifyJar(Path jarPath) throws IOException {
        int baseClasses = 0;
        int maxMajor = 0;
        int nativeEntries = 0;
        boolean relocatedGson = false;
        boolean relocatedYaml = false;
        boolean relocatedSlf4j = false;
        boolean bukkitClasses = false;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            require(manifest != null, "Shaded JAR has no manifest");
            Attributes attributes = manifest.getMainAttributes();
            require("true".equalsIgnoreCase(attributes.getValue("Multi-Release")),
                    "Manifest is missing Multi-Release: true");
            require(attributes.getValue("Implementation-Version") != null,
                    "Manifest is missing Implementation-Version");

            JarEntry descriptor = jar.getJarEntry("plugin.yml");
            require(descriptor != null, "plugin.yml is missing");
            String pluginYml = readString(jar.getInputStream(descriptor));
            require(MAIN_CLASS.equals(descriptorValue(pluginYml, "main")),
                    "plugin.yml main class is not " + MAIN_CLASS);
            require("1.17".equals(descriptorValue(pluginYml, "api-version")),
                    "plugin.yml api-version is not 1.17");
            require(jar.getJarEntry(MAIN_CLASS.replace('.', '/') + ".class") != null,
                    "plugin main class is missing from the JAR");

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/versions/")) continue;
                if (name.startsWith("org/bukkit/")) bukkitClasses = true;
                if (name.startsWith("io/github/mikestanaszak/goldbag/libs/gson/")) relocatedGson = true;
                if (name.startsWith("io/github/mikestanaszak/goldbag/libs/yaml/")) relocatedYaml = true;
                if (name.startsWith("io/github/mikestanaszak/goldbag/libs/slf4j/")) relocatedSlf4j = true;
                if (name.startsWith("org/sqlite/native/") && isNative(name)) nativeEntries++;
                if (entry.isDirectory() || !name.endsWith(".class") || name.endsWith("module-info.class")) continue;
                int major = classMajor(jar.getInputStream(entry), name);
                baseClasses++;
                maxMajor = Math.max(maxMajor, major);
                require(major <= MAX_SUPPORTED_CLASS_MAJOR,
                        "Base class requires major " + major + ": " + name);
            }
        }
        require(baseClasses > 0, "JAR contains no base classes");
        require(maxMajor <= MAX_SUPPORTED_CLASS_MAJOR, "JAR contains unsupported base bytecode");
        require(nativeEntries > 0, "JAR contains no SQLite native library");
        require(relocatedGson && relocatedYaml && relocatedSlf4j,
                "JAR is missing one or more relocated runtime dependencies");
        require(!bukkitClasses, "JAR unexpectedly bundles org/bukkit classes");
        System.out.println("  archive: base-classes=" + baseClasses + " max-major=" + maxMajor
                + " sqlite-native-entries=" + nativeEntries);
    }

    private static void verifyPackagedBehavior() throws Exception {
        Path work = Files.createTempDirectory("goldbag-packaged-verification-");
        try {
            Path sourceDb = work.resolve("source.db");
            Path export = work.resolve("export.json");
            Path restoredDb = work.resolve("restored.db");
            UUID player = UUID.randomUUID();
            UUID note = UUID.randomUUID();
            UUID issue = UUID.randomUUID();
            UUID pending = UUID.randomUUID();
            String json;

            try (SqliteStore store = new SqliteStore(sourceDb, MAX_BALANCE)) {
                store.ensureAccount(player, "PackagedVerification");
                store.adjust(UUID.randomUUID(), null, player,
                        Catalog.defaults().depositValue("raw_iron", 25), "packaged verification balance");
                store.prepare(issue, player, SqliteStore.Kind.NOTE_ISSUE, 1_000,
                        "issued note fixture", note);
                store.markApplying(issue);
                store.complete(issue);
                store.prepare(pending, player, SqliteStore.Kind.DEPOSIT, 200,
                        "pending inventory evidence", null);
                json = store.exportJson();
            }
            Files.writeString(export, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            byte[] originalExport = Files.readAllBytes(export);

            try (SqliteStore reopened = new SqliteStore(sourceDb, MAX_BALANCE)) {
                require(reopened.account(player).orElseThrow().balance() == 4_000L,
                        "Restart persistence lost the adjusted balance or note liability");
                require("ISSUED".equals(reopened.note(note).orElseThrow().status()),
                        "Restart persistence lost the issued note");
                require(reopened.pending().size() == 1 && reopened.isBlocked(player),
                        "Restart persistence lost pending operation evidence");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PrintStream messages = new PrintStream(output, true, StandardCharsets.UTF_8);
            int validation = OfflineImport.run(
                    new String[]{export.toString(), restoredDb.toString(), "--validate-only"}, messages);
            require(validation == 0, "Packaged offline validation failed: " + output);
            require(!Files.exists(restoredDb), "Offline validation created a destination database");
            require(!Files.exists(Path.of(restoredDb + "-wal")) && !Files.exists(Path.of(restoredDb + "-shm")),
                    "Offline validation left destination sidecars");

            output.reset();
            int restore = OfflineImport.run(new String[]{export.toString(), restoredDb.toString()}, messages);
            require(restore == 0, "Packaged offline restore failed: " + output);
            try (SqliteStore restored = new SqliteStore(restoredDb, MAX_BALANCE)) {
                require(restored.account(player).orElseThrow().balance() == 4_000L,
                        "Offline restore lost the account balance");
                require("ISSUED".equals(restored.note(note).orElseThrow().status()),
                        "Offline restore lost the issued note");
                List<SqliteStore.Pending> pendingRows = restored.pending();
                require(pendingRows.size() == 1 && restored.isBlocked(player),
                        "Offline restore lost pending recovery evidence");
                require(pendingRows.get(0).id().equals(pending)
                                && "pending inventory evidence".equals(pendingRows.get(0).payload()),
                        "Offline restore changed pending evidence");
            }
            require(Arrays.equals(originalExport, Files.readAllBytes(export)),
                    "Offline restore changed the source export");
            System.out.println("  behavior: restart=ok dry-run=ok restore=ok source-preserved=ok");
        } finally {
            try {
                deleteTree(work);
            } catch (IOException cleanupFailure) {
                System.err.println("WARNING: unable to remove verifier fixture " + work + ": "
                        + cleanupFailure.getMessage());
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean isNative(String name) {
        return name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib");
    }

    private static int classMajor(InputStream input, String name) throws IOException {
        try (InputStream in = input) {
            byte[] header = in.readNBytes(8);
            require(header.length == 8, "Truncated class file: " + name);
            require((header[0] & 0xff) == 0xca && (header[1] & 0xff) == 0xfe
                            && (header[2] & 0xff) == 0xba && (header[3] & 0xff) == 0xbe,
                    "Invalid class header: " + name);
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }

    private static String descriptorValue(String text, String key) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(key + ":")) continue;
            String value = trimmed.substring(key.length() + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private static String readString(InputStream input) throws IOException {
        try (InputStream in = input) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
