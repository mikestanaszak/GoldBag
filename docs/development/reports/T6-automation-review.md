# T6 automation independent review

Scope: current automation files `.github/workflows/ci.yml`,
`scripts/Invoke-GoldBagBuild.ps1`, `scripts/verification/PackagedJarVerification.java`,
and `docs/development/reports/T6-automation.md`. Production code was reviewed
read-only; no staging or commit was performed.

## Disposition: clear

CI selects exactly one case-sensitive shaded `GoldBag-*.jar` at the target
root, excludes the `-original` artifact, and fails before verification when
the selection is ambiguous. The verifier is compiled against that JAR and is
run with only its temporary classes plus that JAR, so Maven target classes,
test classes, and an external Bukkit class path cannot mask packaging errors.
The verifier and Maven/compiler failures remain nonzero step failures, while
the `always()` upload preserves reports and any successfully produced package
for diagnosis.

The CI checksum is generated after copying the selected JAR into
`ci-artifacts`, from inside that directory, and contains the downloaded JAR's
basename. The PowerShell helper uses the same basename-only checksum format
next to the verified package. Both are therefore usable with ordinary
`sha256sum -c` or equivalent tooling after downloading the JAR and checksum
together. The helper's case-sensitive PowerShell filter rejects stale
lowercase or noncanonical package names and requires exactly one matching
artifact.

The PowerShell wrapper checks Java, Maven, verifier compilation, verifier
execution, artifact hashing, and checksum writing explicitly. Its temporary
verifier classes are created under a GUID-named system temporary directory and
removed in `finally`; the Java verifier's behavior fixture is a newly created
temporary directory, deleted in reverse order in `finally`, with a warning
that retains the path if cleanup itself fails. CI cleanup is similarly limited
to its generated verifier directory. No cleanup operation targets the
repository or the artifact directory.

The verifier's packaged checks cover the descriptor, manifest, relocated
dependencies, native SQLite entries, bytecode ceiling, restart persistence,
offline validate-only behavior, restore evidence, and source-export
preservation. All Bukkit calls are absent from the verifier class path and
source; the packaged behavior uses only classes inside the shaded artifact.

## Verification

- The existing focused verifier run recorded in `T6-automation.md` passed
  with 712 base classes, maximum major version 60, 20 SQLite native entries,
  restart persistence, dry-run, restore, pending evidence, source preservation,
  and temporary fixture cleanup.
- Recomputed the local package digest with `Get-FileHash` and confirmed the
  adjacent `.sha256` line contains the matching digest and the exact JAR
  basename.
- The focused Maven/controller verification was already green at this
  checkpoint: 66 tests. No full Maven run was repeated for this read-only
  automation review.

No concrete remaining automation defect was found.
