# GoldBag installation

GoldBag is a server-side Bukkit/Spigot plugin. Players do not install a client mod or resource pack. The plugin uses SQLite in its own data directory and does not need a MySQL server.

The compatibility matrix in [compatibility.md](compatibility.md) is the source of truth for tested server builds. A build that is not listed as verified must be treated as unverified, even if the JAR compiles.

## Install on a server

1. Stop the server cleanly and make a backup.
2. Choose a GoldBag JAR that is explicitly listed for the exact server build. Put it in the server's `plugins` directory. Do not put it in Fabric's `mods` directory, a vanilla server, or Minecraft Realms.
3. Start the server with the Java runtime required by that server build. Review the console for the GoldBag startup result.
4. Stop the server after first startup and review the generated GoldBag configuration and resource prices under `plugins/GoldBag/`.
5. Change settings only while the server is stopped unless the command documentation says a setting is reloadable. Keep the SQLite file and its journal companions together.
6. Start the server again, then perform the smoke checks in the compatibility checklist.

GoldBag's database is local to one running server. Do not put it on a network share or allow two server processes to open it. The first release has no legacy database importer; old handwritten banknotes have no trusted issuance identity and are not redeemable.

## Build locally

From the repository root, run:

```powershell
pwsh -File .\scripts\Invoke-GoldBagBuild.ps1
```

The helper checks Java and Maven, runs `mvn -B verify`, returns a failure exit code when verification fails, and prints the produced plugin JAR on success. It does not publish or deploy an artifact.

## Prepare a local test directory

Use an operator-supplied server JAR and the built plugin artifact:

```powershell
pwsh -File .\scripts\Prepare-GoldBagTestServer.ps1 `
  -ServerJar 'C:\artifacts\paper-1.21.1.jar' `
  -TargetDirectory 'C:\goldbag-test\paper-1.21.1' `
  -PluginArtifact '.\goldbag-plugin\target\GoldBag-2.0.0-SNAPSHOT.jar'
```

The helper copies the server to `server.jar` and the plugin to `plugins/GoldBag.jar`, writes a preparation marker, and stops. It never accepts the Minecraft EULA, creates an EULA file, launches a window, overwrites a different file, deletes worlds, or starts the server. A nonempty target requires `-AllowExisting` and a valid marker from an earlier preparation; differing destination hashes are rejected.

Use a target outside the GoldBag workspace. The helper rejects the repository and all directories beneath it so a preparation cannot collide with source files or build output.

Before the first launch, read and accept the server's EULA yourself if you choose to do so. Server provisioning, hosting purchases, and EULA decisions remain operator actions.
