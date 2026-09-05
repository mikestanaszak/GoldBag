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
        try (PreparedStatement s = c.prepareStatement("SELECT op_id,account_id,before_balance,after_balance,delta FROM operation_entries ORDER BY op_id,account_id")) {
            try (ResultSet r = s.executeQuery()) { while (r.next()) { EntryData e=new EntryData(); e.operation=r.getString(1); e.account=r.getString(2); e.before=Long.toString(r.getLong(3)); e.after=Long.toString(r.getLong(4)); e.delta=Long.toString(r.getLong(5)); d.entries.add(e); } }
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
        Set<String> accounts = new HashSet<>();
        for (AccountData a : d.accounts) {
            UUID id = uuid(a.id, "account id");
            if (!accounts.add(id.toString()) || a.name == null || a.name.isBlank()) throw new IllegalArgumentException("Invalid or duplicate account");
            long balance = amount(a.balance, "account balance"); long revision = amount(a.revision, "account revision");
            if (balance < 0 || balance > maxBalance || revision < 0) throw new IllegalArgumentException("Invalid account values");
        }
        Set<String> operations = new HashSet<>();
        Map<String, OperationData> operationById = new HashMap<>();
        for (OperationData o : d.operations) {
            UUID id=uuid(o.id,"operation id"); if (!operations.add(id.toString()) || o.kind == null || o.fingerprint == null || o.state == null) throw new IllegalArgumentException("Invalid or duplicate operation"); operationById.put(id.toString(),o);
            if (!Set.of("ADJUST","SET_BALANCE","TRANSFER","PREPARE").contains(o.kind) || !Set.of("FINAL","CANCELLED","PREPARED","APPLYING","COMPLETED").contains(o.state)) throw new IllegalArgumentException("Invalid operation kind or state");
            if (!"PREPARE".equals(o.kind) && !"FINAL".equals(o.state)) throw new IllegalArgumentException("Direct operation must be finalized");
            checkOptionalAccount(o.actor, accounts); checkOptionalAccount(o.target, accounts); checkOptionalAccount(o.from, accounts); checkOptionalAccount(o.to, accounts); checkOptionalAccount(o.player, accounts);
            if (o.note != null) uuid(o.note,"operation note id");
            if (o.amount != null && amount(o.amount,"operation amount") <= 0) throw new IllegalArgumentException("Invalid operation amount");
            if (o.delta != null) amount(o.delta,"operation delta");
        }
        Set<String> entries = new HashSet<>();
        for (EntryData e : d.entries) {
            UUID op=uuid(e.operation,"entry operation"); UUID account=uuid(e.account,"entry account"); if (!operations.contains(op.toString()) || !accounts.contains(account.toString()) || !entries.add(op+"/"+account)) throw new IllegalArgumentException("Invalid or duplicate operation entry");
            long before=amount(e.before,"entry before"); long after=amount(e.after,"entry after"); long delta=signed(e.delta,"entry delta"); if (before<0 || after<0 || after-before != delta) throw new IllegalArgumentException("Inconsistent operation entry");
        }
        Set<String> notes = new HashSet<>();
        for (NoteData n : d.notes) {
            UUID id=uuid(n.id,"note id"); if (!notes.add(id.toString()) || amount(n.amount,"note amount")<=0 || n.status==null || !Set.of("RESERVED","ISSUED","REDEEMED","CANCELLED").contains(n.status)) throw new IllegalArgumentException("Invalid or duplicate note");
            checkAccount(n.issuer,accounts); UUID issue=uuid(n.issueOperation,"note issue operation"); if (!operations.contains(issue.toString())) throw new IllegalArgumentException("Note references unknown issue operation"); if (n.redeemOperation != null && !operations.contains(uuid(n.redeemOperation,"note redemption operation").toString())) throw new IllegalArgumentException("Note references unknown redemption operation");
        }
        Set<String> pendingOps = new HashSet<>();
        Map<String, PendingData> pendingByOp = new HashMap<>();
        for (PendingData p : d.pending) {
            UUID op=uuid(p.operation,"pending operation"); if (!operations.contains(op.toString()) || !pendingOps.add(op.toString()) || !Set.of("PREPARED","APPLYING","COMPLETED","CANCELLED").contains(p.state) || p.kind == null || !Set.of("DEPOSIT","WITHDRAW","NOTE_ISSUE","NOTE_REDEEM").contains(p.kind)) throw new IllegalArgumentException("Invalid pending operation");
            checkAccount(p.player,accounts); if (amount(p.amount,"pending amount")<=0) throw new IllegalArgumentException("Invalid pending amount"); if (p.note != null) uuid(p.note,"pending note id"); PendingData previous=pendingByOp.put(op.toString(),p); if(previous!=null)throw new IllegalArgumentException("Duplicate pending operation");
            OperationData operation=operationById.get(op.toString());
            if (operation == null || !"PREPARE".equals(operation.kind) || !p.player.equals(operation.player) || !p.amount.equals(operation.amount) || !same(p.note,operation.note) || !pendingStateMatches(p.state,operation.state)) throw new IllegalArgumentException("Pending operation does not match its journal row");
        }
        for (OperationData operation : d.operations) if ("PREPARE".equals(operation.kind) && !pendingByOp.containsKey(operation.id) && !"FINAL".equals(operation.state)) throw new IllegalArgumentException("Unresolved journal operation is missing its pending row");
        Map<String, NoteData> noteById = new HashMap<>();
        for (NoteData n : d.notes) noteById.put(n.id,n);
        for (PendingData p : d.pending) if (p.note != null) { NoteData n=noteById.get(p.note); if (n==null || !p.kind.equals("NOTE_ISSUE") && !p.kind.equals("NOTE_REDEEM")) throw new IllegalArgumentException("Pending note relationship is invalid"); if ("NOTE_ISSUE".equals(p.kind) && !p.operation.equals(n.issueOperation)) throw new IllegalArgumentException("Note issue relationship is invalid"); if ("NOTE_REDEEM".equals(p.kind) && !p.operation.equals(n.redeemOperation) && "COMPLETED".equals(p.state)) throw new IllegalArgumentException("Note redemption relationship is invalid"); }
        for (AuditData a : d.audit) { if (!operations.contains(uuid(a.operation,"audit operation").toString()) || a.action==null || a.reason==null) throw new IllegalArgumentException("Invalid audit record"); if (a.actor != null) uuid(a.actor,"audit actor"); }
        return new ImportData(d);
    }

    private static void requireFields(JsonObject object, String... fields) { for (String field : fields) if (!object.has(field)) throw new IllegalArgumentException("Missing export field: " + field); }
    private static boolean same(String first,String second) { return first==null?second==null:first.equals(second); }
    private static boolean pendingStateMatches(String pending,String operation) { return pending.equals(operation) || "COMPLETED".equals(pending) && "FINAL".equals(operation); }
    private static void checkOptionalAccount(String id, Set<String> accounts) { if (id != null && !accounts.contains(uuid(id,"account reference").toString())) throw new IllegalArgumentException("Unknown account reference"); }
    private static void checkAccount(String id, Set<String> accounts) { if (id == null || !accounts.contains(uuid(id,"account reference").toString())) throw new IllegalArgumentException("Unknown account reference"); }
    static UUID uuid(String value, String label) { try { if (value == null) throw new IllegalArgumentException(label + " is missing"); return UUID.fromString(value); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid " + label, e); } }
    static long amount(String value, String label) { if (value == null || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid " + label); try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + label, e); } }
    static long signed(String value, String label) { if (value == null || !value.matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException("Invalid " + label); try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + label, e); } }

    static final class ImportData { final ExportDocument document; ImportData(ExportDocument d) { document=d; } }
    static final class ExportDocument { int schemaVersion; List<AccountData> accounts; List<OperationData> operations; List<EntryData> entries; List<NoteData> notes; List<PendingData> pending; List<AuditData> audit; }
    static final class AccountData { String id,name,balance,revision; }
    static final class OperationData { String id,kind,fingerprint,actor,target,from,to,player,note,amount,delta,payload,reason,state; long createdAt; Long resolvedAt; }
    static final class EntryData { String operation,account,before,after,delta; }
    static final class NoteData { String id,amount,status,issuer,issueOperation,redeemOperation; }
    static final class PendingData { String operation,player,kind,amount,payload,note,state; long createdAt; }
    static final class AuditData { String operation,actor,action,reason; long createdAt; }
}
