package io.github.mikestanaszak.goldbag.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Serialized, transactional SQLite repository for GoldBag money and durable physical operations. */
public final class SqliteStore implements AutoCloseable {
    public record Account(UUID id, String name, long balance, long revision) {}
    public record Receipt(UUID operationId, Map<UUID, Long> balances, boolean replayed) {}
    public enum Kind { DEPOSIT, WITHDRAW, NOTE_ISSUE, NOTE_REDEEM }
    public record Pending(UUID id, UUID playerId, Kind kind, long amount, String payload, UUID noteId, String state) {}
    public record Note(UUID id, long amount, String status) {}

    private final Path database;
    private final long maxBalance;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private Connection connection;
    private boolean closed;

    public SqliteStore(Path database, long maxBalance) {
        if (database == null) throw new IllegalArgumentException("Database path is required");
        if (maxBalance < 0) throw new IllegalArgumentException("Maximum balance must be nonnegative");
        this.database = database.toAbsolutePath().normalize();
        this.maxBalance = maxBalance;
        try {
            Path parent = this.database.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path lockPath = this.database.resolveSibling(this.database.getFileName().toString() + ".lock");
            this.lockChannel = FileChannel.open(lockPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
            FileLock acquired;
            try { acquired = lockChannel.tryLock(); } catch (OverlappingFileLockException e) { acquired = null; }
            if (acquired == null) { lockChannel.close(); throw new IllegalStateException("GoldBag database is already owned by another process"); }
            this.fileLock = acquired;
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
            this.connection.setAutoCommit(true);
            StoreSchema.initialise(this.connection);
        } catch (IOException | SQLException e) {
            try { if (this.connection != null) this.connection.close(); } catch (Exception ignored) {}
            try { if (this.fileLock != null) this.fileLock.release(); } catch (Exception ignored) {}
            try { if (this.lockChannel != null) this.lockChannel.close(); } catch (Exception ignored) {}
            throw new IllegalStateException("Unable to open GoldBag SQLite database", e);
        } catch (RuntimeException e) {
            try { if (this.connection != null) this.connection.close(); } catch (Exception ignored) {}
            try { if (this.fileLock != null) this.fileLock.release(); } catch (Exception ignored) {}
            try { if (this.lockChannel != null) this.lockChannel.close(); } catch (Exception ignored) {}
            throw e;
        }
    }

    public synchronized Account ensureAccount(UUID id, String name) {
        checkUuid(id, "account id"); checkName(name);
        return transaction(() -> {
            try (PreparedStatement s=connection.prepareStatement("INSERT INTO accounts(id,name,balance,revision,updated_at) VALUES(?,?,0,0,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,updated_at=excluded.updated_at")) { s.setString(1, id.toString()); s.setString(2,name.trim()); s.setLong(3,now()); s.executeUpdate(); }
            return accountRequired(id);
        });
    }

    public synchronized Optional<Account> account(UUID id) {
        checkUuid(id, "account id"); ensureOpen();
        try (PreparedStatement s=connection.prepareStatement("SELECT id,name,balance,revision FROM accounts WHERE id=?")) { s.setString(1,id.toString()); try (ResultSet r=s.executeQuery()) { return r.next()?Optional.of(readAccount(r)):Optional.empty(); } } catch(SQLException e) { throw failure(e); }
    }

    public synchronized Optional<Account> findAccount(String nameOrUuid) {
        ensureOpen(); if (nameOrUuid==null || nameOrUuid.isBlank()) throw new IllegalArgumentException("Account identifier is required");
        String text=nameOrUuid.trim();
        try { return account(UUID.fromString(text)); } catch (IllegalArgumentException ignored) {}
        try (PreparedStatement s=connection.prepareStatement("SELECT id,name,balance,revision FROM accounts WHERE lower(name)=lower(?) ORDER BY id")) { s.setString(1,text); try(ResultSet r=s.executeQuery()) { Account found=null; if(r.next()) found=readAccount(r); if(r.next()) throw new IllegalStateException("Account name is ambiguous; use a UUID"); return Optional.ofNullable(found); } } catch(SQLException e) { throw failure(e); }
    }

    public synchronized List<Account> top(int page) {
        ensureOpen(); if(page<1) throw new IllegalArgumentException("Page must be 1 or greater");
        List<Account> result=new ArrayList<>(); long offset=(long)(page-1)*10L;
        try (PreparedStatement s=connection.prepareStatement("SELECT id,name,balance,revision FROM accounts ORDER BY balance DESC,id ASC LIMIT 10 OFFSET ?")) { s.setLong(1,offset); try(ResultSet r=s.executeQuery()){while(r.next())result.add(readAccount(r));} return List.copyOf(result); } catch(SQLException e){throw failure(e);}
    }

    public synchronized Receipt adjust(UUID op, UUID actor, UUID target, long delta, String reason) {
        checkOp(op);
        checkUuid(target, "target account");
        checkReason(reason);
        if (delta == 0) throw new IllegalArgumentException("Adjustment cannot be zero");
        String fingerprint = StoreFingerprint.of("ADJUST", actor, target, delta, reason);
        ensureOpen();
        return transaction(() -> {
            Receipt replay = existingReceipt(op, fingerprint);
            if (replay != null) return replay;
            rejectExistingOperation(op);
            Account current = accountRequired(target);
            rejectBlocked(target);
            long next = checkedBalance(current.balance(), delta);
            insertOperation(op, "ADJUST", fingerprint, actor, target, null, null, null, null,
                    delta, delta, null, reason, "FINAL");
            updateBalance(target, next);
            insertEntry(op, target, current.balance(), next, delta, current.revision() + 1);
            return receipt(op, Map.of(target, next), false);
        });
    }

    public synchronized Receipt setBalance(UUID op, UUID actor, UUID target, long amount, String reason) {
        checkOp(op);
        checkUuid(target, "target account");
        checkReason(reason);
        checkAmountWithinMax(amount, "balance");
        String fingerprint = StoreFingerprint.of("SET_BALANCE", actor, target, amount, reason);
        ensureOpen();
        return transaction(() -> {
            Receipt replay = existingReceipt(op, fingerprint);
            if (replay != null) return replay;
            rejectExistingOperation(op);
            Account current = accountRequired(target);
            rejectBlocked(target);
            long delta = amount - current.balance();
            insertOperation(op, "SET_BALANCE", fingerprint, actor, target, null, null, null, null,
                    amount, delta, null, reason, "FINAL");
            updateBalance(target, amount);
            insertEntry(op, target, current.balance(), amount, delta, current.revision() + 1);
            return receipt(op, Map.of(target, amount), false);
        });
    }

    public synchronized Receipt transfer(UUID op, UUID from, UUID to, long amount) {
        checkOp(op);
        checkUuid(from, "sender");
        checkUuid(to, "recipient");
        if (from.equals(to)) throw new IllegalArgumentException("Cannot transfer to the same account");
        checkPositive(amount, "transfer amount");
        String fingerprint = StoreFingerprint.of("TRANSFER", from, to, amount, null);
        ensureOpen();
        return transaction(() -> {
            Receipt replay = existingReceipt(op, fingerprint);
            if (replay != null) return replay;
            rejectExistingOperation(op);
            rejectBlocked(from);
            rejectBlocked(to);
            Account sender = accountRequired(from);
            Account receiver = accountRequired(to);
            if (sender.balance() < amount) throw new IllegalStateException("Insufficient balance");
            long credited = checkedBalance(receiver.balance(), amount);
            insertOperation(op, "TRANSFER", fingerprint, from, null, from, to, null, null,
                    amount, -amount, null, "transfer", "FINAL");
            updateBalance(from, sender.balance() - amount);
            updateBalance(to, credited);
            insertEntry(op, from, sender.balance(), sender.balance() - amount, -amount,
                    sender.revision() + 1);
            insertEntry(op, to, receiver.balance(), credited, amount, receiver.revision() + 1);
            Map<UUID, Long> balances = new LinkedHashMap<>();
            balances.put(from, sender.balance() - amount);
            balances.put(to, credited);
            return receipt(op, balances, false);
        });
    }

    public synchronized Pending prepare(UUID op, UUID player, Kind kind, long amount, String payload, UUID noteId) {
        checkOp(op);
        checkUuid(player, "player");
        if (kind == null || kind == Kind.NOTE_REDEEM) throw new IllegalArgumentException("Invalid prepare kind");
        checkPositive(amount, "pending amount");
        if (kind == Kind.NOTE_ISSUE && noteId == null) throw new IllegalArgumentException("Note issue requires a note id");
        if (noteId != null) checkUuid(noteId, "note id");
        String fingerprint = StoreFingerprint.of("PREPARE", player, kind, amount, payload, noteId);
        ensureOpen();
        return transaction(() -> {
            Pending existing = existingPending(op, fingerprint);
            if (existing != null) return existing;
            rejectExistingOperation(op);
            Account account = accountRequired(player);
            rejectBlocked(player);
            validatePrepareBalance(kind, amount, account);
            if (kind == Kind.NOTE_ISSUE && noteExists(noteId)) throw new IllegalStateException("Note id already exists");
            insertOperation(op, "PREPARE", fingerprint, null, null, null, null, player, noteId,
                    amount, null, payload, null, "PREPARED");
            if (kind == Kind.NOTE_ISSUE) insertNote(noteId, amount, "RESERVED", player, op);
            insertPending(op, player, kind, amount, payload, noteId, "PREPARED");
            return new Pending(op, player, kind, amount, payload, noteId, "PREPARED");
        });
    }

    public synchronized Pending prepareRedemption(UUID op, UUID player, UUID note, String payload) {
        checkOp(op);
        checkUuid(player, "player");
        checkUuid(note, "note id");
        String fingerprint = StoreFingerprint.of("PREPARE_REDEEM", player, note, payload);
        ensureOpen();
        return transaction(() -> {
            Pending existing = existingPending(op, fingerprint);
            if (existing != null) return existing;
            rejectExistingOperation(op);
            Account account = accountRequired(player);
            rejectBlocked(player);
            NoteData noteData = noteData(note);
            if (noteData == null) throw new IllegalStateException("Unknown banknote");
            if (!"ISSUED".equals(noteData.status)) throw new IllegalStateException("Banknote is not redeemable");
            if (notePending(note)) throw new IllegalStateException("Banknote redemption is already pending");
            if (checkedBalance(account.balance(), noteData.amount) > maxBalance) throw new IllegalStateException("Balance exceeds maximum");
            insertOperation(op, "PREPARE", fingerprint, null, null, null, null, player, note,
                    noteData.amount, null, payload, null, "PREPARED");
            insertPending(op, player, Kind.NOTE_REDEEM, noteData.amount, payload, note, "PREPARED");
            return new Pending(op, player, Kind.NOTE_REDEEM, noteData.amount, payload, note, "PREPARED");
        });
    }

    public synchronized void markApplying(UUID op) {
        checkOp(op);
        ensureOpen();
        transaction(() -> {
            PendingData pending = pendingData(op);
            if (pending == null) throw new IllegalStateException("Unknown pending operation");
            if ("APPLYING".equals(pending.state)) return null;
            if (!"PREPARED".equals(pending.state)) throw new IllegalStateException("Operation is not PREPARED");
            updatePendingState(op.toString(), "APPLYING");
            updateOperationState(op.toString(), "APPLYING");
            return null;
        });
    }

    public synchronized Receipt complete(UUID op) {
        checkOp(op);
        ensureOpen();
        return transaction(() -> {
            PendingData pending = pendingData(op);
            if (pending == null) {
                OperationState operation = operationState(op);
                if (operation != null && "FINAL".equals(operation.state)) return receipt(op, operationBalances(op), true);
                throw new IllegalStateException("Unknown pending operation");
            }
            if ("COMPLETED".equals(pending.state)) return receipt(op, operationBalances(op), true);
            if (!"APPLYING".equals(pending.state)) throw new IllegalStateException("Operation must be APPLYING before completion");
            return applyPending(pending, false, null, null);
        });
    }

    public synchronized void cancelPrepared(UUID op, String reason) {
        checkOp(op);
        checkReason(reason);
        ensureOpen();
        transaction(() -> {
            PendingData pending = pendingData(op);
            if (pending == null || "CANCELLED".equals(pending.state)) {
                OperationState operation = operationState(op);
                if (operation != null && "CANCELLED".equals(operation.state) && operationAuditMatches(op, "CANCEL", null, reason)) return null;
                if (pending == null) throw new IllegalStateException("Unknown pending operation");
            }
            if (!"PREPARED".equals(pending.state)) throw new IllegalStateException("Only PREPARED operations can be cancelled");
            updatePendingState(op.toString(), "CANCELLED");
            updateOperationState(op.toString(), "CANCELLED");
            if (pending.note != null && "NOTE_ISSUE".equals(pending.kind)) updateNoteStatus(pending.note, "CANCELLED", null);
            insertAudit(op, null, "CANCEL", reason);
            return null;
        });
    }

    public synchronized Receipt resolve(UUID op, boolean apply, UUID actor, String reason) {
        checkOp(op);
        checkUuid(actor, "resolution actor");
        checkReason(reason);
        ensureOpen();
        return transaction(() -> {
            PendingData pending = pendingData(op);
            if (pending == null || "COMPLETED".equals(pending.state) || "CANCELLED".equals(pending.state)) {
                OperationState operation = operationState(op);
                String action = apply ? "RESOLVE_APPLY" : "RESOLVE_CANCEL";
                if (operation != null && ("FINAL".equals(operation.state) || "CANCELLED".equals(operation.state)) && operationAuditMatches(op, action, actor, reason)) {
                    return "FINAL".equals(operation.state)
                            ? receipt(op, operationBalances(op), true)
                            : receipt(op, currentBalances(operation.player), true);
                }
                if (pending == null) throw new IllegalStateException("Unknown pending operation");
            }
            if (!"APPLYING".equals(pending.state)) throw new IllegalStateException("Only APPLYING operations require resolution");
            insertAudit(op, actor, apply ? "RESOLVE_APPLY" : "RESOLVE_CANCEL", reason);
            if (apply) return applyPending(pending, true, actor, reason);
            updatePendingState(op.toString(), "CANCELLED");
            updateOperationState(op.toString(), "CANCELLED");
            if (pending.note != null && "NOTE_ISSUE".equals(pending.kind)) updateNoteStatus(pending.note, "CANCELLED", null);
            return receipt(op, currentBalances(pending.player), false);
        });
    }

    public synchronized List<Pending> pending() {
        ensureOpen(); List<Pending> result=new ArrayList<>(); try(PreparedStatement s=connection.prepareStatement("SELECT op_id,player_id,kind,amount,payload,note_id,state FROM pending_operations WHERE state IN ('PREPARED','APPLYING') ORDER BY created_at,op_id")){try(ResultSet r=s.executeQuery()){while(r.next())result.add(readPending(r));}return List.copyOf(result);}catch(SQLException e){throw failure(e);}
    }

    public synchronized boolean isBlocked(UUID player) { checkUuid(player,"player"); ensureOpen(); try(PreparedStatement s=connection.prepareStatement("SELECT 1 FROM pending_operations WHERE player_id=? AND state IN ('PREPARED','APPLYING') LIMIT 1")){s.setString(1,player.toString());try(ResultSet r=s.executeQuery()){return r.next();}}catch(SQLException e){throw failure(e);} }

    public synchronized Optional<Note> note(UUID id) { checkUuid(id,"note id"); ensureOpen(); try { NoteData n=noteData(id); return n==null?Optional.empty():Optional.of(new Note(id,n.amount,n.status)); } catch(SQLException e) { throw failure(e); } }

    public synchronized String exportJson() { ensureOpen(); return transaction(() -> StoreJson.exportData(connection)); }

    public synchronized void importJson(String json) {
        ensureOpen(); StoreJson.ImportData imported=StoreJson.parseAndValidate(json,maxBalance); transaction(() -> { if(tableHasRows("accounts")||tableHasRows("operations")||tableHasRows("notes")||tableHasRows("pending_operations"))throw new IllegalStateException("Import destination must be empty"); insertImport(imported.document); return null; });
    }

    @Override public synchronized void close() {
        if(closed)return; closed=true; try{connection.close();}catch(SQLException e){throw failure(e);} finally {try{fileLock.release();}catch(IOException ignored){} try{lockChannel.close();}catch(IOException ignored){}} }

    private Receipt applyPending(PendingData p, boolean resolved, UUID actor, String reason) throws SQLException {
        Account a=accountRequired(UUID.fromString(p.player)); long delta=("DEPOSIT".equals(p.kind)||"NOTE_REDEEM".equals(p.kind))?p.amount:-p.amount; long next=checkedBalance(a.balance(),delta); if(delta<0 && a.balance()<p.amount)throw new IllegalStateException("Insufficient balance at completion");
        updateBalance(UUID.fromString(p.player),next); insertEntry(UUID.fromString(p.operation),UUID.fromString(p.player),a.balance(),next,delta,a.revision()+1);
        if("NOTE_ISSUE".equals(p.kind)) { NoteData n=noteData(UUID.fromString(p.note)); if(n==null||!"RESERVED".equals(n.status))throw new IllegalStateException("Reserved note is missing"); updateNoteStatus(p.note,"ISSUED",null); }
        if("NOTE_REDEEM".equals(p.kind)) { NoteData n=noteData(UUID.fromString(p.note)); if(n==null||!"ISSUED".equals(n.status))throw new IllegalStateException("Banknote is no longer redeemable"); updateNoteStatus(p.note,"REDEEMED",p.operation); }
        updatePendingState(p.operation,"COMPLETED"); updateOperationState(p.operation,"FINAL"); return receipt(UUID.fromString(p.operation),Map.of(UUID.fromString(p.player),next),false);
    }

    private void insertImport(StoreJson.ExportDocument d) throws SQLException {
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO accounts(id,name,balance,revision,updated_at) VALUES(?,?,?,?,?)")){for(StoreJson.AccountData a:d.accounts){s.setString(1,a.id);s.setString(2,a.name);s.setLong(3,StoreJson.amount(a.balance,"account balance"));s.setLong(4,StoreJson.amount(a.revision,"account revision"));s.setLong(5,now());s.addBatch();}s.executeBatch();}
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO operations(op_id,kind,fingerprint,actor_id,target_id,from_id,to_id,player_id,note_id,amount,delta,payload,reason,state,created_at,resolved_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){for(StoreJson.OperationData o:d.operations){s.setString(1,o.id);s.setString(2,o.kind);s.setString(3,o.fingerprint);setNullable(s,4,o.actor);setNullable(s,5,o.target);setNullable(s,6,o.from);setNullable(s,7,o.to);setNullable(s,8,o.player);setNullable(s,9,o.note);setNullableLong(s,10,o.amount);setNullableLong(s,11,o.delta);setNullable(s,12,o.payload);setNullable(s,13,o.reason);s.setString(14,o.state);s.setLong(15,o.createdAt);if(o.resolvedAt==null)s.setObject(16,null);else s.setLong(16,o.resolvedAt);s.addBatch();}s.executeBatch();}
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO operation_entries(op_id,account_id,before_balance,after_balance,delta,account_revision) VALUES(?,?,?,?,?,?)")){for(StoreJson.EntryData e:d.entries){s.setString(1,e.operation);s.setString(2,e.account);s.setLong(3,StoreJson.amount(e.before,"entry before"));s.setLong(4,StoreJson.amount(e.after,"entry after"));s.setLong(5,StoreJson.signed(e.delta,"entry delta"));s.setLong(6,StoreJson.amount(e.accountRevision,"entry revision"));s.addBatch();}s.executeBatch();}
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO notes(note_id,amount,status,issuer_id,issue_op,redeem_op) VALUES(?,?,?,?,?,?)")){for(StoreJson.NoteData n:d.notes){s.setString(1,n.id);s.setLong(2,StoreJson.amount(n.amount,"note amount"));s.setString(3,n.status);s.setString(4,n.issuer);s.setString(5,n.issueOperation);setNullable(s,6,n.redeemOperation);s.addBatch();}s.executeBatch();}
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO pending_operations(op_id,player_id,kind,amount,payload,note_id,state,created_at) VALUES(?,?,?,?,?,?,?,?)")){for(StoreJson.PendingData p:d.pending){s.setString(1,p.operation);s.setString(2,p.player);s.setString(3,p.kind);s.setLong(4,StoreJson.amount(p.amount,"pending amount"));setNullable(s,5,p.payload);setNullable(s,6,p.note);s.setString(7,p.state);s.setLong(8,p.createdAt);s.addBatch();}s.executeBatch();}
        try(PreparedStatement s=connection.prepareStatement("INSERT INTO operation_audit(op_id,actor_id,action,reason,created_at) VALUES(?,?,?,?,?)")){for(StoreJson.AuditData a:d.audit){s.setString(1,a.operation);setNullable(s,2,a.actor);s.setString(3,a.action);s.setString(4,a.reason);s.setLong(5,a.createdAt);s.addBatch();}s.executeBatch();}
    }

    private boolean tableHasRows(String table) throws SQLException { try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT 1 FROM "+table+" LIMIT 1")){return r.next();} }
    private void insertOperation(UUID op,String kind,String fp,UUID actor,UUID target,UUID from,UUID to,UUID player,UUID note,long amount,Long delta,String payload,String reason,String state)throws SQLException{try(PreparedStatement s=connection.prepareStatement("INSERT INTO operations(op_id,kind,fingerprint,actor_id,target_id,from_id,to_id,player_id,note_id,amount,delta,payload,reason,state,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){s.setString(1,op.toString());s.setString(2,kind);s.setString(3,fp);setNullable(s,4,actor==null?null:actor.toString());setNullable(s,5,target==null?null:target.toString());setNullable(s,6,from==null?null:from.toString());setNullable(s,7,to==null?null:to.toString());setNullable(s,8,player==null?null:player.toString());setNullable(s,9,note==null?null:note.toString());s.setLong(10,amount);if(delta==null)s.setObject(11,null);else s.setLong(11,delta);setNullable(s,12,payload);setNullable(s,13,reason);s.setString(14,state);s.setLong(15,now());s.executeUpdate();}}
    private void insertPending(UUID op,UUID player,Kind kind,long amount,String payload,UUID note,String state)throws SQLException{try(PreparedStatement s=connection.prepareStatement("INSERT INTO pending_operations(op_id,player_id,kind,amount,payload,note_id,state,created_at) VALUES(?,?,?,?,?,?,?,?)")){s.setString(1,op.toString());s.setString(2,player.toString());s.setString(3,kind.name());s.setLong(4,amount);setNullable(s,5,payload);setNullable(s,6,note==null?null:note.toString());s.setString(7,state);s.setLong(8,now());s.executeUpdate();}}
    private void insertNote(UUID id,long amount,String status,UUID issuer,UUID issueOp)throws SQLException{try(PreparedStatement s=connection.prepareStatement("INSERT INTO notes(note_id,amount,status,issuer_id,issue_op) VALUES(?,?,?,?,?)")){s.setString(1,id.toString());s.setLong(2,amount);s.setString(3,status);s.setString(4,issuer.toString());s.setString(5,issueOp.toString());s.executeUpdate();}}
    private void insertEntry(UUID op,UUID account,long before,long after,long delta,long revision)throws SQLException{try(PreparedStatement s=connection.prepareStatement("INSERT INTO operation_entries(op_id,account_id,before_balance,after_balance,delta,account_revision) VALUES(?,?,?,?,?,?)")){s.setString(1,op.toString());s.setString(2,account.toString());s.setLong(3,before);s.setLong(4,after);s.setLong(5,delta);s.setLong(6,revision);s.executeUpdate();}}
    private void insertAudit(UUID op,UUID actor,String action,String reason)throws SQLException{try(PreparedStatement s=connection.prepareStatement("INSERT INTO operation_audit(op_id,actor_id,action,reason,created_at) VALUES(?,?,?,?,?)")){s.setString(1,op.toString());setNullable(s,2,actor==null?null:actor.toString());s.setString(3,action);s.setString(4,reason);s.setLong(5,now());s.executeUpdate();}}
    private void updatePendingState(String op,String state)throws SQLException{try(PreparedStatement s=connection.prepareStatement("UPDATE pending_operations SET state=? WHERE op_id=?")){s.setString(1,state);s.setString(2,op);s.executeUpdate();}}
    private void updateOperationState(String op,String state)throws SQLException{try(PreparedStatement s=connection.prepareStatement("UPDATE operations SET state=?,resolved_at=? WHERE op_id=?")){s.setString(1,state);s.setLong(2,now());s.setString(3,op);s.executeUpdate();}}
    private void updateNoteStatus(String note,String status,String redeemOp)throws SQLException{try(PreparedStatement s=connection.prepareStatement("UPDATE notes SET status=?,redeem_op=? WHERE note_id=?")){s.setString(1,status);setNullable(s,2,redeemOp);s.setString(3,note);s.executeUpdate();}}
    private void updateBalance(UUID id,long balance)throws SQLException{try(PreparedStatement s=connection.prepareStatement("UPDATE accounts SET balance=?,revision=revision+1,updated_at=? WHERE id=?")){s.setLong(1,balance);s.setLong(2,now());s.setString(3,id.toString());if(s.executeUpdate()!=1)throw new IllegalStateException("Account disappeared");}}

    private Pending existingPending(UUID op,String fp)throws SQLException{OperationState state=operationState(op);if(state==null)return null;if(!fp.equals(state.fingerprint))throw new IllegalStateException("Operation UUID was reused with a different request");PendingData p=pendingData(op);if(p!=null)return new Pending(op,UUID.fromString(p.player),Kind.valueOf(p.kind),p.amount,p.payload,p.note==null?null:UUID.fromString(p.note),p.state);if("FINAL".equals(state.state))return new Pending(op,UUID.fromString(state.player),Kind.valueOf(state.kind),state.amount,state.payload,state.note==null?null:UUID.fromString(state.note),"COMPLETED");throw new IllegalStateException("Operation has no pending record");}
    private Receipt existingReceipt(UUID op,String fp)throws SQLException{OperationState o=operationState(op);if(o==null)return null;if(!fp.equals(o.fingerprint))throw new IllegalStateException("Operation UUID was reused with a different request");if("FINAL".equals(o.state))return receipt(op,operationBalances(op),true);throw new IllegalStateException("Operation is already in progress");}
    private void rejectExistingOperation(UUID op)throws SQLException{if(operationState(op)!=null)throw new IllegalStateException("Operation UUID was already used");}
    private OperationState operationState(UUID op)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT kind,fingerprint,state,player_id,amount,payload,note_id FROM operations WHERE op_id=?")){s.setString(1,op.toString());try(ResultSet r=s.executeQuery()){if(!r.next())return null;OperationState o=new OperationState();o.kind=r.getString(1);o.fingerprint=r.getString(2);o.state=r.getString(3);o.player=r.getString(4);o.amount=r.getLong(5);o.payload=r.getString(6);o.note=r.getString(7);return o;}}}
    private PendingData pendingData(UUID op)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT op_id,player_id,kind,amount,payload,note_id,state,created_at FROM pending_operations WHERE op_id=?")){s.setString(1,op.toString());try(ResultSet r=s.executeQuery()){if(!r.next())return null;return readPendingData(r);}}}
    private NoteData noteData(UUID id)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT note_id,amount,status,issuer_id,issue_op,redeem_op FROM notes WHERE note_id=?")){s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){if(!r.next())return null;NoteData n=new NoteData();n.amount=r.getLong(2);n.status=r.getString(3);n.issuer=r.getString(4);n.issueOp=r.getString(5);n.redeemOp=r.getString(6);return n;}}}
    private boolean noteExists(UUID id)throws SQLException{return noteData(id)!=null;}
    private boolean notePending(UUID id)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT 1 FROM pending_operations WHERE note_id=? AND state IN ('PREPARED','APPLYING')")){s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){return r.next();}}}
    private long reservedOutgoing(UUID player)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM pending_operations WHERE player_id=? AND state IN ('PREPARED','APPLYING') AND kind IN ('WITHDRAW','NOTE_ISSUE')")){s.setString(1,player.toString());try(ResultSet r=s.executeQuery()){return r.getLong(1);}}}
    private Map<UUID,Long> operationBalances(UUID op)throws SQLException{Map<UUID,Long> m=new LinkedHashMap<>();try(PreparedStatement s=connection.prepareStatement("SELECT account_id,after_balance FROM operation_entries WHERE op_id=? ORDER BY account_id")){s.setString(1,op.toString());try(ResultSet r=s.executeQuery()){while(r.next())m.put(UUID.fromString(r.getString(1)),r.getLong(2));}}return m;}
    private Map<UUID,Long> currentBalances(String player)throws SQLException{Account a=accountRequired(UUID.fromString(player));return Map.of(a.id(),a.balance());}
    private Account accountRequired(UUID id)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT id,name,balance,revision FROM accounts WHERE id=?")){s.setString(1,id.toString());try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalStateException("Unknown account: "+id);return readAccount(r);}}}
    private Account readAccount(ResultSet r)throws SQLException{return new Account(UUID.fromString(r.getString(1)),r.getString(2),r.getLong(3),r.getLong(4));}
    private Pending readPending(ResultSet r)throws SQLException{return new Pending(UUID.fromString(r.getString(1)),UUID.fromString(r.getString(2)),Kind.valueOf(r.getString(3)),r.getLong(4),r.getString(5),r.getString(6)==null?null:UUID.fromString(r.getString(6)),r.getString(7));}
    private PendingData readPendingData(ResultSet r)throws SQLException{PendingData p=new PendingData();p.operation=r.getString(1);p.player=r.getString(2);p.kind=r.getString(3);p.amount=r.getLong(4);p.payload=r.getString(5);p.note=r.getString(6);p.state=r.getString(7);p.createdAt=r.getLong(8);return p;}
    private Receipt receipt(UUID op,Map<UUID,Long> balances,boolean replayed){return new Receipt(op,Map.copyOf(balances),replayed);}

    private <T> T transaction(SqlWork<T> work){ensureOpen();try{connection.setAutoCommit(false);T value=work.run();connection.commit();connection.setAutoCommit(true);return value;}catch(Exception e){try{connection.rollback();connection.setAutoCommit(true);}catch(SQLException ignored){}if(e instanceof RuntimeException)throw (RuntimeException)e;throw failure((SQLException)e);}}
    private interface SqlWork<T>{T run()throws Exception;}
    private long checkedBalance(long current,long delta){try{long result=Math.addExact(current,delta);if(result<0||result>maxBalance)throw new IllegalStateException("Balance is outside the allowed range");return result;}catch(ArithmeticException e){throw new IllegalStateException("Balance overflow",e);}}
    private void checkAmountWithinMax(long value,String label){if(value<0||value>maxBalance)throw new IllegalArgumentException(label+" is outside the allowed range");}
    private static void checkPositive(long value,String label){if(value<=0)throw new IllegalArgumentException(label+" must be positive");}
    private static void checkUuid(UUID value,String label){if(value==null)throw new IllegalArgumentException(label+" is required");}
    private static void checkOp(UUID value){checkUuid(value,"operation id");}
    private static void checkName(String name){if(name==null||name.isBlank()||name.length()>64)throw new IllegalArgumentException("Account name is invalid");}
    private static void checkReason(String reason){if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("Reason is required");}
    private void rejectBlocked(UUID player)throws SQLException{if(isBlocked(player))throw new IllegalStateException("Account has an unresolved pending operation");}
    private void validatePrepareBalance(Kind kind, long amount, Account account) throws SQLException {
        if (kind == Kind.WITHDRAW || kind == Kind.NOTE_ISSUE) {
            long reserved = reservedOutgoing(account.id());
            if (amount > account.balance() - reserved) throw new IllegalStateException("Insufficient available balance");
        } else if (kind == Kind.DEPOSIT) {
            checkedBalance(account.balance(), amount);
        }
    }
    private static long now(){return Instant.now().toEpochMilli();}
    private static void setNullable(PreparedStatement s,int index,String value)throws SQLException{if(value==null)s.setObject(index,null);else s.setString(index,value);}
    private static void setNullableLong(PreparedStatement s,int index,String value)throws SQLException{if(value==null)s.setObject(index,null);else s.setLong(index,StoreJson.signed(value,"amount"));}
    private boolean operationAuditMatches(UUID op,String action,UUID actor,String reason)throws SQLException{try(PreparedStatement s=connection.prepareStatement("SELECT actor_id,reason FROM operation_audit WHERE op_id=? AND action=? ORDER BY id DESC LIMIT 1")){s.setString(1,op.toString());s.setString(2,action);try(ResultSet r=s.executeQuery()){if(!r.next())return false;String savedActor=r.getString(1);return (actor==null?savedActor==null:actor.toString().equals(savedActor))&&reason.equals(r.getString(2));}}}
    private void ensureOpen(){if(closed)throw new IllegalStateException("Storage is closed");}
    private static IllegalStateException failure(SQLException e){return new IllegalStateException("GoldBag SQLite operation failed",e);}

    private static final class PendingData { String operation,player,kind,payload,note,state; long amount,createdAt; }
    private static final class OperationState { String kind,fingerprint,state,player,payload,note; long amount; }
    private static final class NoteData { long amount; String status,issuer,issueOp,redeemOp; }
}
