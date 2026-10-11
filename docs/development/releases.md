# Automated releases

After this workflow is merged, every push to the default branch `main` (including PR merges) runs Java 17/21/25 builds, shaded-package verification, player-client unit checks, and release commit-analysis checks. Only after all jobs pass does semantic-release evaluate commits since the last `vX.Y.Z` tag. Feature branches and pull requests never publish.

Use Conventional Commits in commits retained by merge commits or in the title/body of a squash merge:

| Commit | Result |
| --- | --- |
| `fix: correct note redemption` | Patch |
| `feat: add an exchange option` | Minor |
| Any commit with a `BREAKING CHANGE: ...` footer | Major |
| `docs: ...`, `test: ...`, `chore: ...` | No release by themselves |

For a breaking change, use the explicit footer even if the subject also contains `!`. Ordinary merge commit subjects are ignored; the retained conventional commits determine the version. The strongest change wins. The existing `v1.1.1` tag remains the release baseline; this PR records the SQLite rebuild's legacy database/banknote incompatibility as a breaking change so the first automated version is `2.0.0` if no intervening release occurs.

The release prepare step updates all reactor POMs in the disposable Actions checkout to the selected version, runs `mvn -B clean verify`, and checks the actual shaded JAR (including descriptor/manifest version agreement, native SQLite, restart persistence, and offline restore). GitHub receives `GoldBag-X.Y.Z.jar`, its SHA-256 file, a `vX.Y.Z` tag, and generated release notes. Development POMs remain `2.0.0-SNAPSHOT`; no version-bump commit or npm package is published. Issue/PR comments and released labels are disabled.

Only the release job has `contents: write`; it uses the repository's built-in `GITHUB_TOKEN`. No personal token or npm credential is needed. Repository rules that prohibit the Actions token from creating tags/releases must be adjusted by an administrator if publishing fails. Main-branch runs are not canceled when a newer push arrives; semantic-release considers unreleased commits in the checked-out branch history.

## Local verification

Use Node 24.10 or newer:

```sh
npm ci
npm test
npm run release -- --dry-run --no-ci
```

The dry run checks GitHub authentication/push permissions and shows version/notes on `main`, but skips preparation and publishing. On feature branches it reports that no release is allowed. Never run a real local release for verification.

To verify preparation separately, use an expendable copy of the source tree with Java 25 and Maven installed:

```sh
bash scripts/Prepare-GoldBagRelease.sh 2.0.0
(cd dist && sha256sum -c GoldBag-2.0.0.jar.sha256)
```

The prepare script deliberately changes that copy's POM versions and replaces its ignored `dist` directory.

Configuration follows the [semantic-release documentation](https://semantic-release.org/usage/configuration/), [exec plugin](https://github.com/semantic-release/exec), and [GitHub plugin](https://github.com/semantic-release/github).
