package io.github.mikestanaszak.goldbag.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

final class StoreJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private StoreJson() {}

    static String exportData(Connection c) throws SQLException {
        ExportDocument d = new ExportDocument();
        d.schemaVersion = StoreSchema.VERSION;
        d.accounts = new ArrayList<>();
        d.operations = new ArrayList<>();
        d.entries = new ArrayList<>();
        d.notes = new ArrayList<>();
        d.pending = new ArrayList<>();
        d.audit = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT id,name,balance,revision FROM accounts ORDER BY id")) {
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) { AccountData a = new AccountData(); a.id=r.getString(1); a.name=r.getString(2); a.balance=Long.toString(r.getLong(3)); a.revision=Long.toString(r.getLong(4)); d.accounts.add(a); }
            }
        }
        try (PreparedStatement s = c.prepareStatement("SELECT op_id,kind,fingerprint,actor_id,target_id,from_id,to_id,player_id,note_id,amount,delta,payload,reason,state,created_at,resolved_at FROM operations ORDER BY op_id")) {
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) { OperationData o = new OperationData(); o.id=r.getString(1); o.kind=r.getString(2); o.fingerprint=r.getString(3); o.actor=r.getString(4); o.target=r.getString(5); o.from=r.getString(6); o.to=r.getString(7); o.player=r.getString(8); o.note=r.getString(9); o.amount=nullableAmount(r,10); o.delta=nullableAmount(r,11); o.payload=r.getString(12); o.reason=r.getString(13); o.state=r.getString(14); o.createdAt=r.getLong(15); o.resolvedAt=r.getObject(16)==null?null:r.getLong(16); d.operations.add(o); }
            }
        }
        try (PreparedStatement s = c.prepareStatement("SELECT op_id,account_id,before_balance,after_balance,delta,account_revision FROM operation_entries ORDER BY account_id,account_revision,op_id")) {
            try (ResultSet r = s.executeQuery()) { while (r.next()) { EntryData e=new EntryData(); e.operation=r.getString(1); e.account=r.getString(2); e.before=Long.toString(r.getLong(3)); e.after=Long.toString(r.getLong(4)); e.delta=Long.toString(r.getLong(5)); e.accountRevision=Long.toString(r.getLong(6)); d.entries.add(e); } }
        }
        try (PreparedStatement s = c.prepareStatement("SELECT note_id,amount,status,issuer_id,issue_op,redeem_op FROM notes ORDER BY note_id")) {
            try (ResultSet r = s.executeQuery()) { while (r.next()) { NoteData n=new NoteData(); n.id=r.getString(1); n.amount=Long.toString(r.getLong(2)); n.status=r.getString(3); n.issuer=r.getString(4); n.issueOperation=r.getString(5); n.redeemOperation=r.getString(6); d.notes.add(n); } }
        }
        try (PreparedStatement s = c.prepareStatement("SELECT op_id,player_id,kind,amount,payload,note_id,state,created_at FROM pending_operations ORDER BY op_id")) {
            try (ResultSet r = s.executeQuery()) { while (r.next()) { PendingData p=new PendingData(); p.operation=r.getString(1); p.player=r.getString(2); p.kind=r.getString(3); p.amount=Long.toString(r.getLong(4)); p.payload=r.getString(5); p.note=r.getString(6); p.state=r.getString(7); p.createdAt=r.getLong(8); d.pending.add(p); } }
        }
        try (PreparedStatement s = c.prepareStatement("SELECT op_id,actor_id,action,reason,created_at FROM operation_audit ORDER BY id")) {
            try (ResultSet r = s.executeQuery()) { while (r.next()) { AuditData a=new AuditData(); a.operation=r.getString(1); a.actor=r.getString(2); a.action=r.getString(3); a.reason=r.getString(4); a.createdAt=r.getLong(5); d.audit.add(a); } }
        }
        return GSON.toJson(d);
    }

    private static String nullableAmount(ResultSet r, int column) throws SQLException {
        Object value = r.getObject(column);
        return value == null ? null : Long.toString(((Number)value).longValue());
    }

    static ImportData parseAndValidate(String json, long maxBalance) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Import JSON is empty");
        final ExportDocument d;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) throw new IllegalArgumentException("Import root must be an object");
            JsonObject object = root.getAsJsonObject();
            requireFields(object, "schemaVersion", "accounts", "operations", "entries", "notes", "pending", "audit");
            d = GSON.fromJson(object, ExportDocument.class);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            throw new IllegalArgumentException("Malformed GoldBag export", e);
        }
        if (d == null || d.schemaVersion != StoreSchema.VERSION || d.accounts == null || d.operations == null || d.entries == null || d.notes == null || d.pending == null || d.audit == null) throw new IllegalArgumentException("Unsupported or incomplete GoldBag export");
        canonicalizeIds(d);
        Set<String> accounts = new HashSet<>();
        for (AccountData a : d.accounts) {
            UUID id = uuid(a.id, "account id");
            if (!accounts.add(id.toString()) || a.name == null || a.name.isBlank()) {
                throw new IllegalArgumentException("Invalid or duplicate account");
            }
            long balance = amount(a.balance, "account balance");
            long revision = amount(a.revision, "account revision");
            if (balance > maxBalance) throw new IllegalArgumentException("Invalid account values");
        }
        Set<String> operations = new HashSet<>();
        Map<String, OperationData> operationById = new HashMap<>();
        for (OperationData o : d.operations) {
            UUID id = uuid(o.id, "operation id");
            if (!operations.add(id.toString()) || o.kind == null || o.fingerprint == null || o.state == null) {
                throw new IllegalArgumentException("Invalid or duplicate operation");
            }
            operationById.put(id.toString(), o);
            if (!Set.of("ADJUST", "SET_BALANCE", "TRANSFER", "PREPARE").contains(o.kind)
                    || !Set.of("FINAL", "CANCELLED", "PREPARED", "APPLYING", "COMPLETED").contains(o.state)) {
                throw new IllegalArgumentException("Invalid operation kind or state");
            }
            if (!"PREPARE".equals(o.kind) && !"FINAL".equals(o.state)) {
                throw new IllegalArgumentException("Direct operation must be finalized");
            }
            checkOptionalAccount(o.actor, accounts);
            checkOptionalAccount(o.target, accounts);
            checkOptionalAccount(o.from, accounts);
            checkOptionalAccount(o.to, accounts);
            checkOptionalAccount(o.player, accounts);
            if (o.note != null) uuid(o.note, "operation note id");
            validateOperationValues(o);
            String expected = expectedFingerprint(o);
            if (expected != null && !expected.equals(o.fingerprint)) {
                throw new IllegalArgumentException("Operation fingerprint does not match its request");
            }
        }
        Set<String> entries = new HashSet<>();
        Map<String, List<EntryData>> entriesByAccount = new HashMap<>();
        Map<String, List<EntryData>> entriesByOperation = new HashMap<>();
        for (EntryData e : d.entries) {
            UUID op = uuid(e.operation, "entry operation");
            UUID account = uuid(e.account, "entry account");
            String key = op + "/" + account;
            if (!operations.contains(op.toString()) || !accounts.contains(account.toString()) || !entries.add(key)) {
                throw new IllegalArgumentException("Invalid or duplicate operation entry");
            }
            long before = amount(e.before, "entry before");
            long after = amount(e.after, "entry after");
            long delta = signed(e.delta, "entry delta");
            long revision = amount(e.accountRevision, "entry revision");
            if (after - before != delta || revision < 1) throw new IllegalArgumentException("Inconsistent operation entry");
            entriesByAccount.computeIfAbsent(account.toString(), ignored -> new ArrayList<>()).add(e);
            entriesByOperation.computeIfAbsent(op.toString(), ignored -> new ArrayList<>()).add(e);
        }
        Set<String> notes = new HashSet<>();
        Map<String, NoteData> noteById = new HashMap<>();
        for (NoteData n : d.notes) {
            UUID id = uuid(n.id, "note id");
            if (!notes.add(id.toString()) || amount(n.amount, "note amount") <= 0 || n.status == null
                    || !Set.of("RESERVED", "ISSUED", "REDEEMED", "CANCELLED").contains(n.status)) {
                throw new IllegalArgumentException("Invalid or duplicate note");
            }
            checkAccount(n.issuer, accounts);
            UUID issue = uuid(n.issueOperation, "note issue operation");
            if (!operations.contains(issue.toString())) throw new IllegalArgumentException("Note references unknown issue operation");
            if (n.redeemOperation != null && !operations.contains(uuid(n.redeemOperation, "note redemption operation").toString())) {
                throw new IllegalArgumentException("Note references unknown redemption operation");
            }
            noteById.put(id.toString(), n);
        }
        Set<String> pendingOps = new HashSet<>();
        Set<String> activeNoteIds = new HashSet<>();
        Map<String, PendingData> pendingByOp = new HashMap<>();
        for (PendingData p : d.pending) {
            UUID op = uuid(p.operation, "pending operation");
            if (!operations.contains(op.toString()) || !pendingOps.add(op.toString())
                    || !Set.of("PREPARED", "APPLYING", "COMPLETED", "CANCELLED").contains(p.state)
                    || p.kind == null || !Set.of("DEPOSIT", "WITHDRAW", "NOTE_ISSUE", "NOTE_REDEEM").contains(p.kind)) {
                throw new IllegalArgumentException("Invalid pending operation");
            }
            checkAccount(p.player, accounts);
            if (amount(p.amount, "pending amount") <= 0) throw new IllegalArgumentException("Invalid pending amount");
            if (p.note != null) uuid(p.note, "pending note id");
            boolean noteKind = "NOTE_ISSUE".equals(p.kind) || "NOTE_REDEEM".equals(p.kind);
            if (noteKind != (p.note != null)) throw new IllegalArgumentException("Only note operations may carry a note id");
            if (noteKind && ("PREPARED".equals(p.state) || "APPLYING".equals(p.state)) && !activeNoteIds.add(p.note)) {
                throw new IllegalArgumentException("A note cannot have multiple active pending operations");
            }
            PendingData previous = pendingByOp.put(op.toString(), p);
            if (previous != null) throw new IllegalArgumentException("Duplicate pending operation");
            OperationData operation = operationById.get(op.toString());
            if (operation == null || !"PREPARE".equals(operation.kind) || !p.player.equals(operation.player)
                    || !p.amount.equals(operation.amount) || !same(p.note, operation.note)
                    || !pendingStateMatches(p.state, operation.state)) {
                throw new IllegalArgumentException("Pending operation does not match its journal row");
            }
            if ("NOTE_ISSUE".equals(p.kind)) {
                NoteData note = noteById.get(p.note);
                if (note == null || !p.operation.equals(note.issueOperation)) throw new IllegalArgumentException("Note issue does not own its note");
            }
            if ("NOTE_REDEEM".equals(p.kind) && ("PREPARED".equals(p.state) || "APPLYING".equals(p.state))) {
                validateActiveRedemption(p, noteById);
            }
        }
        for (OperationData operation : d.operations) if ("PREPARE".equals(operation.kind) && !pendingByOp.containsKey(operation.id)) throw new IllegalArgumentException("Journal operation is missing its pending row");
        validatePendingFingerprints(operationById, pendingByOp);
        validateLedger(d.accounts, operationById, entriesByAccount, entriesByOperation, pendingByOp, maxBalance);
        validateNotes(d.notes, noteById, operationById, pendingByOp);
        for (AuditData a : d.audit) { if (!operations.contains(uuid(a.operation,"audit operation").toString()) || a.action==null || a.reason==null) throw new IllegalArgumentException("Invalid audit record"); if (a.actor != null) uuid(a.actor,"audit actor"); }
        return new ImportData(d);
    }

    private static void canonicalizeIds(ExportDocument document) {
        for (AccountData account : document.accounts) account.id = canonical(account.id, "account id");
        for (OperationData operation : document.operations) {
            operation.id = canonical(operation.id, "operation id");
            operation.actor = optionalCanonical(operation.actor, "actor");
            operation.target = optionalCanonical(operation.target, "target account");
            operation.from = optionalCanonical(operation.from, "sender");
            operation.to = optionalCanonical(operation.to, "recipient");
            operation.player = optionalCanonical(operation.player, "player");
            operation.note = optionalCanonical(operation.note, "note id");
        }
        for (EntryData entry : document.entries) {
            entry.operation = canonical(entry.operation, "entry operation");
            entry.account = canonical(entry.account, "entry account");
        }
        for (NoteData note : document.notes) {
            note.id = canonical(note.id, "note id");
            note.issuer = canonical(note.issuer, "note issuer");
            note.issueOperation = canonical(note.issueOperation, "note issue operation");
            note.redeemOperation = optionalCanonical(note.redeemOperation, "note redemption operation");
        }
        for (PendingData pending : document.pending) {
            pending.operation = canonical(pending.operation, "pending operation");
            pending.player = canonical(pending.player, "pending player");
            pending.note = optionalCanonical(pending.note, "pending note id");
        }
        for (AuditData audit : document.audit) {
            audit.operation = canonical(audit.operation, "audit operation");
            audit.actor = optionalCanonical(audit.actor, "audit actor");
        }
    }

    private static String canonical(String value, String label) { return uuid(value, label).toString(); }
    private static String optionalCanonical(String value, String label) { return value == null ? null : canonical(value, label); }

    private static void requireFields(JsonObject object, String... fields) { for (String field : fields) if (!object.has(field)) throw new IllegalArgumentException("Missing export field: " + field); }
    private static boolean same(String first,String second) { return first==null?second==null:first.equals(second); }
    private static boolean pendingStateMatches(String pending,String operation) { return pending.equals(operation) || "COMPLETED".equals(pending) && "FINAL".equals(operation); }
    private static void checkOptionalAccount(String id, Set<String> accounts) { if (id != null && !accounts.contains(uuid(id,"account reference").toString())) throw new IllegalArgumentException("Unknown account reference"); }
    private static void checkAccount(String id, Set<String> accounts) { if (id == null || !accounts.contains(uuid(id,"account reference").toString())) throw new IllegalArgumentException("Unknown account reference"); }

    private static void validateOperationValues(OperationData operation) {
        if (operation.amount == null) throw new IllegalArgumentException("Operation amount is required");
        long amount = signed(operation.amount, "operation amount");
        if ("PREPARE".equals(operation.kind)) {
            if (amount <= 0) throw new IllegalArgumentException("Invalid pending operation amount");
            return;
        }
        if (operation.delta == null) throw new IllegalArgumentException("Operation delta is required");
        long delta = signed(operation.delta, "operation delta");
        if ("ADJUST".equals(operation.kind)) {
            if (amount == 0 || delta != amount) throw new IllegalArgumentException("Invalid adjustment operation");
        } else if ("SET_BALANCE".equals(operation.kind)) {
            if (amount < 0) throw new IllegalArgumentException("Invalid set balance amount");
        } else if ("TRANSFER".equals(operation.kind)) {
            if (amount <= 0 || delta != -amount) throw new IllegalArgumentException("Invalid transfer operation");
        }
    }

    private static String expectedFingerprint(OperationData operation) {
        if ("ADJUST".equals(operation.kind)) {
            return StoreFingerprint.of("ADJUST", optionalUuid(operation.actor), uuid(operation.target, "target account"),
                    signed(operation.delta, "adjustment"), operation.reason);
        }
        if ("SET_BALANCE".equals(operation.kind)) {
            return StoreFingerprint.of("SET_BALANCE", optionalUuid(operation.actor), uuid(operation.target, "target account"),
                    signed(operation.amount, "balance"), operation.reason);
        }
        if ("TRANSFER".equals(operation.kind)) {
            return StoreFingerprint.of("TRANSFER", uuid(operation.from, "sender"), uuid(operation.to, "recipient"),
                    signed(operation.amount, "transfer amount"), null);
        }
        if ("PREPARE".equals(operation.kind)) return null;
        throw new IllegalArgumentException("Unknown operation kind");
    }

    private static UUID optionalUuid(String value) { return value == null ? null : uuid(value,"actor"); }

    private static void validatePendingFingerprints(Map<String,OperationData> operations, Map<String,PendingData> pending) {
        for (Map.Entry<String,PendingData> row : pending.entrySet()) {
            OperationData operation = operations.get(row.getKey());
            PendingData pendingRow = row.getValue();
            String expected;
            if ("NOTE_REDEEM".equals(pendingRow.kind)) {
                expected = StoreFingerprint.of("PREPARE_REDEEM", uuid(pendingRow.player, "player"),
                        uuid(pendingRow.note, "note"), pendingRow.payload);
            } else {
                expected = StoreFingerprint.of("PREPARE", uuid(pendingRow.player, "player"),
                        SqliteStore.Kind.valueOf(pendingRow.kind), amount(pendingRow.amount, "pending amount"),
                        pendingRow.payload, pendingRow.note == null ? null : uuid(pendingRow.note, "note"));
            }
            if (!expected.equals(operation.fingerprint)) throw new IllegalArgumentException("Pending operation fingerprint does not match its request");
        }
    }

    private static void validateLedger(List<AccountData> accounts, Map<String,OperationData> operations, Map<String,List<EntryData>> byAccount, Map<String,List<EntryData>> byOperation, Map<String,PendingData> pending, long maxBalance) {
        Set<String> seenEntries = new HashSet<>();
        for (AccountData account : accounts) {
            long expectedBalance = 0;
            long expectedRevision = 0;
            List<EntryData> entries = new ArrayList<>(byAccount.getOrDefault(account.id, List.of()));
            entries.sort(Comparator.comparingLong(e -> amount(e.accountRevision, "entry revision")));
            for (EntryData entry : entries) {
                long revision = amount(entry.accountRevision, "entry revision");
                if (revision != expectedRevision + 1) throw new IllegalArgumentException("Account ledger revisions are not contiguous");
                long before = amount(entry.before, "entry before");
                long after = amount(entry.after, "entry after");
                long delta = signed(entry.delta, "entry delta");
                if (before != expectedBalance || after - before != delta || after > maxBalance) throw new IllegalArgumentException("Account ledger replay does not match");
                expectedBalance = after;
                expectedRevision = revision;
                seenEntries.add(entry.operation + "/" + entry.account);
            }
            if (expectedBalance != amount(account.balance, "account balance") || expectedRevision != amount(account.revision, "account revision")) throw new IllegalArgumentException("Account balance or revision does not match its ledger");
        }
        for (OperationData operation : operations.values()) {
            List<EntryData> entries = byOperation.getOrDefault(operation.id, List.of());
            if ("ADJUST".equals(operation.kind) || "SET_BALANCE".equals(operation.kind)) {
                if (!"FINAL".equals(operation.state) || entries.size() != 1 || operation.target == null || !operation.target.equals(entries.get(0).account)) throw new IllegalArgumentException("Balance operation entry cardinality is invalid");
                EntryData entry = entries.get(0);
                long amount = signed(operation.amount, "operation amount");
                long delta = signed(entry.delta, "entry delta");
                if ("ADJUST".equals(operation.kind) && delta != amount) throw new IllegalArgumentException("Adjustment entry is invalid");
                if ("SET_BALANCE".equals(operation.kind)
                        && (amount != amount(entry.after, "entry after") || signed(operation.delta, "operation delta") != delta)) {
                    throw new IllegalArgumentException("Set balance entry is invalid");
                }
            } else if ("TRANSFER".equals(operation.kind)) {
                if (!"FINAL".equals(operation.state) || entries.size() != 2 || operation.from == null || operation.to == null) throw new IllegalArgumentException("Transfer entry cardinality is invalid");
                long amount = signed(operation.amount, "operation amount");
                boolean from = false;
                boolean to = false;
                for (EntryData entry : entries) {
                    long delta = signed(entry.delta, "entry delta");
                    if (operation.from.equals(entry.account) && delta == -amount) from = true;
                    if (operation.to.equals(entry.account) && delta == amount) to = true;
                }
                if (!from || !to) throw new IllegalArgumentException("Transfer entries are invalid");
            } else if ("PREPARE".equals(operation.kind)) {
                PendingData pendingRow = pending.get(operation.id);
                if (pendingRow == null) throw new IllegalArgumentException("Pending operation is missing");
                boolean finalized = "FINAL".equals(operation.state);
                if (finalized != "COMPLETED".equals(pendingRow.state)) throw new IllegalArgumentException("Pending state does not match journal state");
                if (finalized && entries.size() != 1 || !finalized && !entries.isEmpty()) throw new IllegalArgumentException("Pending entry cardinality is invalid");
                if (finalized && !operation.player.equals(entries.get(0).account)) throw new IllegalArgumentException("Pending entry account is invalid");
            }
        }
        for (String key : seenEntries) {
            String operation = key.substring(0, key.indexOf('/'));
            if (!operations.containsKey(operation)) throw new IllegalArgumentException("Entry references unknown operation");
        }
    }

    private static void validateNotes(List<NoteData> notes, Map<String, NoteData> byId,
                                      Map<String, OperationData> operations,
                                      Map<String, PendingData> pending) {
        for (NoteData note : notes) {
            OperationData issue = operations.get(note.issueOperation);
            if (issue == null || !"PREPARE".equals(issue.kind) || !note.id.equals(issue.note)
                    || !note.issuer.equals(issue.player)
                    || amount(note.amount, "note amount") != signed(issue.amount, "operation amount")) {
                throw new IllegalArgumentException("Note issue relationship is invalid");
            }
            PendingData issuePending = pending.get(note.issueOperation);
            if (issuePending == null || !"NOTE_ISSUE".equals(issuePending.kind)) {
                throw new IllegalArgumentException("Note issue pending relationship is invalid");
            }
            if ("RESERVED".equals(note.status)) {
                if (!("PREPARED".equals(issuePending.state) || "APPLYING".equals(issuePending.state))
                        || !note.id.equals(issuePending.note)) {
                    throw new IllegalArgumentException("Reserved note state is invalid");
                }
            } else if ("ISSUED".equals(note.status)) {
                if (!"COMPLETED".equals(issuePending.state) || !"FINAL".equals(issue.state)
                        || !note.id.equals(issuePending.note) || note.redeemOperation != null) {
                    throw new IllegalArgumentException("Issued note state is invalid");
                }
            } else if ("CANCELLED".equals(note.status)) {
                if (!"CANCELLED".equals(issuePending.state) || !"CANCELLED".equals(issue.state)
                        || note.redeemOperation != null) {
                    throw new IllegalArgumentException("Cancelled note state is invalid");
                }
            } else if ("REDEEMED".equals(note.status)) {
                validateRedeemedNote(note, issue, issuePending, operations, pending);
            }
        }
        for (PendingData operation : pending.values()) {
            if (!"NOTE_ISSUE".equals(operation.kind) && !"NOTE_REDEEM".equals(operation.kind)) continue;
            if (operation.note == null || !byId.containsKey(operation.note)) {
                throw new IllegalArgumentException("Note pending operation requires a valid note");
            }
            if ("NOTE_REDEEM".equals(operation.kind) && "COMPLETED".equals(operation.state)
                    && !operation.operation.equals(byId.get(operation.note).redeemOperation)) {
                throw new IllegalArgumentException("Redemption operation does not own note");
            }
        }
    }

    private static void validateRedeemedNote(NoteData note, OperationData issue,
                                              PendingData issuePending,
                                              Map<String, OperationData> operations,
                                              Map<String, PendingData> pending) {
        if (!"COMPLETED".equals(issuePending.state) || !"FINAL".equals(issue.state)
                || note.redeemOperation == null) {
            throw new IllegalArgumentException("Redeemed note state is invalid");
        }
        OperationData redeem = operations.get(note.redeemOperation);
        PendingData redeemPending = pending.get(note.redeemOperation);
        if (redeem == null || redeemPending == null || !"PREPARE".equals(redeem.kind)
                || !"NOTE_REDEEM".equals(redeemPending.kind)
                || !"COMPLETED".equals(redeemPending.state)
                || !note.id.equals(redeem.note)
                || amount(note.amount, "note amount") != signed(redeem.amount, "redemption amount")) {
            throw new IllegalArgumentException("Note redemption relationship is invalid");
        }
    }

    private static void validateActiveRedemption(PendingData pending, Map<String, NoteData> notes) {
        NoteData note = notes.get(pending.note);
        if (note == null || !"ISSUED".equals(note.status) || note.redeemOperation != null) {
            throw new IllegalArgumentException("Active redemption requires an issued, unredeemed note");
        }
    }
    static UUID uuid(String value, String label) { try { if (value == null) throw new IllegalArgumentException(label + " is missing"); return UUID.fromString(value); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid " + label, e); } }
    static long amount(String value, String label) { if (value == null || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid " + label); try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + label, e); } }
    static long signed(String value, String label) { if (value == null || !value.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException("Invalid " + label); try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + label, e); } }

    static final class ImportData { final ExportDocument document; ImportData(ExportDocument d) { document=d; } }
    static final class ExportDocument { int schemaVersion; List<AccountData> accounts; List<OperationData> operations; List<EntryData> entries; List<NoteData> notes; List<PendingData> pending; List<AuditData> audit; }
    static final class AccountData { String id,name,balance,revision; }
    static final class OperationData { String id,kind,fingerprint,actor,target,from,to,player,note,amount,delta,payload,reason,state; long createdAt; Long resolvedAt; }
    static final class EntryData { String operation,account,before,after,delta,accountRevision; }
    static final class NoteData { String id,amount,status,issuer,issueOperation,redeemOperation; }
    static final class PendingData { String operation,player,kind,amount,payload,note,state; long createdAt; }
    static final class AuditData { String operation,actor,action,reason; long createdAt; }
}
