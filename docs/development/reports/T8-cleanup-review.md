# T8 cleanup review

## Scope and result

Reviewed the T8 cleanup diff removing eight legacy Java files under `java/goldbag/goldbag/` and four legacy resources under `resources/`, plus the README and STATUS updates. The removal is consistent with the current Maven layout. No cleanup defect or evidence of unintended current-source/data loss was found.

The original files remain addressable through the immutable commit links in the accepted specification, and the deleted paths remain recoverable from Git history. `.runtime/` and the external local Paper test directory were not targeted by this cleanup and remain outside the removed paths.

## Checks performed

- `git status --short` and `git diff --stat`: confirmed the expected eight Java deletions, four resource deletions, README/STATUS edits, and T8 report; no unrelated tracked deletions were present.
- `rg -n --hidden -g '!target/**' -g '!.git/**' '(java/goldbag|resources/(config|defaultSQL|defaultValues|plugin)|goldbag\.goldbag|ChatInputMap|ChatInputStuff|ChatParser|DatabaseHandler|EventHandlers|GoldBagCommands|GoldBagTabComplete)' .`: found only the three immutable historical source links in `docs/superpowers/specs/2026-09-05-goldbag-design.md`; no current code, build file, script, or runtime reference to the removed paths.
- `rg -n --hidden -g '!target/**' -g '!.git/**' '(sourceDirectory|resourcesDirectory|<source>|<resources>|maven-compiler|goldbag-plugin)' pom.xml goldbag-core goldbag-storage goldbag-plugin .github scripts docs README.md`: confirmed the reactor and scripts reference `goldbag-core`, `goldbag-storage`, and `goldbag-plugin`; the plugin explicitly uses `goldbag-plugin/src/main/resources`.
- Read `pom.xml`, all three module POMs, `.gitignore`, README, STATUS, and the T8 cleanup brief. Maven declares only the three module roots; no root `java/` or `resources/` override exists. Generated `target/` directories are ignored as expected.
- `git diff --check`: passed; only normal CRLF conversion warnings appeared for README/STATUS.
- `git show --stat --oneline HEAD` and `git ls-files java resources`: confirmed the legacy files are historical tracked paths from the prior layout and are the exact paths in the cleanup diff. The historical source and resource content remains recoverable from Git.
- Read-only directory inspection: root `java/` and `resources/` directories are absent; current module targets contain the current reactor outputs only (`GoldBag-2.0.0-SNAPSHOT.jar`, checksum, and Maven’s `original-` jar).

## Remaining steps

The controller should finish the already-running clean rebuild and packaged verification, then update the T8 task state/report and integrate the cleanup. This review does not claim those concurrent checks’ results.

## Integration API

No source or build edits were made by this reviewer. Only this report is owned by the review task; the controller should stage and commit it together with the cleanup after verification.
