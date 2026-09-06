# T6 incremental packaging review

Scope: controller-owned `goldbag-plugin/pom.xml` and the incremental package
assertion in `.github/workflows/ci.yml`. No source or server behavior was
reviewed here.

## Disposition

No P1/P2 defect found. The correction is appropriately scoped and addresses
the observed stale shaded-input failure.

The parent POM manages `maven-jar-plugin` at version 3.4.2, so the newly
activated plugin receives a stable version. Its `forceCreation=true` setting
is the documented Maven JAR behavior for a JAR subsequently post-processed by
Shade: it recreates the unshaded input even when Maven considers its ordinary
inputs unchanged. Since Shade replaces the main artifact in this project,
that prevents a prior shaded JAR from being fed back into the next shading
pass. The setting is confined to the plugin module and does not alter source
compilation or runtime behavior.

The CI assertion locates exactly one non-`-original` GoldBag JAR after the
normal full `mvn -B verify`, records its checksum, runs a second
`mvn -B -DskipTests package`, and compares the same artifact's checksum before
the packaged verifier and artifact upload. This directly covers the reported
no-source-change regression while avoiding a second test execution. The
project's fixed `project.build.outputTimestamp` supports stable archive bytes
across the forced rebuild. The existing exact-one-JAR guard and verifier remain
in place.

The configuration matches the official Maven JAR guidance:

https://maven.apache.org/plugins/maven-jar-plugin/jar-mojo.html

## Verification

- Reviewed the uncommitted POM and CI diff against the incremental packaging
  report and parent plugin management.
- Confirmed `git diff --check` reports no whitespace errors.
- Maven was not run in this review because the controller was concurrently
  running clean verification and the repeated package check, as requested.

No implementation change is requested from this review.
