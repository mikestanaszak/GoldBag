# T6 packaged verification automation

## Scope

This checkpoint adds a standalone Java verifier under `scripts/verification/`, wires it after Maven verification in both CI and the local PowerShell build helper, and stages only the canonical shaded plugin JAR, its SHA-256 checksum, and Maven test reports. No Bukkit class path is used by the verifier and no server/EULA step is included.

## Implemented

- `scripts/verification/PackagedJarVerification.java` accepts one JAR path and checks:
  - `plugin.yml` exists, declares `io.github.mikestanaszak.goldbag.plugin.GoldBagPlugin`, and targets API `1.17`;
  - manifest `Multi-Release: true` and `Implementation-Version` are present;
  - the plugin main class, relocated Gson/SnakeYAML/SLF4J packages, and SQLite native libraries are present;
  - no Bukkit classes are bundled and every base class has major version at most 60 (Java 16); `META-INF/versions/**` entries are excluded from that base check;
  - the shaded storage facade creates an account, adjusts its balance, closes/reopens the database, and preserves the issued-note liability and pending operation evidence;
  - packaged `OfflineImport` validate-only creates no destination and restore preserves the balance, issued note, pending evidence, and source export bytes.
- `.github/workflows/ci.yml` runs the verifier after `mvn -B verify` on Java 17 and Java 21. It requires exactly one case-sensitive `GoldBag-*.jar` excluding `-original.jar`, then uploads only `ci-artifacts/*.jar`, `ci-artifacts/*.sha256`, and `**/target/surefire-reports/**`.
- `scripts/Invoke-GoldBagBuild.ps1` runs the same verifier after local Maven verification, propagates Maven/compiler/verifier failures, and writes/prints `<jar>.sha256`. PowerShell selection is case-sensitive so stale lowercase shaded filenames cannot be mistaken for the canonical final artifact.

## Verification evidence

- Direct packaged run against the current artifact:
  `javac -encoding UTF-8 -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar -d <temporary-classes> scripts/verification/PackagedJarVerification.java`
  followed by `java -cp <temporary-classes>;goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar verification.PackagedJarVerification <absolute-jar>`: PASS. Reported 712 base classes, maximum major 60, 20 SQLite native entries, restart/dry-run/restore/source-preservation all OK.
- `pwsh -NoProfile -File scripts/Invoke-GoldBagBuild.ps1`: PASS. Maven verify completed with 12 core, 17 storage, and 37 plugin tests; packaged verification passed; checksum written to `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar.sha256`.
- The first local helper run intentionally exposed a Windows case-insensitive wildcard collision with a stale lowercase shaded JAR and failed before verification; the exact case-sensitive selection fix above was applied and the rerun passed.
- `git diff --check`: PASS for the automation edits. PowerShell parser check: PASS.

## Remaining

- Controller should include these owned files in the next documentation/build checkpoint and inspect the Java 17/21 CI result after push.
- No live Minecraft server or EULA-dependent smoke is included by design.

## Focused correction checkpoint

- Corrected offline validate-only sidecar assertions to use SQLite's `-wal` and `-shm` names.
- Corrected CI checksum generation to run inside `ci-artifacts`, so the checksum contains the downloaded JAR basename rather than a workspace-relative path.
- Wrapped the verifier fixture in `try/finally` and added best-effort recursive cleanup; cleanup warnings preserve the verification result while retaining the fixture path for diagnosis.
- Removed the unused behavior-check JAR argument and unused import.

Focused verification against the current `goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar`:

`javac -encoding UTF-8 -cp goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar -d .runtime/automation-check scripts/verification/PackagedJarVerification.java`

`java -cp .runtime/automation-check;goldbag-plugin/target/GoldBag-2.0.0-SNAPSHOT.jar verification.PackagedJarVerification <absolute-jar>`

PASS: 712 base classes, maximum major 60, 20 SQLite native entries, restart persistence, dry-run, restore, issued note, pending evidence, source preservation, and temporary fixture cleanup.

`git diff --check` and PowerShell parser validation also pass for the owned automation files. No full Maven run was repeated for this correction-only checkpoint.
