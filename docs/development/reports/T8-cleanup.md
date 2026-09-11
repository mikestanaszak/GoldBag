# T8 Superseded source and build cleanup

## Brief and scope

User requested removal of the original files and old artifacts. Remove the unused root `java/` and `resources/` trees (eight Java files and four resource files), clear generated Maven outputs, rebuild the current modules, and update the existing README PR. Preserve Git history, current source, saved reports, test-server data, and recovery evidence.

Controller owns removals, README/STATUS, this report, verification, and Git integration. Luna owns only `T8-cleanup-review.md`; no nested agents or worker commits.

## Initial evidence

- `git status --short`: clean before cleanup; branch `codex/goldbag-rebuild`.
- `gh pr view 2 --json state,headRefOid,url`: README PR #2 is open.
- Inspected all legacy files and root/module POMs. Maven uses only the three `goldbag-*` modules and their standard source/resource paths.
- Searched for old package names, SQL/default-value files, and source-directory overrides. Only immutable original-source links in the accepted specification refer to those files outside the legacy directories.
- Found stale `goldbag-plugin-2.0.0-SNAPSHOT-shaded.jar` alongside the current package. Clear all generated module targets before verification.
- `.runtime` contains saved database/export and validation evidence, so it is retained.

## Verification and remaining work

- Removed the 12 tracked legacy files individually with `apply_patch`, then removed their verified-empty directories. A bulk recursive cleanup command was rejected by the tool policy before execution; individual tracked edits and Maven's standard clean lifecycle completed the cleanup instead.
- `mvn -B clean verify`: PASS, 69 tests (12 core, 17 storage, 40 plugin); all three module targets regenerated. Absolute target paths were inspected and contained no reparse points before clean.
- `javac -encoding UTF-8 -cp <plugin-jar> -d goldbag-plugin/target/packaged-verifier scripts/verification/PackagedJarVerification.java` and `java -cp <verifier-classes>;<plugin-jar> verification.PackagedJarVerification <plugin-jar>`: PASS; 712 base classes, max major 60, 20 SQLite native entries, restart and offline restore checks clear.
- `Get-FileHash -Algorithm SHA256`: rebuilt JAR exactly matches the server-tested artifact, `C48C54EC4037C8777B32FEC2F5017B00A69200306D95C417A95C5845D9785535`; adjacent checksum regenerated.
- Stale `goldbag-plugin-2.0.0-SNAPSHOT-shaded.jar` is gone. The freshly generated `original-GoldBag-2.0.0-SNAPSHOT.jar` is Maven's current unshaded intermediate, not the removed legacy plugin.
- `git diff --check`: PASS. No current runtime source, tests, build configuration, or saved server data changed.

Independent Luna review: clear; see `T8-cleanup-review.md`. Included in [PR #2](https://github.com/mikestanaszak/GoldBag/pull/2); GitHub is authoritative for publication and check status. No server rerun is needed for this cleanup because the rebuilt plugin bytes match the already tested artifact.
