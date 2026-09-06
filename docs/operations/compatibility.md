# Compatibility matrix and checklist

Compilation and a successful Maven build do not prove that GoldBag runs on a Minecraft server. The exact Paper build below has a recorded automated smoke pass. Broad version rows remain unverified, and the extended fault scenarios below are still pending.

| Minecraft server | Platform/build | Java runtime | GoldBag artifact | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
| 1.21.11 | Paper build 132, c5eb079 | Temurin 21.0.11+10, Windows 11 amd64 | GoldBag 2.0.0-SNAPSHOT, runtime source 37fb85f, packaging 63331a0; SHA C48C54EC…D9785535 | Automated smoke PASS; extended validation pending | [Server/recovery/restart evidence](../development/reports/T6-server-validation.md), [16 player checks](../development/reports/T6-player-server-tests.md) |
| 1.17.x | Spigot exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.17.x | Paper exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.18.x | Spigot exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.18.x | Paper exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.19.x | Spigot exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.19.x | Paper exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.20.x | Spigot exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.20.x | Paper exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT: unverified | Unverified | No server test recorded |
| 1.21.x | Spigot exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT.jar: unverified | Unverified | No server test recorded |
| 1.21.x | Paper exact build: unverified | Required Java: unverified | GoldBag 2.0.0-SNAPSHOT.jar: unverified | Unverified | No server test recorded |

The target is Spigot/Bukkit public API 1.17 with Java 16-compatible bytecode. Paper may be recommended after testing, but a Paper result does not certify Spigot. Future releases and snapshots are unverified. Fabric and Forge use a different mod system; vanilla servers and Realms cannot load this plugin. Client protocol compatibility is a server capability and is separate from GoldBag server compatibility.

## Required evidence for a verified row

- Record the exact server distribution and build identifier, Java version, operating system, GoldBag JAR checksum, and clean startup/shutdown logs.
- Confirm the full default catalog is available at that server version; unsupported materials must cause a clear diagnostic rather than partial activation.
- Exercise account creation, restart persistence, deposit, withdrawal, payment, banknote issue/redemption, top balances, reload validation, and storage export.
- Exercise custom-item rejection, full inventory, insufficient funds, duplicate confirmation, disconnect/death during an exchange, pending-operation listing, and audited recovery.
- Confirm no required behavior depends on a Paper-only API when testing Spigot.
- Record failures and unavailable server builds instead of silently broadening the supported range.

The exact Paper smoke included startup/shutdown, all 18 resources, deposits/withdrawals/payments, main/offhand notes and copy rejection, custom/full-inventory/ingot/creative rejection, menus, reload validation, synthetic recovery apply/cancel and account blocking, restart persistence, and actual server export/restore.

Still unverified: forced crashes at each inventory/journal stage, disconnect/death during APPLYING, interactions with other permission/protection plugins, and other server builds/platforms. Synthetic recovery records exercised operator accounting; they do not substitute for hard-crash inventory-persistence evidence.
