# Incremental packaging correction

## Observed failure

The pre-merge `mvn -B verify` ran against an already packaged checkout. Maven skipped the unchanged jar goal, then Shade processed the previous shaded JAR again. It emitted new duplicate-class warnings and changed the artifact checksum from `676E04E0112B2411FE10C95262EE3F2E5AE8E32FDEDCCF2B512DCC8CEC530472` to `CC88BC88112DBAC501F2EFCC7A1661B62C52E0C8447C0E23449E60D52D1158C8` without a production-source change. Tests and runtime checks still passed, but incremental packaging was not reproducible.

## Correction

The plugin's Maven JAR configuration now sets `forceCreation=true`, rebuilding the unshaded input before each Shade execution. This is the documented setting for postprocessed JARs in the [official Maven JAR documentation](https://maven.apache.org/plugins/maven-jar-plugin/jar-mojo.html).

CI now compares the first package checksum with a second `mvn -B -DskipTests package` result before verifying/uploading the artifact. The second invocation repeats only packaging, not the already completed tests. This checks the observed behavior directly.

Controller owns shared POM/CI changes. Clean full verification, repeated-package hash equality, packaged checks, and independent Luna review are next. The resulting artifact metadata changes because its POM is embedded; its source behavior is unchanged, and the existing player harness will be rerun with this final artifact alongside its own pending fixes.

## Verified result

- `mvn -B clean verify`: PASS, 69 tests (12 core,17 storage,40 plugin).
- A subsequent `mvn -B -DskipTests package` returned exactly the same SHA-256: `C48C54EC4037C8777B32FEC2F5017B00A69200306D95C417A95C5845D9785535`. Only the expected input-manifest overlap remains; repeated dependency-class overlaps disappeared.
- Packaged verifier PASS:712baseclasses,maxmajor60,20SQLite native entries, restart/restore/source preservation.
- Independent Luna review clear in `T6-incremental-packaging-review.md`.
