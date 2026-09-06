# T6 validation record

This is an incremental record, not a completed server-release certificate. The final automated result is recorded at the end; older checkpoints are historical.

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

## Latest implementation checkpoint `904e67f`

- Controller `mvn -B verify`: PASS, 63 tests (12 core, 17 storage, 34 plugin), zero failures/errors/skips.
- GitHub Linux Java 17/21 verification: PASS at the same source checkpoint, run `34007572136`.
- Both standalone packaged-JAR smoke commands above passed again. The offline CLI preserved the balance, issued note, unresolved pending operation/evidence, and original export bytes; its dry run created no destination.
- Artifact SHA-256: `F9B0F5F1ED777EB77E52A0A24C51B5B0272537039E6FF34D2AD8E8CFAA728388`.
- Final header inspection: 712 base classes, maximum major version 60 (Java 16); multi-release overrides excluded. This is a bytecode check, not a server compatibility claim.
- Final scoped Luna review dispositions are pending because both reviewers hit the usage limit. Exact continuation briefs are in `T5-review.md`. The artifact remains a development build; no actual Minecraft server behavior or version compatibility is certified.

## Final automated checkpoint `39c93b7`

- Production fixes at `5b2efb8` and `51238d8` independently cleared by the other Luna reviewer. Automation independently cleared in `T6-automation-review.md`. No open findings remain in those scoped reviews.
- Controller `mvn -B clean verify`: PASS, 66 tests (12 core, 17 storage, 37 plugin), zero failures/errors/skips. Environment: Windows, Java 21.0.11, Maven 3.9.11.
- Controller compiled `scripts/verification/PackagedJarVerification.java` against only the shaded JAR and ran it with `.runtime/final-verifier` plus that JAR on the classpath: PASS. Worker also verified the PowerShell build helper end to end and parsed its final script successfully.
- Archive: 712 base classes, maximum major 60 (Java 16), 20 native SQLite libraries, expected main class/descriptor/manifest, relocated dependencies, and no bundled Bukkit classes.
- Packaged behavior: real SQLite restart persistence, CLI validate-only with no destination/SQLite sidecars, empty-destination restore preserving account balance, issued banknote, pending evidence/account quarantine, unchanged source JSON, and temporary fixture cleanup all passed.
- GitHub Linux Java 17 and 21 both passed Maven and packaged verification at this same source: [run 34008700654](https://github.com/mikestanaszak/GoldBag/actions/runs/34008700654). Both uploaded canonical shaded JARs, portable SHA-256 files, and test reports. Build artifacts are retained for 14 days; source remains on the feature branch and can reproduce them.
- Local artifact: `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`; SHA-256 `43377E3C0C2E8AAB9F5AB22F4DD65A38B0353ED48363683CEFBC2F0ACBB4AE0E`, saved beside it as `.jar.sha256`.
- Original source/history retained; `main` unchanged. Feature-branch source and review checkpoints pushed. Final documentation-only handoff follows this checkpoint.
- Repeat automatic verification with `pwsh -File scripts/Invoke-GoldBagBuild.ps1`. Actual server startup, inventory behavior, and exact-version compatibility remain unverified until the operator supplies a server and makes the EULA decision. The test-server preparation helper and compatibility checklist are ready.
