# Preservation plan

## Recorded pre-change state

| Field | Evidence/result |
|---|---|
| Repository root | `/workspace/Screen-Recorder` (`git rev-parse --show-toplevel`) |
| Branch | `work` (not the stated default `master`) |
| HEAD | `ff1dc90d6395f36f12f6c47eb75957b94ae9c2f6` (`release3`) |
| Initial worktree | Clean; `git status --short` returned no lines |
| Remote | None configured; `git remote -v` returned no lines, so no URL/credentials were exposed |
| App identity/version | `com.droidnova.screenrecorder`; code `4`; name `0.004` (`app/build.gradle.kts`) |
| History | Nine visible commits; recorded with `git log --oneline --decorate -n 20` before edits |

No pre-existing worktree changes existed. No branch or tag matching the recommendations below existed (`git branch --list` / `git tag --list`).

## Recommended archive (owner action after review only)

Recommended branch: `archive/legacy-xml-v0.004`; annotated tag: `legacy-v0.004`. First establish whether `work` or the upstream `master` is the production source, configure/verify the correct remote, and compare with Play Console source/version. Then the owner may run:

```bash
git status --short
git branch archive/legacy-xml-v0.004 ff1dc90d6395f36f12f6c47eb75957b94ae9c2f6
git tag -a legacy-v0.004 ff1dc90d6395f36f12f6c47eb75957b94ae9c2f6 -m "Archive legacy XML Screen Recorder v0.004"
git push <verified-remote> archive/legacy-xml-v0.004
git push <verified-remote> legacy-v0.004
```

These commands were **not** executed by the audit. Preserve the existing AAB (`app/release/app-release.aab`, approximately 7.6 MiB) separately with checksums and access controls; do not infer that it is the Play production artifact or inspect signing contents.
