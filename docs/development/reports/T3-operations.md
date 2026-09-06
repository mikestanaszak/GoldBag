# T3 operations report

Status: T3 implementation complete; round-one review fix applied and verified. Reactor integration remains pending. No Git staging or commit was performed by this worker.

## Scope delivered

- `.github/workflows/ci.yml`: Maven `verify` on Temurin Java 17 and 21, test-report retention, and plugin JAR artifact retention. Pushes that change only documentation/recovery metadata are ignored to avoid redundant builds; pull requests still run. There is no release or deployment step.
- `scripts/Invoke-GoldBagBuild.ps1`: checks Java and Maven, runs `mvn -B verify`, propagates failures, and prints the plugin JAR path.
- `scripts/Prepare-GoldBagTestServer.ps1`: prepares an isolated directory from operator-supplied server/plugin JARs with collision, ancestor reparse-point, nonempty-target, and hash checks. It validates target and source ancestors before any filesystem mutation. It does not accept the EULA, create `eula.txt`, delete files, overwrite differing files, or launch a server.
- `docs/operations/installation.md`, `commands-and-permissions.md`, `backup-and-restore.md`, `recovery.md`, and `compatibility.md`: installation, command/permission, backup/JSON restore, uncertain-operation recovery, local test-server, and compatibility guidance. Compatibility rows intentionally remain unverified.

## Verification commands and outcomes

The following fresh fixture checks have been run by this worker:

| Check | Command | Outcome |
| --- | --- | --- |
| Missing-tool build failure | `pwsh -NoProfile -File .\scripts\Invoke-GoldBagBuild.ps1 -RepositoryRoot <fixture-with-pom> -MavenCommand __missing_mvn__` | Exit 1; reports the missing Maven tool |
| Maven failure propagation | Same helper with a fixture `mvn-fail.cmd` returning 7 | Exit 7; reports Maven verify failure without collapsing the code |
| Safe prep rejects missing server JAR | `pwsh -NoProfile -File .\scripts\Prepare-GoldBagTestServer.ps1 -ServerJar <missing> -TargetDirectory <temp> -PluginArtifact <fixture>` | Exit 1; no target created |
| Safe prep rejects nonempty unmarked target | Same helper with a pre-existing file and no marker | Exit 1; reports no files changed |
| Fresh and idempotent prep | Same helper with two fixture JARs, then `-AllowExisting` rerun | Both exit 0; destination hashes match; no `eula.txt` |
| Workspace target rejection | Same helper with `-TargetDirectory` set to the repository | Exit 1; reports workspace refusal |
| Source/destination collision | Same helper with target containing source fixture | Exit 1; reports collision |
| Differing artifact protection | Fresh prep, change source plugin bytes, rerun with `-AllowExisting` | Exit 1; destination remains unchanged |
| Differing server protection | Fresh prep, change source server bytes, rerun with `-AllowExisting` | Exit 1; destination remains unchanged |
| Target junction ancestor regression | Temporary junction ancestor with missing target child | Exit 1; no files created through junction |
| Source junction ancestor regression | Temporary junction ancestor in source path | Exit 1; target not created |
| Symbolic-link ancestor regression | Same fixture using a directory symbolic link | Not runnable here; Windows symbolic-link creation was unavailable; junction coverage exercises the same `ReparsePoint` guard |
| PowerShell syntax parse | `Parser::ParseFile` for both helper scripts | No parse errors |
| Build verification | `mvn -B verify` | Pending integration; controller owns reactor build |

## Round-one review fix

The review reproduced a target path whose existing ancestor was a junction into the workspace. Before the fix, the helper exited 0 and copied files through the junction into its real destination because only the final target item was checked. The helper now walks every existing target and source ancestor with literal paths and rejects `ReparsePoint` components before resolving sources or creating directories. A controlled junction regression now exits 1, creates no target, and leaves the junction destination untouched. A source-path junction regression also exits 1 before target creation. Directory symbolic-link creation was unavailable in this Windows test environment; the same guard covers symbolic links because they expose the same `ReparsePoint` attribute.

Final fresh verification: both helper scripts parse successfully; target-junction regression exits 1 with `prepared_through_junction=False`; normal preparation and `-AllowExisting` rerun both exit 0; no `eula.txt` is created.

## Integration API and assumptions

- The build helper expects the reactor root `pom.xml`, module `goldbag-plugin/target`, and a shaded artifact named `GoldBag-*.jar`.
- The CI workflow assumes the controller's Maven POM keeps compiler release 16 and the three-module reactor paths.
- The preparation helper creates only `server.jar`, `plugins/GoldBag.jar`, `.goldbag-prepared.json`, and the `plugins` directory in a new target. It leaves EULA and server launch decisions to the operator.
- Before any filesystem mutation, the preparation helper walks existing target and source ancestors with literal paths and rejects `ReparsePoint` components. Normal paths remain supported; target reruns require the explicit marker and matching hashes.
- The compatibility matrix must be updated only by the integration/server-validation owner after exact server evidence exists.

## Remaining work

The controller should run the full reactor `mvn -B verify` after T1/T2/T4 integration and inspect the shaded artifact. Update the compatibility matrix only after exact server evidence exists; do not mark server compatibility verified from compilation alone.
