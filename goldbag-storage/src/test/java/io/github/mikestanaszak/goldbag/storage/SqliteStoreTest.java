package io.github.mikestanaszak.goldbag.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteStoreTest {
    @TempDir Path temp;

    @Test void transferIsAtomicAndFingerprintIdempotent() {
        Path db=temp.resolve("economy.db"); UUID a=UUID.randomUUID(), b=UUID.randomUUID(), op=UUID.randomUUID();
        try(SqliteStore store=new SqliteStore(db,10000)) {
            store.ensureAccount(a,"Alice"); store.ensureAccount(b,"Bob");
            store.adjust(UUID.randomUUID(),null,a,5000,"seed");
            SqliteStore.Receipt first=store.transfer(op,a,b,2000);
            SqliteStore.Receipt replay=store.transfer(op,a,b,2000);
            assertFalse(first.replayed()); assertTrue(replay.replayed()); assertEquals(3000,store.account(a).orElseThrow().balance()); assertEquals(2000,store.account(b).orElseThrow().balance());
            assertThrows(IllegalStateException.class,()->store.transfer(op,a,b,1000));
            assertThrows(IllegalStateException.class,()->store.transfer(UUID.randomUUID(),a,b,4000));
            assertEquals(3000,store.account(a).orElseThrow().balance()); assertEquals(2000,store.account(b).orElseThrow().balance());
        }
        try(SqliteStore reopened=new SqliteStore(db,10000)) { assertEquals(3000,reopened.account(a).orElseThrow().balance()); assertEquals(2000,reopened.account(b).orElseThrow().balance()); }
    }

    @Test void pendingReservationBlocksAndCompletionIsDurable() {
        UUID player=UUID.randomUUID(); Path db=temp.resolve("pending.db"); UUID seed=UUID.randomUUID(), pending=UUID.randomUUID();
        try(SqliteStore store=new SqliteStore(db,10000)) {
            store.ensureAccount(player,"Player"); store.adjust(seed,null,player,5000,"seed");
            store.prepare(pending,player,SqliteStore.Kind.WITHDRAW,4000,"diamond:1",null);
            assertTrue(store.isBlocked(player)); assertEquals(1,store.pending().size());
            assertThrows(IllegalStateException.class,()->store.adjust(UUID.randomUUID(),null,player,1,"blocked"));
            store.markApplying(pending); SqliteStore.Receipt result=store.complete(pending);
            assertFalse(result.replayed()); assertEquals(1000,store.account(player).orElseThrow().balance()); assertFalse(store.isBlocked(player));
            assertTrue(store.complete(pending).replayed());
        }
    }

    @Test void noteCannotRedeemTwiceAndExportRestoreIsTransactional() {
        UUID issuer=UUID.randomUUID(), redeemer=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(), redeem=UUID.randomUUID(); Path db=temp.resolve("notes.db"); String exported;
        try(SqliteStore store=new SqliteStore(db,10000)) {
            store.ensureAccount(issuer,"Issuer"); store.ensureAccount(redeemer,"Redeemer"); store.adjust(UUID.randomUUID(),null,issuer,5000,"seed");
            store.prepare(issue,issuer,SqliteStore.Kind.NOTE_ISSUE,2500,"paper",note); store.markApplying(issue); store.complete(issue); assertEquals("ISSUED",store.note(note).orElseThrow().status());
            store.prepareRedemption(redeem,redeemer,note,"paper"); store.markApplying(redeem); store.complete(redeem); assertEquals("REDEEMED",store.note(note).orElseThrow().status());
            assertThrows(IllegalStateException.class,()->store.prepareRedemption(UUID.randomUUID(),issuer,note,"forged"));
            exported=store.exportJson(); JsonObject root=JsonParser.parseString(exported).getAsJsonObject(); assertEquals(2,root.get("schemaVersion").getAsInt()); assertTrue(root.getAsJsonArray("accounts").get(0).getAsJsonObject().get("balance").isJsonPrimitive());
        }
        Path restore=temp.resolve("restore.db"); try(SqliteStore restored=new SqliteStore(restore,10000)) { restored.importJson(exported); assertEquals(2500,restored.account(redeemer).orElseThrow().balance()); assertEquals("REDEEMED",restored.note(note).orElseThrow().status()); assertThrows(IllegalArgumentException.class,()->restored.importJson("{}")); assertEquals(2500,restored.account(redeemer).orElseThrow().balance()); }
    }

    @Test void malformedImportDoesNotWriteAndSecondOwnerIsRejected() {
        Path db=temp.resolve("owner.db"); SqliteStore first=new SqliteStore(db,1000); UUID id=UUID.randomUUID(); first.ensureAccount(id,"One");
        assertThrows(IllegalStateException.class,()->new SqliteStore(db,1000)); first.close();
        try(SqliteStore empty=new SqliteStore(temp.resolve("empty.db"),1000)) { assertThrows(IllegalArgumentException.class,()->empty.importJson("{\"schemaVersion\":1,\"accounts\":[],\"operations\":[],\"entries\":[],\"notes\":[],\"pending\":[],\"audit\":[{\"operation\":\"bad\"}]}")); assertTrue(empty.top(1).isEmpty()); }
    }

    @Test void applyingResolutionIsAuditedAndIdempotent() {
        UUID player=UUID.randomUUID(), op=UUID.randomUUID(), actor=UUID.randomUUID();
        try(SqliteStore store=new SqliteStore(temp.resolve("resolve.db"),10000)) {
            store.ensureAccount(player,"Player"); store.prepare(op,player,SqliteStore.Kind.DEPOSIT,75,"raw_gold:1",null); store.markApplying(op);
            SqliteStore.Receipt first=store.resolve(op,true,actor,"inventory confirmed"); SqliteStore.Receipt replay=store.resolve(op,true,actor,"inventory confirmed");
            assertFalse(first.replayed()); assertTrue(replay.replayed()); assertEquals(75,store.account(player).orElseThrow().balance());
            assertThrows(IllegalStateException.class,()->store.resolve(op,true,actor,"different evidence"));
        }
    }

    @Test void unresolvedPendingExportCanBeRestored() {
        UUID player=UUID.randomUUID(), op=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("source-pending.db"),10000)) {
            store.ensureAccount(player,"Player"); store.prepare(op,player,SqliteStore.Kind.DEPOSIT,120,"raw_iron:1",null); exported=store.exportJson();
        }
        try(SqliteStore restored=new SqliteStore(temp.resolve("destination-pending.db"),10000)) {
            restored.importJson(exported); assertEquals(1,restored.pending().size()); assertTrue(restored.isBlocked(player)); assertEquals(120,restored.pending().get(0).amount());
        }
    }

    @Test void signedOperationsRoundTripThroughExportImport() {
        UUID account=UUID.randomUUID(), other=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("signed-source.db"),10000)) {
            store.ensureAccount(account,"Account"); store.ensureAccount(other,"Other");
            store.adjust(UUID.randomUUID(),null,account,5000,"seed");
            store.adjust(UUID.randomUUID(),null,account,-500,"debit");
            store.setBalance(UUID.randomUUID(),null,account,4000,"set lower");
            store.transfer(UUID.randomUUID(),account,other,1000);
            exported=store.exportJson();
        }
        try(SqliteStore restored=new SqliteStore(temp.resolve("signed-destination.db"),10000)) {
            assertDoesNotThrow(()->restored.importJson(exported));
            assertEquals(3000,restored.account(account).orElseThrow().balance());
            assertEquals(1000,restored.account(other).orElseThrow().balance());
        }
    }

    @Test void forgedBalanceOrRevisionCannotBeImported() {
        UUID account=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("forged-source.db"),10000)) {
            store.ensureAccount(account,"Account"); exported=store.exportJson();
        }
        String forgedBalance=exported.replace("\"balance\":\"0\"","\"balance\":\"999\"");
        String forgedRevision=exported.replace("\"revision\":\"0\"","\"revision\":\"1\"");
        try(SqliteStore restored=new SqliteStore(temp.resolve("forged-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forgedBalance));
            assertTrue(restored.top(1).isEmpty());
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forgedRevision));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void malformedNotePendingRowsAreRejectedBeforeWrite() {
        UUID player=UUID.randomUUID(), note=UUID.randomUUID(), op=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("bad-note-source.db"),10000)) {
            store.ensureAccount(player,"Player"); store.adjust(UUID.randomUUID(),null,player,500,"seed"); store.prepare(op,player,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); exported=store.exportJson();
        }
        String missingNote=exported.replace("\"note\":\""+note+"\"","\"note\":null");
        try(SqliteStore restored=new SqliteStore(temp.resolve("bad-note-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(missingNote));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void forgedNoteStatusAndMissingLedgerEntryAreRejected() {
        UUID player=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("forged-note-source.db"),10000)) {
            store.ensureAccount(player,"Player"); store.adjust(UUID.randomUUID(),null,player,500,"seed"); store.prepare(issue,player,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); store.markApplying(issue); store.complete(issue); exported=store.exportJson();
        }
        String forgedStatus=exported.replace("\"status\":\"ISSUED\"","\"status\":\"REDEEMED\"");
        String forgedEntries=exported.replaceFirst("\"entries\":\\[[^]]*\\]", "\"entries\":[]");
        try(SqliteStore restored=new SqliteStore(temp.resolve("forged-note-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forgedStatus));
            assertTrue(restored.top(1).isEmpty());
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forgedEntries));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void typedFingerprintsDistinguishNullLiteralAndSeparators() {
        UUID player=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        try(SqliteStore store=new SqliteStore(temp.resolve("fingerprints.db"),10000)) {
            store.ensureAccount(player,"Player");
            store.prepare(first,player,SqliteStore.Kind.DEPOSIT,10,null,null);
            assertThrows(IllegalStateException.class,()->store.prepare(first,player,SqliteStore.Kind.DEPOSIT,10,"<null>",null));
            store.cancelPrepared(first,"cleanup");
            store.prepare(second,player,SqliteStore.Kind.DEPOSIT,10,"a\u001fb",null);
            assertThrows(IllegalStateException.class,()->store.prepare(second,player,SqliteStore.Kind.DEPOSIT,10,"a",null));
        }
    }

    @Test void existingUnversionedDatabaseIsRejected() throws Exception {
        Path db=temp.resolve("legacy.db");
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+db)) {
            connection.createStatement().execute("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, balance INTEGER, revision INTEGER, updated_at INTEGER)");
        }
        assertThrows(IllegalStateException.class,()->new SqliteStore(db,10000));
    }

    @Test void uppercaseIdentityFieldsAreCanonicalizedOnRestore() {
        UUID player=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("uppercase-source.db"),10000)) {
            store.ensureAccount(player,"Player"); store.adjust(UUID.randomUUID(),null,player,500,"seed");
            store.prepare(issue,player,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); exported=store.exportJson();
        }
        exported=replaceUuidWithUppercase(exported,player,issue,note);
        try(SqliteStore restored=new SqliteStore(temp.resolve("uppercase-destination.db"),10000)) {
            restored.importJson(exported);
            assertEquals(500,restored.account(player).orElseThrow().balance());
            assertEquals(note,restored.pending().get(0).noteId());
            assertEquals(player,restored.pending().get(0).playerId());
            assertEquals("RESERVED",restored.note(note).orElseThrow().status());
            restored.cancelPrepared(issue,"restored cleanup");
            assertTrue(restored.pending().isEmpty());
            assertEquals("CANCELLED",restored.note(note).orElseThrow().status());
        }
    }

    @Test void duplicateActiveRedemptionAcrossAccountsIsRejected() {
        UUID issuer=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(), redeem=UUID.randomUUID(), duplicate=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("duplicate-note-source.db"),10000)) {
            store.ensureAccount(issuer,"Issuer"); store.ensureAccount(first,"First"); store.ensureAccount(second,"Second"); store.adjust(UUID.randomUUID(),null,issuer,500,"seed");
            store.prepare(issue,issuer,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); store.markApplying(issue); store.complete(issue);
            store.prepareRedemption(redeem,first,note,"paper");
            exported=store.exportJson();
        }
        JsonObject root=JsonParser.parseString(exported).getAsJsonObject();
        JsonObject operation=null, pending=null;
        for(var element:root.getAsJsonArray("operations")) if(redeem.toString().equals(element.getAsJsonObject().get("id").getAsString())) operation=element.getAsJsonObject().deepCopy();
        for(var element:root.getAsJsonArray("pending")) if(redeem.toString().equals(element.getAsJsonObject().get("operation").getAsString())) pending=element.getAsJsonObject().deepCopy();
        operation.addProperty("id",duplicate.toString()); operation.addProperty("player",second.toString()); operation.addProperty("fingerprint",StoreFingerprint.of("PREPARE_REDEEM",second,note,operation.get("payload").isJsonNull()?null:operation.get("payload").getAsString())); pending.addProperty("operation",duplicate.toString()); pending.addProperty("player",second.toString());
        root.getAsJsonArray("operations").add(operation); root.getAsJsonArray("pending").add(pending);
        try(SqliteStore restored=new SqliteStore(temp.resolve("duplicate-note-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(root.toString()));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void noteIssueMustOwnItsIssueOperation() {
        UUID player=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("note-owner-source.db"),10000)) {
            store.ensureAccount(player,"Player"); store.adjust(UUID.randomUUID(),null,player,500,"seed"); store.prepare(issue,player,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); exported=store.exportJson();
        }
        String forged=exported.replace("\"issueOperation\":\""+issue+"\"","\"issueOperation\":\""+UUID.randomUUID()+"\"");
        try(SqliteStore restored=new SqliteStore(temp.resolve("note-owner-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forged));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void tamperedSetBalanceDeltaIsRejected() {
        UUID player=UUID.randomUUID(); String exported;
        try(SqliteStore store=new SqliteStore(temp.resolve("set-delta-source.db"),10000)) {
            store.ensureAccount(player,"Player"); store.setBalance(UUID.randomUUID(),null,player,100,"set"); exported=store.exportJson();
        }
        String forged=exported.replaceFirst("\"delta\":\"100\"","\"delta\":\"999\"");
        try(SqliteStore restored=new SqliteStore(temp.resolve("set-delta-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(forged));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    @Test void activeRedemptionRequiresIssuedNote() {
        UUID issuer=UUID.randomUUID(), redeemer=UUID.randomUUID(), note=UUID.randomUUID(), issue=UUID.randomUUID(), redeem=UUID.randomUUID();
        String redeemedExport;
        try(SqliteStore store=new SqliteStore(temp.resolve("redeemed-active-source.db"),10000)) {
            store.ensureAccount(issuer,"Issuer"); store.ensureAccount(redeemer,"Redeemer"); store.adjust(UUID.randomUUID(),null,issuer,500,"seed");
            store.prepare(issue,issuer,SqliteStore.Kind.NOTE_ISSUE,100,"paper",note); store.markApplying(issue); store.complete(issue);
            store.prepareRedemption(redeem,redeemer,note,"paper"); store.markApplying(redeem); store.complete(redeem); redeemedExport=store.exportJson();
        }
        String redeemedWithActive=appendActiveRedemption(redeemedExport,issue,UUID.randomUUID(),redeemer,note);
        try(SqliteStore restored=new SqliteStore(temp.resolve("redeemed-active-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(redeemedWithActive));
            assertTrue(restored.top(1).isEmpty());
        }

        UUID cancelledNote=UUID.randomUUID(), cancelledIssue=UUID.randomUUID(); String cancelledExport;
        try(SqliteStore store=new SqliteStore(temp.resolve("cancelled-active-source.db"),10000)) {
            store.ensureAccount(issuer,"Issuer"); store.adjust(UUID.randomUUID(),null,issuer,500,"seed");
            store.prepare(cancelledIssue,issuer,SqliteStore.Kind.NOTE_ISSUE,100,"paper",cancelledNote); store.cancelPrepared(cancelledIssue,"inventory changed"); cancelledExport=store.exportJson();
        }
        String cancelledWithActive=appendActiveRedemption(cancelledExport,cancelledIssue,UUID.randomUUID(),redeemer,cancelledNote);
        try(SqliteStore restored=new SqliteStore(temp.resolve("cancelled-active-destination.db"),10000)) {
            assertThrows(IllegalArgumentException.class,()->restored.importJson(cancelledWithActive));
            assertTrue(restored.top(1).isEmpty());
        }
    }

    private static String appendActiveRedemption(String exported, UUID templateOperation, UUID duplicateOperation, UUID player, UUID note) {
        JsonObject root=JsonParser.parseString(exported).getAsJsonObject();
        JsonObject template=null;
        for (var element:root.getAsJsonArray("operations")) {
            if (templateOperation.toString().equals(element.getAsJsonObject().get("id").getAsString())) template=element.getAsJsonObject();
        }
        JsonObject operation=template.deepCopy();
        operation.addProperty("id",duplicateOperation.toString()); operation.addProperty("player",player.toString()); operation.addProperty("note",note.toString()); operation.addProperty("amount","100"); operation.addProperty("payload","paper"); operation.addProperty("state","PREPARED"); operation.addProperty("fingerprint",StoreFingerprint.of("PREPARE_REDEEM",player,note,"paper")); operation.remove("resolvedAt");
        root.getAsJsonArray("operations").add(operation);
        JsonObject pending=new JsonObject(); pending.addProperty("operation",duplicateOperation.toString()); pending.addProperty("player",player.toString()); pending.addProperty("kind","NOTE_REDEEM"); pending.addProperty("amount","100"); pending.addProperty("payload","paper"); pending.addProperty("note",note.toString()); pending.addProperty("state","PREPARED"); pending.addProperty("createdAt",999999L);
        root.getAsJsonArray("pending").add(pending);
        return root.toString();
    }

    private static String replaceUuidWithUppercase(String json, UUID... ids) {
        for (UUID id : ids) json=json.replace(id.toString(),id.toString().toUpperCase());
        return json;
    }
}
