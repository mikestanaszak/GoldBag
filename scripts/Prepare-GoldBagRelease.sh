#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?Usage: Prepare-GoldBagRelease.sh <major.minor.patch>}"
if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  printf 'Invalid stable release version: %s\n' "$version" >&2
  exit 1
fi

# Only the disposable release checkout is updated; source POMs stay snapshots.
mvn -B org.codehaus.mojo:versions-maven-plugin:2.19.1:set \
  -DnewVersion="$version" -DprocessAllModules=true -DgenerateBackupPoms=false \
  -DupdateBuildOutputTimestampPolicy=never
mvn -B clean verify

jar="goldbag-plugin/target/GoldBag-$version.jar"
verifier="$(mktemp -d)"
trap 'rm -rf "$verifier"' EXIT
javac -encoding UTF-8 -cp "$jar" -d "$verifier" scripts/verification/PackagedJarVerification.java
java -cp "$verifier:$jar" verification.PackagedJarVerification "$jar"

rm -rf dist
mkdir dist
cp "$jar" dist/
(cd dist && sha256sum "GoldBag-$version.jar" > "GoldBag-$version.jar.sha256")
