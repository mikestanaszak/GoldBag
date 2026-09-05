# GoldBag Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkboxes for tracking. The user explicitly selected gpt-5.6-luna parallel subagents.

**Goal:** Build a resumable, tested SQLite-backed GoldBag plugin preserving the approved resource economy.

**Architecture:** Maven reactor with independent pure-Java core and SQLite storage modules, plus a Bukkit/Spigot adapter plugin. Immutable catalog snapshots and integer currency flow through a durable operation journal before physical inventory changes. Operations documentation and automation are independent of production modules.

**Tech Stack:** Java 16 bytecode (development JDK 21), Maven, Spigot API 1.17, SQLite JDBC, SnakeYAML, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-05-goldbag-design.md`

## Global constraints

- SQLite only. No MySQL, smelted ingots/refined metal substitutes, interest, Fabric, NMS, or Paper-only required API.
- One shared balance per player UUID; exact integer cents; maximum 100000000000 cents by default.
- Target public Spigot API 1.17 and all feasible newer server versions; publish only tested compatibility.
- Original resource prices stay fixed; diamond 5000, emerald 2500, quartz 100, amethyst 50 cents. Blocks derive nine times base.
- Every task persists its report incrementally; controller alone commits. Do not overwrite other workers' files.
- APIs below are contracts. Report any necessary change before changing it; consumers use exact signatures.

## T0: Build and recovery foundation (controller)

Files: root `pom.xml`, module POMs, `.gitignore`, `AGENTS.md`, `RESUME.md`, STATUS, this plan.

- [x] Create Maven modules `goldbag-core`, `goldbag-storage`, `goldbag-plugin`; use `io.github.mikestanaszak` group and `2.0.0-SNAPSHOT` version.
- [x] Establish JUnit, compiler release 16, shade packaging in plugin, reproducible build timestamps and UTF-8.
- [x] Run `mvn -B validate` and checkpoint the plan/recovery files before dispatch.

## T1: Money, catalog, configuration (independent Luna worker)

Own `goldbag-core/src/**` and `docs/development/reports/T1-core.md`. No Bukkit dependency.

Produce package `io.github.mikestanaszak.goldbag.core`:

```java
public final class Money {
  public static long parse(String text); // nonnegative decimal, max 2 places, no exponent
  public static long positive(String text); // strictly positive
  public static String format(long cents); // e.g. "50.00"
}
public record Resource(String id, String material, long depositPrice, long withdrawPrice,
                       boolean depositEnabled, boolean withdrawEnabled) {}
public final class Catalog {
  public static Catalog defaults();
  public static Catalog load(java.io.Reader yaml);
  public java.util.List<Resource> resources();
  public Resource require(String materialOrAlias);
  public long depositValue(String material, int count);
  public long withdrawalCost(String material, int count);
  public int maximumWithdrawal(String material, long balance, int capacity, int limit);
}
public record Settings(long maxBalance, int quoteTimeoutSeconds, int maxItemsPerTransaction,
  boolean allowCreative, boolean allowSpectator, boolean shortcutEnabled,
  boolean banknotesEnabled, boolean legacyAliases, String databaseFile,
  String currencyName, String currencySymbol) {
  public static Settings defaults();
  public static Settings load(java.io.Reader yaml);
}
```

- [x] Write JUnit behavior tests first; examples: `assertEquals(5000, Catalog.defaults().depositValue("raw_iron",25));`, `assertEquals(200, Money.parse("2.00"));`, `assertThrows(IllegalArgumentException.class, () -> Money.parse("1.001"));`.
- [x] Implement exact overflow-safe arithmetic, normalized material IDs/aliases, immutable catalog, explicit unknown/disabled-resource errors, counts >0, no rounding.
- [x] Implement SnakeYAML safe parsing and structural validation. Reject duplicate keys/mappings, unknown settings, invalid prices, prohibited ingots/nuggets/refined blocks, incompatible storage block pairings, non-nine ratios, invalid precision, and withdrawal prices below deposits when both directions are enabled. Only known reversible nine-to-one pairs can define storage blocks. Prices must be quoted decimal strings, not YAML numeric scalars. Identifiers/paths/display fields must be strings, and present-null sections are malformed. Restrict databaseFile to a simple filename; preserve currencyName/currencySymbol for the plugin. Default absent optional settings; reject unknown keys so typos do not silently change behavior.
- [x] Ship `defaults/config.yml`, `defaults/resources.yml`, and `defaults/messages.yml` in core resources, matching spec. Messages should have stable keys for common results; plugin may add keys as needed.
- [x] Run `mvn -B -f goldbag-core/pom.xml test`, report red/green evidence, actual API and remaining risks.

## T2: SQLite economy and durable journal (independent Luna worker)

Own `goldbag-storage/src/**` and report `T2-storage.md`. No dependency on core or Bukkit; units are validated long cents.

Produce package `io.github.mikestanaszak.goldbag.storage` with `SqliteStore implements AutoCloseable`. Constructor `SqliteStore(Path database, long maxBalance)` opens/migrates schema and acquires exclusive process/file ownership. All public methods serialized; no async executor inside store (caller owns thread scheduling). Nested records/enums:

```java
record Account(UUID id, String name, long balance, long revision) {}
record Receipt(UUID operationId, Map<UUID,Long> balances, boolean replayed) {}
enum Kind { DEPOSIT, WITHDRAW, NOTE_ISSUE, NOTE_REDEEM }
record Pending(UUID id, UUID playerId, Kind kind, long amount, String payload,
               UUID noteId, String state) {}
record Note(UUID id, long amount, String status) {}
```

Exact public API:

```java
Account ensureAccount(UUID id, String name);
Optional<Account> account(UUID id);
Optional<Account> findAccount(String nameOrUuid); // reject ambiguous names
List<Account> top(int page); // 1 based, 10 rows, stable UUID tiebreak
Receipt adjust(UUID op, UUID actor, UUID target, long delta, String reason);
Receipt setBalance(UUID op, UUID actor, UUID target, long amount, String reason);
Receipt transfer(UUID op, UUID from, UUID to, long amount);
Pending prepare(UUID op, UUID player, Kind kind, long amount, String payload, UUID noteId);
Pending prepareRedemption(UUID op, UUID player, UUID note, String payload);
void markApplying(UUID op);
Receipt complete(UUID op);
void cancelPrepared(UUID op, String reason); // only before APPLYING
Receipt resolve(UUID op, boolean apply, UUID actor, String reason); // audited operator resolution
List<Pending> pending();
boolean isBlocked(UUID player);
Optional<Note> note(UUID id);
String exportJson(); // consistent snapshot, amounts as decimal strings
void importJson(String json); // only empty destination, validate all, transactional
```

- [ ] Write real temporary-file SQLite tests first. Example: create A/B, credit A 5000, transfer 2000, expect A=3000/B=2000 and replay same UUID changes nothing. Insufficient funds leaves both unchanged.
- [ ] Create constrained accounts/operations/entries/notes/pending/schema tables; reject newer schemas. Transactions include idempotency and request-fingerprint matching: same UUID with different request is an error.
- [ ] Implement exact adjustments/payments, known account resolution, bounded amounts, stable leaderboard, and complete audit reasons. All mutations reject affected accounts with unresolved pending operations.
- [ ] Prepare journal holds account and note exclusivity without applying balances. WITHDRAW/NOTE_ISSUE reserve affordability; DEPOSIT/NOTE_REDEEM check max balance. NOTE_ISSUE requires fresh note ID; redeem amount comes from persisted issued note, never client input. markApplying before physical change; complete commits balance and note state atomically; no automatic replay of ambiguous physical steps. Cancellation only PREPARED; APPLYING requires audited resolve. Repeating finalized operations returns their prior receipt and never mutates again.
- [ ] Persist close/reopen state. Tests cover duplicate note redemption, mismatched UUID reuse, unresolved account blocking, two-process-owner rejection, rollback, overflow, export/import with pending records and notes, and malformed imports causing no partial writes.
- [ ] Use JSON via Gson, validating IDs/relationships/sums/states and string integer amounts. Support empty-destination restore, with complete rollback on validation failure.
- [ ] Run `mvn -B -f goldbag-storage/pom.xml test`; report test evidence and exact recovery semantics.

## T3: Operations and reproducible checks (independent Luna worker)

Own `.github/**`, `scripts/**`, `docs/operations/**`, report `T3-operations.md`. Do not edit root POM/README/STATUS.

- [x] Add CI build with Java 17 and 21 (bytecode release 16), `mvn -B verify`, test report retention and plugin artifact upload. No automatic release or deployment.
- [x] Create PowerShell build/check helper that checks Java/Maven, runs verify, propagates failure status, and prints produced plugin JAR path without swallowing failures.
- [x] Create safe local test-server preparation helper taking an operator-supplied server JAR, isolated target directory, and plugin artifact. Do not accept EULA, overwrite worlds, delete files, or launch visible background windows. Reject source/destination collisions; refuse nonempty target directory unless explicitly safe and idempotent.
- [x] Write installation, commands/permissions, backup/JSON restore, recovery, and compatibility checklist docs. Clearly distinguish Java server versions from client protocol support. Matrix starts as unverified; no fabricated server tests.
- [x] Test helpers against missing tools/files/unsafe paths where practical, and document exact checks. Do not test human prose with source-string assertions.

## T4: Bukkit adapter and playable plugin (Luna, after T1/T2)

Own `goldbag-plugin/src/**` and report `T4-plugin.md`. Consume exact core/storage interfaces above; shared POM edits requested from controller.

- [ ] Build plugin lifecycle with async serialized bounded storage executor, startup catalog/material/runtime validation, existing account initialization on join, orderly close, status failures, reload all-or-nothing, and default file generation.
- [ ] Register traditional plugin.yml API 1.17 and canonical commands, optional compatibility aliases, permissions (modern explicit deny wins over legacy grants). Console can administer/query but not mutate inventory. Write tests for parser/permission/quote logic first.
- [ ] Add main menu, catalog/pagination, deposit selection, quantity preview/confirmation, rates, max-count capacity, payments, notes, leaderboard, and admin operations. Keep player items in inventory during previews; expire/cancel quotes and invalidate on reload. Use plugin InventoryHolder sessions, not titles.
- [ ] Implement inventory adapter and durable exchange coordinator. Reserve a session on main thread before async prepare, lock relevant inventory paths during operation, persist APPLYING before changing inventory, then complete asynchronously. On late/uncertain results keep affected accounts quarantined and report operation ID. Never mutate inventories from DB thread; reject offline/dead/changed state before mutation. Do not roll back unrelated slots. Scan only main 36 slots and reject custom metadata. Note issuance/redeem use PDC UUID and authoritative store record; copied notes cannot credit twice.
- [ ] Provide `/goldbag recovery list` and `/goldbag recovery resolve <op> <apply|cancel> <reason>` for operators/console, clearly identifying ambiguity and resulting balance action. Command resolves database accounting after operator checks inventory, without silently restoring inventory snapshots.
- [ ] Add storage export and offline import entrypoint or helper integration. Export path fixed under plugin data folder; avoid arbitrary player-controlled paths.
- [ ] Test pure coordinator state transitions using controlled scheduler/inventory doubles and real SQLite; include delayed callback, cancellation, offline/death, changed inventory, full stacks, drag/hotbar/offhand, note-copy redemption and backend exceptions. Use real server smoke checks separately for Bukkit behavior.
- [ ] Run `mvn -B verify`; save remaining in-game validation steps if server cannot run without user-owned EULA acceptance.

## T5: Review and integration fixes (fresh Luna reviewer)

- [ ] Review T1/T2/T3/T4 diffs against their tasks and spec. Report concrete priority findings with file/line and reproduction, focusing on duplicate money, custom item acceptance, async races, loss/duplication, invalid config, missing required behavior, restore integrity, and runtime compatibility.
- [ ] Route each fix to its owner with regression test and persist report. Re-review only fixes. Do not mark open important findings complete.
- [ ] Write root README with actual implemented capabilities, build instructions, limitations, and durable task links; archive original source explicitly without losing Git history.

## T6: Verification and checkpoint

- [ ] Run full `mvn -B verify`, inspect shaded artifact, plugin descriptor, dependency bytecode, and repository diff.
- [ ] Prepare local server smoke validation and run it only where terms are already accepted. Record exact server build/Java/plugin result; untested stays unverified.
- [ ] Update STATUS with tests, artifact location, open work, next exact command and commits. Create a feature-branch checkpoint; never merge main automatically.
- [ ] Report resumable state and any limits honestly. No claim of complete broad compatibility until matrix evidence exists.

## Preflight interface/ownership review

| Tasks | Shared boundary | Resolution |
|---|---|---|
| T1/T2 | Money long cents only | Independent; store validates its own numeric boundaries |
| T1/T4 | Money, Catalog, Settings | Exact signatures above; core publishes defaults under defaults/ |
| T2/T4 | SqliteStore and nested records | Exact signatures above; plugin owns async scheduling |
| T3/T0 | Artifact and Maven module paths | Fixed plugin module path, controller owns POMs |
| T3/T4 | Runtime documentation | Ops docs label unimplemented/unverified until integration |
| T5/T1–T4 | Review fixes | Controller routes fixes; reviewer does not mutate concurrently |
| T0–T6 self-check | Requirements/tests/files | Tasks have disjoint ownership; integration waits for interfaces; no compatibility claim from compilation |
