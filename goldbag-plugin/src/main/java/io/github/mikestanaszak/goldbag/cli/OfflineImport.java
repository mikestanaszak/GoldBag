package io.github.mikestanaszak.goldbag.cli;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.mikestanaszak.goldbag.core.Money;
import io.github.mikestanaszak.goldbag.core.Settings;
import io.github.mikestanaszak.goldbag.storage.SqliteStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Validates a GoldBag JSON export offline and optionally publishes it as a new SQLite database. */
public final class OfflineImport {
    private static final long MAX_EXPORT_BYTES = 256L * 1024L * 1024L;
    private static final String VALIDATE_ONLY = "--validate-only";
    private static final String MAX_BALANCE = "--max-balance-cents";

    private OfflineImport() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    /** Returns 0 for a successful validation/restore and 2 for an operator or input error. */
    public static int run(String[] args, PrintStream output) {
        return run(args, output, () -> { });
    }

    static int run(String[] args, PrintStream output, Runnable beforePublication) {
        if (output == null) throw new IllegalArgumentException("Output is required");
        if (beforePublication == null) throw new IllegalArgumentException("Publication hook is required");
        try {
            Options options = Options.parse(args);
            Path source = absolute(options.source());
            Path destination = absolute(options.destination());
            checkPaths(source, destination);
            String json = readBounded(source);

            Path parent = destination.getParent();
            Path temporary = Files.createTempFile(parent, ".goldbag-import-", ".db");
            boolean published = false;
            try {
                Summary summary;
                try (SqliteStore isolated = new SqliteStore(temporary, options.maxBalance())) {
                    isolated.importJson(json);
                    summary = Summary.from(json);
                }
                output.println(summary.format());
                if (options.validateOnly()) return 0;

                deleteTemporarySidecars(temporary);
                try (DestinationReservation ignored = DestinationReservation.acquire(destination)) {
                    beforePublication.run();
                    ensureDestinationVacant(destination);
                    publishWithoutReplace(temporary, destination);
                    published = true;
                }
                output.println("RESTORED destination=" + destination);
                return 0;
            } finally {
                if (!published) deleteTemporary(temporary);
            }
        } catch (Exception error) {
            output.println("ERROR: " + message(error));
            return 2;
        }
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void checkPaths(Path source, Path destination) throws IOException {
        if (source.equals(destination)) throw new IOException("Source and destination must differ");
        rejectSymlinkAncestors(source);
        rejectSymlinkAncestors(destination.getParent());
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Export source is not a regular file: " + source);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
            throw new IOException("Destination already exists; refusing to overwrite: " + destination);
        }
        if (destination.getParent() == null || !Files.isDirectory(destination.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Destination parent directory does not exist: " + destination.getParent());
        }
    }

    private static void rejectSymlinkAncestors(Path path) throws IOException {
        if (path == null) throw new IOException("Destination must have a parent directory");
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new IOException("Path has no filesystem root: " + path);
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current) || !current.equals(current.toRealPath())) {
                throw new IOException("Symlink path component is not allowed: " + current);
            }
        }
    }

    private static String readBounded(Path source) throws IOException {
        BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) throw new IOException("Export source changed from a regular file");
        if (before.size() > MAX_EXPORT_BYTES) throw new IOException("Export exceeds 256 MiB limit");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(before.size(), 8192L));
        try (InputStream input = Files.newInputStream(source, StandardOpenOption.READ)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_EXPORT_BYTES) throw new IOException("Export exceeds 256 MiB limit");
                bytes.write(buffer, 0, read);
            }
        }
        BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || (before.fileKey() != null && !before.fileKey().equals(after.fileKey()))
                || bytes.size() > MAX_EXPORT_BYTES) {
            throw new IOException("Export changed while it was being read");
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void ensureDestinationVacant(Path destination) throws IOException {
        for (Path path : destinationFiles(destination)) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new IOException("Destination database or SQLite sidecar already exists: " + path);
            }
        }
    }

    private static Path[] destinationFiles(Path destination) {
        return new Path[]{destination, Path.of(destination + "-wal"), Path.of(destination + "-shm")};
    }

    private static void publishWithoutReplace(Path temporary, Path destination) throws IOException {
        boolean linked = false;
        try {
            // POSIX link(2) and the Windows hard-link primitive fail atomically when the target exists.
            // There is deliberately no rename fallback: rename may replace an appearing destination.
            Files.createLink(destination, temporary);
            linked = true;
            Files.delete(temporary);
        } catch (IOException | UnsupportedOperationException error) {
            if (linked) delete(destination);
            throw new IOException("Destination publication requires same-volume hard-link support", error);
        }
    }

    private static void deleteTemporary(Path temporary) {
        delete(temporary);
        deleteTemporarySidecars(temporary);
    }

    private static void deleteTemporarySidecars(Path temporary) {
        delete(Path.of(temporary + "-wal"));
        delete(Path.of(temporary + "-shm"));
        delete(temporary.resolveSibling(temporary.getFileName() + ".lock"));
    }

    private static void delete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private record Options(Path source, Path destination, boolean validateOnly, long maxBalance) {
        static Options parse(String[] args) {
            if (args == null || args.length < 2) throw new IllegalArgumentException(usage());
            Path source = Path.of(args[0]);
            Path destination = Path.of(args[1]);
            boolean validateOnly = false;
            long maxBalance = Settings.defaults().maxBalance();
            for (int i = 2; i < args.length; i++) {
                String argument = args[i];
                if (VALIDATE_ONLY.equals(argument)) {
                    if (validateOnly) throw new IllegalArgumentException("Duplicate " + VALIDATE_ONLY);
                    validateOnly = true;
                } else if (argument.startsWith(MAX_BALANCE + "=")) {
                    if (argument.length() == MAX_BALANCE.length() + 1) throw new IllegalArgumentException("Missing max balance");
                    maxBalance = parseMaxBalance(argument.substring(MAX_BALANCE.length() + 1));
                } else if (MAX_BALANCE.equals(argument) && i + 1 < args.length) {
                    maxBalance = parseMaxBalance(args[++i]);
                } else {
                    throw new IllegalArgumentException(usage());
                }
            }
            return new Options(source, destination, validateOnly, maxBalance);
        }

        private static long parseMaxBalance(String value) {
            try {
                long result = Long.parseLong(value);
                if (result <= 0) throw new IllegalArgumentException("Maximum balance must be positive");
                return result;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Maximum balance must be integer cents", error);
            }
        }

        private static String usage() {
            return "Usage: OfflineImport <export.json> <new-destination.db> [--validate-only] [--max-balance-cents <cents>]";
        }
    }

    private static final class DestinationReservation implements AutoCloseable {
        private final Path marker;
        private final FileChannel channel;
        private final FileLock lock;

        private DestinationReservation(Path marker, FileChannel channel, FileLock lock) {
            this.marker = marker;
            this.channel = channel;
            this.lock = lock;
        }

        static DestinationReservation acquire(Path destination) throws IOException {
            Path marker = destination.resolveSibling(destination.getFileName() + ".lock");
            if (Files.isSymbolicLink(marker)) throw new IOException("Destination lock path is a symlink: " + marker);
            FileChannel channel = FileChannel.open(marker, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock;
                try { lock = channel.tryLock(); } catch (OverlappingFileLockException overlap) { lock = null; }
                if (lock == null) throw new IOException("Destination import is already locked: " + destination);
                return new DestinationReservation(marker, channel, lock);
            } catch (Exception error) {
                try { channel.close(); } catch (IOException ignored) { }
                if (error instanceof IOException io) throw io;
                throw new IOException("Unable to reserve destination", error);
            }
        }

        @Override public void close() {
            try { lock.release(); } catch (IOException ignored) { }
            try { channel.close(); } catch (IOException ignored) { }
        }
    }

    private record Summary(int schemaVersion, int accounts, long balance, int operations, int entries,
                           int notes, long noteLiability, int pending, int unresolvedOperations,
                           long unresolvedAmount, int audit) {
        static Summary from(String json) {
            try {
                ExportDocument document = new Gson().fromJson(json, ExportDocument.class);
                if (document == null || document.accounts == null || document.operations == null
                        || document.entries == null || document.notes == null || document.pending == null
                        || document.audit == null) throw new IllegalArgumentException("Export summary is incomplete");
                long balance = 0;
                for (AccountRow account : document.accounts) balance = Math.addExact(balance, amount(account.balance));
                long liabilities = 0;
                for (NoteRow note : document.notes) {
                    if ("ISSUED".equals(note.status) || "RESERVED".equals(note.status)) {
                        liabilities = Math.addExact(liabilities, amount(note.amount));
                    }
                }
                int unresolved = 0;
                long unresolvedAmount = 0;
                for (PendingRow row : document.pending) {
                    if ("PREPARED".equals(row.state) || "APPLYING".equals(row.state)) {
                        unresolved++;
                        unresolvedAmount = Math.addExact(unresolvedAmount, amount(row.amount));
                    }
                }
                return new Summary(document.schemaVersion, document.accounts.size(), balance,
                        document.operations.size(), document.entries.size(), document.notes.size(), liabilities,
                        document.pending.size(), unresolved, unresolvedAmount, document.audit.size());
            } catch (JsonParseException | ArithmeticException | NullPointerException error) {
                throw new IllegalArgumentException("Unable to summarize validated export", error);
            }
        }

        private static long amount(String value) {
            if (value == null || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid export amount");
            try { return Long.parseLong(value); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid export amount", error); }
        }

        String format() {
            return "VALID schema=" + schemaVersion + " accounts=" + accounts + " operations=" + operations
                    + " entries=" + entries + " notes=" + notes + " pending=" + pending + " audit=" + audit
                    + " balance=" + Money.format(balance) + " note-liability=" + Money.format(noteLiability)
                    + " unresolved-operations=" + unresolvedOperations + " unresolved-amount=" + Money.format(unresolvedAmount);
        }
    }

    private static final class ExportDocument {
        int schemaVersion;
        List<AccountRow> accounts;
        List<Object> operations;
        List<Object> entries;
        List<NoteRow> notes;
        List<PendingRow> pending;
        List<Object> audit;
    }

    private static final class AccountRow { String balance; }
    private static final class NoteRow { String amount; String status; }
    private static final class PendingRow { String amount; String state; }
}
