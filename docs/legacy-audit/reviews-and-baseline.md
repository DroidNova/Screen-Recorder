# Reviews, tests, and baseline

## Build health (2026-09-06 UTC)

Each required command was attempted separately and returned exit 126 because tracked `gradlew` is not executable. To distinguish project health from wrapper permissions, `bash gradlew` was also attempted separately; each returned exit 1 because Gradle 8.9 download was blocked by the environment proxy (`HTTP 403`). Thus compilation, unit tests and lint are **blocked by environment**, not known pass/fail project results.

| Check | Exact commands | Result |
|---|---|---|
| Debug build | `./gradlew :app:assembleDebug`; `bash gradlew :app:assembleDebug` | permission denied; then proxy 403 |
| Unit tests | `./gradlew :app:testDebugUnitTest`; `bash gradlew :app:testDebugUnitTest` | permission denied; then proxy 403 |
| Lint | `./gradlew :app:lintDebug`; `bash gradlew :app:lintDebug` | permission denied; then proxy 403 |

No instrumentation tests ran because no suitable already-running device/emulator was established. Existing tests are generated samples only (`ExampleUnitTest.addition_isCorrect`; `ExampleInstrumentedTest.useAppContext`) and meaningfully cover no recording, permissions, storage, UI, service, migration, encoding, failure, or lifecycle behavior. A checked-in release AAB is approximately 7.6 MiB, but provenance/build/signing/production equivalence is unknown; it is not a newly built baseline.

## Supplied public feedback mapping

Only the five stakeholder-supplied complaints are recorded; no additional reviews are invented. Public listing verification could not be relied on in this audit environment.

| Complaint | Current evidence | Probable cause (not confirmed) | Rebuild requirement / future milestone |
|---|---|---|---|
| Setup/navigation captured before desired content | Fixed 3-second full-screen countdown starts capture immediately afterward; no app-switch workflow | User cannot comfortably navigate to target during fixed countdown | Configurable/pre-capture workflow and clear start affordance; recording UX milestone |
| Controls unclear/appeared unavailable | In-app only Start/Stop; pause/resume in notification; notification “Save” means stop and uses wrong icons | Notification hidden/denied or label/icon mismatch | Persistent discoverable controls, semantic labels, denial fallback; UX/service milestone |
| >100 MB could not easily save to phone | Primary location app-specific; SAF must be selected then Save tapped again; 4 KiB copy, no progress bytes/cancel/space validation | Export friction/provider limits or insufficient feedback | Robust resumable/streamed export and large-file tests; storage milestone |
| Glitching/confusion about broad capture access | MediaProjection OS consent plus special overlay and microphone are mandatory; setup has dimension/cleanup risks | Multiple broad-looking prompts and device codec mismatch | Contextual education, minimize overlay/mic, compatibility handling; permissions/engine milestones |
| Countdown choices 3/5/15 requested | Countdown hard-coded to 3000 ms | No setting exists | Typed preference and accessible choices including requested values; settings/recording UX milestone |

## Owner export required from Play Console

Private reviews; crash clusters and stack traces; ANRs; Android vitals; device/OS distribution; active users on API 24 and 25; highest production version code; pre-launch reports; permission-denial metrics (where available); existing release history/artifact mapping. Handle exports as sensitive data and summarize/redact before sharing.

## Manual test priorities

Run clean install and production-upgrade paths on API 24/25, 29, 33, 34+ and representative OEMs; all permission grant/deny/permanent-deny paths; each format option; portrait/landscape/rotation; notification pause/resume/stop with app backgrounded; system projection stop; process kill; rapid/very short stop; low storage; 100MB+ and multi-hour recordings; export to multiple SAF providers; play/share/rename/delete/collision/corrupt files; dark/light, tablet, TalkBack, switch access, 100/150/200% font, RTL, thermal/audio interruptions.
