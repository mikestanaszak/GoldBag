package io.github.mikestanaszak.goldbag.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Length-prefixed, typed request encoding used for operation idempotency. */
final class StoreFingerprint {
    private StoreFingerprint() {}

    static String of(Object... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            for (Object value : values) write(output, value);
            output.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required", e);
        }
    }

    private static void write(DataOutputStream output, Object value) throws IOException {
        if (value == null) {
            output.writeByte('N');
            return;
        }
        if (value instanceof Long) {
            output.writeByte('L');
            output.writeLong((Long) value);
            return;
        }
        if (value instanceof UUID) {
            output.writeByte('U');
            output.writeLong(((UUID) value).getMostSignificantBits());
            output.writeLong(((UUID) value).getLeastSignificantBits());
            return;
        }
        if (value instanceof Enum<?>) {
            output.writeByte('E');
            writeString(output, value.getClass().getName());
            writeString(output, ((Enum<?>) value).name());
            return;
        }
        if (value instanceof String) {
            output.writeByte('S');
            writeString(output, (String) value);
            return;
        }
        if (value instanceof Boolean) {
            output.writeByte('B');
            output.writeBoolean((Boolean) value);
            return;
        }
        throw new IllegalArgumentException("Unsupported fingerprint value type: " + value.getClass());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
