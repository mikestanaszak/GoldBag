# T4 boundary review (frozen snapshot `65dd1b7`)

Scope: `.superpowers/sdd/2026-09-05-goldbag-rebuild/T4-boundary.diff` only, with small cross-call reads of the committed command/coordinator classes where needed to establish a named boundary. The active worker's uncommitted files were not used. No build was run; these are static findings against the committed snapshot.

## Findings

### P1 — Startup exceptions are swallowed, so a failed plugin can remain enabled

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:78-82`

`onEnable()` catches every startup exception, logs it, closes partial storage, and returns. Bukkit only automatically disables a plugin when enable throws (or when the plugin manager is explicitly asked to disable it); this path therefore leaves the plugin presented as enabled while `healthy` is false. Commands/listeners registered before the failing step can remain exposed and later hit null/unavailable paths instead of the plugin being disabled as required for invalid startup configuration.

Targeted regression: inject a failing `PluginConfig.load` or `SqliteStore` construction into a plugin harness and assert the plugin manager marks GoldBag disabled, no mutating command is usable, and partial executor/store resources are closed. Re-throw after cleanup or explicitly disable through the plugin manager.

### P1 — Storage preparation failures permanently leak the player guard

Files: `GoldBagPlugin.java:256-264`, `271-279`, `285-290`, and generic completion handling at `303-308`.

Deposit, withdrawal, and note issuance call `guard(player)` before submitting `prepare`. If `StorageExecutor.submit` returns an exceptional future (backend error, unresolved-account rejection, or a full queue), the generic error callback only sends the failure message; it never calls `unguard`. The player then remains blocked by `onClick`, `onDrag`, `onDrop`, and `onSwap` until quitting or restarting. A saturated bounded queue makes this reproducible without a database fault.

Targeted regression: make `prepare` fail and separately make the executor reject a submission, then assert the guard is released and a subsequent ordinary inventory event is not cancelled. Pass an operation-specific failure cleanup to the common submit path or release it in each exceptional completion branch.

### P1 — A quit can be followed by an asynchronous physical inventory mutation

Files: `GoldBagPlugin.java:161`, `209-218`, `253-279`, and `282-290`.

`onQuit` immediately removes the player guard. The deposit/withdraw/note callbacks check `isOnline` before calling asynchronous `markApplying`, but the main-thread callback after `markApplying` (for example lines 259-262 and 274-277) does not recheck online/dead state or inventory immediately before `remove`/`add`. A player can quit while `markApplying` is in flight; the queued callback then changes the offline `Player` inventory object after the guard has been removed. Note redemption has the same late check gap and never acquires a guard at all. This violates the required server-thread revalidation and can produce lost deposits or undelivered withdrawals across logout timing.

Targeted regression: defer `markApplying`, fire `PlayerQuitEvent`, then release the callback and assert no inventory mutation occurs and the durable operation remains cancellable or unresolved for recovery. Keep the guard until a terminal state and revalidate online/dead and the exact inventory evidence in the final main-thread callback; do not mutate an offline player.

### P1 — Creative and spectator restrictions are loaded but never enforced

Files: `GoldBagPlugin.java:195-219`, `221-249`, and `282-290`; the relevant defaults are `config.yml:11-12`.

The default `allow-creative` and `allow-spectator` settings are false, but no path checks `Player.getGameMode()` against those settings. Creative players can deposit arbitrary generated resources and withdraw them; creative/spectator players can issue or redeem notes as well. This is both a direct economy bypass and a violation of the default gameplay rule. `Settings.allowCreative()`/`allowSpectator()` must gate every resource exchange and note issue/redeem entry point, including command confirmation and right-click redemption.

Targeted regression: run deposit, withdrawal, note issue, and note redeem under CREATIVE and SPECTATOR with the default config and assert no journal operation or inventory mutation is created; repeat with each allow flag true to verify the configured opt-in.

### P1 — Confirmation does not recheck the quoted operation's permission

Files: `GoldBagPlugin.java:244-249`, `253-279`; cross-call entry is `GoldBagCommand.java:69-70` in the same committed snapshot.

`confirm()` accepts the current deposit/withdraw quote and dispatches directly to execution. The command dispatcher also does not require a permission for `confirm`, and `executeDeposit`/`executeWithdrawal` do not check `goldbag.deposit`/`goldbag.withdraw` again. A player can obtain a quote while permitted, lose that permission, and still consume/receive items by confirming before expiry. The specification requires permission revalidation at confirmation.

Targeted regression: create a quote with the permission granted, revoke the modern node while retaining the quote, call confirm, and assert no `prepare` call or inventory change; verify a legacy grant is still accepted only when the modern node is not explicitly denied.

### P1 — `isPlain` accepts non-default item components and can credit custom items

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/InventoryAdapter.java:63-68`

The check rejects display name, lore, enchantments, custom model data, and PDC only. It accepts other item metadata/components such as item flags, unbreakable/damage state, attribute modifiers, and other component data. A custom raw-resource stack carrying one of those components therefore passes `count`/`remove` and is exchanged for currency, contrary to the explicit “other non-default item components” rejection rule.

Targeted regression: construct each configured material with an otherwise ordinary `ItemMeta` carrying an item flag, unbreakable state, damage, or attribute modifier and assert `isPlain`, `count`, and deposit selection reject it. Compare against a pristine `new ItemStack(expected)` metadata baseline rather than maintaining a partial deny list.

### P1 — Startup pending-operation read failure is ignored while mutations become healthy

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:73-76`

`healthy` is set true before the asynchronous `pending()` check. Its completion callback only logs when `error == null`; a failed read is silently ignored and the plugin continues accepting commands and inventory mutations. The storage contract requires database failures to stop mutations and report temporary unavailability, and restart recovery must account for pending operations before releasing affected accounts.

Targeted regression: make the first `pending()` call fail during enable and assert the plugin remains unavailable (or fails enable) and reports the storage error; only set healthy after the check succeeds and pending records are represented as blocked/recovery state.

### P2 — Reload performs configuration file I/O on the game/command thread

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:137-147`.

`reloadConfiguration` calls `PluginConfig.load` synchronously from the command handler. `PluginConfig.load` opens and parses all three files, including `YamlConfiguration.loadConfiguration`, on that thread. A slow filesystem or large/malformed file can stall the server, contrary to the requirement that database/file I/O stay off the game thread. Parse a candidate snapshot on the bounded executor, then atomically apply it on the main thread only after validation succeeds; keep the old snapshot on any error.

Targeted regression: use a blocking reader/file fixture and assert the command returns control to the server thread while loading, then verify a failed candidate leaves the previous `PluginConfig` and quote revision unchanged.

### P2 — Message YAML is not validated atomically

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/PluginConfig.java:24-34`.

`YamlConfiguration.loadConfiguration(File)` is a forgiving loader: missing or malformed message files can produce an empty/partial configuration rather than throwing. The loader also accepts any scalar coercion returned by `getString` and never validates expected message keys. Consequently a malformed `messages.yml` can make reload succeed and replace the active snapshot, despite the all-or-nothing configuration requirement; the fallback strings hide the failure. Use a strict parser/required-file check and reject malformed message mappings before swapping the snapshot.

Targeted regression: delete or corrupt `messages.yml`, call `PluginConfig.load`/reload, and assert an error with the old snapshot still active; include a non-string value and duplicate key case.

### P2 — Shutdown interrupts and discards storage work without an orderly drain

Files: `GoldBagPlugin.java:111-115` and `StorageExecutor.java:24-38`.

`closeStorage()` calls `shutdownNow()` and immediately closes the SQLite store. Queued task wrappers are discarded without completing their `CompletableFuture`s, and an active JDBC call can be interrupted while the store is closed underneath it. If an inventory change has happened and its `complete` task is queued, the user receives no completion callback and the operation is left to an unreported recovery state; if a call is in-flight, close/interrupt ordering makes the outcome timing-dependent. Shutdown should stop accepting work, drain or explicitly cancel queued tasks, await the writer, then close the store while preserving/reporting any APPLYING operations.

Targeted regression: hold a prepare/complete task at each journal stage, invoke `onDisable`, and assert no task uses a closed store, every returned future is resolved/cancelled, and physical operations remain represented in `pending()` for restart recovery.

### P2 — The bounded executor has no timeout/deadline for a stuck storage call

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/StorageExecutor.java:14-38`.

The queue is bounded, but the single worker has no execution timeout. A blocked SQLite call consumes the only worker indefinitely; only after 64 additional submissions are queued/rejected do callers see the busy error. This does not meet the task/spec requirement for bounded queues and timeouts and can leave an operation waiting without a user-visible response. Add a bounded operation deadline/cancellation policy and surface timeout as temporary unavailability while retaining any durable pending record.

Targeted regression: block the worker beyond the configured deadline, assert the request reports busy/unavailable within that deadline, and verify the operation is either never prepared or remains explicitly recoverable.

### P2 — `legacy-aliases: false` has no effect

Files: `goldbag-plugin/src/main/resources/plugin.yml:7-18`; cross-call alias parsing is `GoldBagCommand.java:42-48`.

The descriptor always registers `gb`/`purse` and the compatibility command names, and the dispatcher always translates them. `Settings.legacyAliases()` is loaded but never consulted by registration or parsing. An operator who disables compatibility aliases still exposes the legacy command surface and potential command conflicts.

Targeted regression: load a config with `legacy-aliases: false` and assert `/gb`, `/purse`, `/balance`, `/money`, `/pursetop`, and legacy `/withdraw` are unavailable (while `/goldbag` remains available); assert all are wired when true.

### P2 — Valid note redemption depends on Bukkit object identity

File: `goldbag-plugin/src/main/java/io/github/mikestanaszak/goldbag/plugin/GoldBagPlugin.java:211-216`.

The callback accepts the operation only when `player.getInventory().getItemInMainHand() != item` is false. Bukkit's public inventory API does not guarantee that two reads return the same `ItemStack` object; an equal stack may be a copy/wrapper. A still-present valid note can therefore be treated as moved and its prepared operation cancelled. Compare the slot's current material, amount, and note ID/value evidence, then consume exactly that slot under an inventory guard rather than relying on Java reference identity.

Targeted regression: use an inventory double that returns distinct but equal `ItemStack` instances for the same main-hand slot and assert redemption proceeds once; move/drop/change the slot and assert cancellation without credit.
