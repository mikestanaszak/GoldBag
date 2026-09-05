# Compatibility matrix and checklist

This matrix starts unverified. Compilation and a successful Maven build do not prove that GoldBag runs on a Minecraft server. Add a row only after the exact server build, Java runtime, platform, plugin JAR, startup result, catalog validation, exchange, note, recovery, restart, and shutdown checks have been recorded.

| Minecraft server | Platform/build | Java runtime | GoldBag artifact | Status | Evidence |
| --- | --- | --- | --- | --- | --- |
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

The matrix must be updated with evidence by the integration/server-validation owner. Until then every row remains unverified.
