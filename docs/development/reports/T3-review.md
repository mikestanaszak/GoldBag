# T3 operations review

Verdict: **Request changes**.

The CI workflow and build helper meet the stated Java 17/21, `mvn -B verify`, artifact/report retention, and truthful exit-status requirements. The documentation keeps compatibility rows unverified and does not accept the EULA. The local-server helper correctly refuses a target that is itself a reparse point, rejects differing destination contents, and does not create `eula.txt`.

## Findings

### Important: ancestor reparse points bypass the isolated-target check

**File:** `scripts/Prepare-GoldBagTestServer.ps1:60-72` (also described too strongly in `docs/operations/installation.md:39-41`)

The helper checks the lexical target path and only tests the final target directory's `ReparsePoint` attribute. It does not inspect existing parent components. A target such as `C:\\outside\\redirect\\child`, where `redirect` is a junction to the GoldBag workspace (or another protected directory), is lexically outside the workspace and `child` does not yet exist, so all checks pass. `New-Item` and the later copies then write through the junction into the junction target. This violates the isolated-target and no-source/build-output-collision requirements.

Reproduction on Windows (using disposable paths):

```powershell
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('goldbag-review-' + [guid]::NewGuid().ToString('N'))
$real = Join-Path $tmp 'real'
$parent = Join-Path $tmp 'parent'
$redirect = Join-Path $parent 'redirect'
$target = Join-Path $redirect 'child'
New-Item -ItemType Directory -Path $real,$parent -Force | Out-Null
New-Item -ItemType Junction -Path $redirect -Target $real | Out-Null
Set-Content (Join-Path $tmp 'server.jar') server -NoNewline
Set-Content (Join-Path $tmp 'plugin.jar') plugin -NoNewline
pwsh -NoProfile -File .\scripts\Prepare-GoldBagTestServer.ps1 `
  -ServerJar (Join-Path $tmp 'server.jar') -TargetDirectory $target `
  -PluginArtifact (Join-Path $tmp 'plugin.jar')
Get-ChildItem (Join-Path $real 'child') -Recurse
```

Observed result: exit 0, with all prepared files under `$real\child`; the final target path itself was never a reparse point. The fix should canonicalize and validate every existing ancestor from the target up to the volume/root (and reject reparse points), then perform the same validation for the source paths before any directory creation or copy.

No other concrete T3 finding was identified in the requested diff. The review did not rerun the worker's passed fixture suite; the junction reproduction was run specifically for this safety doubt.

## Round-one fix re-review

Verdict: **Pass** for the scoped fix.

`Assert-NoReparseAncestors` now walks every existing component of the target and both source paths before any directory creation or copy. This closes the previously reproduced target-under-junction escape, and the second guard after `Resolve-Path` covers a source whose resolved path differs from its input path. The worker's recorded target-junction and source-junction regressions both exit 1 before mutation. I found no new concrete breakage in this fix-only diff. The CI `paths-ignore` addition is intentional and leaves pull-request verification enabled.
