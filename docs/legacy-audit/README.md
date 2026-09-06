# Legacy Screen Recorder audit

Milestone 1 preserves an evidence-based description of the XML/Kotlin application at `ff1dc90d6395f36f12f6c47eb75957b94ae9c2f6`. It is a code inspection, not a device test or rebuild. The package was verified as `com.droidnova.screenrecorder` in `app/build.gradle.kts` and `app/src/androidTest/.../ExampleInstrumentedTest.kt`.

## Index

| Document | Scope |
|---|---|
| [preservation-plan.md](preservation-plan.md) | Git/version snapshot and owner-run archival commands |
| [project-inventory.md](project-inventory.md) | Build, modules, classes, dependencies and tests |
| [ui-inventory.md](ui-inventory.md) | Screens, dialogs, sheets, states and accessibility |
| [user-flows.md](user-flows.md) | Launch, recording, files and settings flows |
| [feature-matrix.md](feature-matrix.md) | Requested feature verification |
| [recording-engine-audit.md](recording-engine-audit.md) | End-to-end engine trace and classified findings |
| [permission-matrix.md](permission-matrix.md) | Manifest/runtime permission decisions |
| [storage-and-migration.md](storage-and-migration.md) | Storage, SAF, sharing, preferences and upgrade plan |
| [asset-inventory.md](asset-inventory.md) | Visual identity and asset classifications |
| [known-risks.md](known-risks.md) | Severity-ranked user and technical risks |
| [reviews-and-baseline.md](reviews-and-baseline.md) | Builds, tests, public feedback and Play Console needs |
| [manual-capture-checklist.md](manual-capture-checklist.md) | Required device captures and tests |

## Evidence rules and limits

“Verified” means directly evident in repository files; “probable risk” requires device confirmation; “unknown” has no repository evidence. No installed legacy app, emulator, instrumentation test, Play Console, signing material, or private analytics was inspected. Sensitive advertising/configuration values are intentionally omitted. The repository has no configured remote and its checked-out branch is `work`, not the stated default `master`; nevertheless its application ID and implementation identify the expected project. No Milestone 2 work is included.
