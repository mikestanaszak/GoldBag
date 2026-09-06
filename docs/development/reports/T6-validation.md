# T6 validation record

This is an incremental record, not a completed server-release certificate. Recheck the final artifact after T4 and storage review fixes.

## Foundation checkpoint (before Bukkit adapter)

- Source through `82306c4`, Java 21.0.11, Maven 3.9.11, Windows 11.
- `mvn -B verify`: PASS; 12 core tests and 6 SQLite storage tests, no test failures/errors. Shading still reported overlapping module descriptors/manifests/license resources; controller has prepared a POM cleanup to verify at next package build.
- GitHub Linux `mvn verify` passed on Java 17 and 21 at storage checkpoint `946b731` (run `33979161347`). These are build/runtime tests, not Minecraft server tests.
- Standalone source-mode Java smoke against the shaded JAR: PASS. Loaded relocated libraries, used native SQLite, credited 25 raw iron as 5000 cents, closed/reopened the account, exported JSON, and restored the same balance into an empty database. Local throwaway verification source: `.runtime/ShadedSmoke.java`.
- Inspected class-file headers for all 670 base classes in the foundation shaded JAR: none require a class major version above 60 (Java 16). Multi-release classes were excluded from the base-class check. This is a bytecode check, not proof of behavior on Java 16 or on all Minecraft releases.
- At this checkpoint the JAR has no completed Bukkit adapter; it is not installable as a working plugin. Do not distribute it as a release.

## Required final checks

- Finish T2 fixes and independent re-review, then T4 and its independent review.
- Rebuild final artifact and repeat tests/packaged dependency smoke after source changes.
- Inspect actual `plugin.yml`, main class, shaded native libraries, and artifact bytecode compatibility.
- Run exact server-build startup/inventory scenarios only when operator terms are accepted. Keep untested matrix entries unverified.
- Update STATUS with artifact hash, test counts, unresolved findings, and the next exact recovery step.

## Integrated checkpoint `05e7ab4`

- Controller ran `mvn -B verify` on Java 21.0.11 / Maven 3.9.11: PASS, 58 tests (12 core, 17 storage, 29 plugin), zero failures/errors/skips. This includes inventory-plan doubles and real SQLite coordinator tests, not a running Minecraft server.
- Shaded artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`, SHA-256 `173D21E369DDADCC642738568F0343E2327B05C47FAC2D678B0B0A9988949DB1`. Later source fixes require rebuilding and a new hash.
- `java -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar .runtime/ShadedSmoke.java`: PASS for relocated libraries, native SQLite, restart persistence and restore.
- `java -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar .runtime/PackagedCliSmoke.java`: PASS for the packaged offline entrypoint, dry-run leaving no destination, actual restore preserving balance, issued banknote, unresolved pending evidence, and unchanged source JSON. No Bukkit API on the classpath was required for these standalone checks.
- ZIP/header inspection: all 710 base class files have major version at most 60 (Java 16); no `org/bukkit/` classes bundled; 20 native SQLite files included; offline entrypoint present. Multi-release overrides were excluded from the base-header count.
- Descriptor resolves version `2.0.0-SNAPSHOT`, main class `io.github.mikestanaszak.goldbag.plugin.GoldBagPlugin`, API `1.17`. Manifest contains `Multi-Release: true` and the expected implementation version.
- Package cleanup removed module-descriptor/license overlap warnings. The remaining warning is the expected input-manifest overlap; the output manifest was inspected above.
- GitHub Linux Java 17/21 verification passed at `7367d29` (run `34006088585`). Newer checkpoint CI is pending. Earlier WIP `079950a` had an intermediate guard compile error, fixed before `7367d29`.
- Player-flow and physical-flow review fixes remain; this artifact is a development checkpoint, not a verified server release. No EULA accepted or server started.
