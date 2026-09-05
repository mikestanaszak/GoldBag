package io.github.mikestanaszak.goldbag.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
            exported=store.exportJson(); JsonObject root=JsonParser.parseString(exported).getAsJsonObject(); assertEquals(1,root.get("schemaVersion").getAsInt()); assertTrue(root.getAsJsonArray("accounts").get(0).getAsJsonObject().get("balance").isJsonPrimitive());
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
}
