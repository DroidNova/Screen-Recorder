# Milestone 5A: Android 17 / API 37 toolchain migration

## Purpose and scope

This isolated migration moves the verified Milestone 5 project to the Android 17/API 37 build toolchain. It changes build configuration and documentation only. Recording, MediaProjection, codec, muxer, storage, UI, navigation, permissions, application identity, and product behavior are unchanged.

Official Android 17, AGP 9.3, built-in Kotlin, Dagger/Hilt, and KSP release documentation is authoritative. Codex's documentation/browser and direct network requests were blocked in this environment (HTTP 401/403), so the prescribed stable versions in the milestone specification were applied without substituting third-party sources.

Authoritative references for owner review:

- [Android 17 overview](https://developer.android.com/about/versions/17)
- [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Android Studio releases](https://developer.android.com/studio/releases)
- [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [AGP 9 migration](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Dagger releases](https://github.com/google/dagger/releases)
- [KSP releases](https://github.com/google/ksp/releases)

## Toolchain versions

| Component | Starting repository value | Final value |
|---|---:|---:|
| Android Gradle Plugin | 8.13.2 | 9.3.2 |
| Gradle wrapper distribution | 8.13 | 9.5.0 |
| Compile SDK | 36 | 37 |
| Target SDK | 36 | 37 |
| Minimum SDK | 24 | 24 |
| Java source/target | 17 | 17 |
| Kotlin JVM target | 17 | 17 |
| Project Kotlin/Compose plugin | 2.2.21 | 2.2.21 |
| KSP plugin | 2.2.21-2.0.4 | 2.3.10 |
| Hilt runtime/compiler/plugin | 2.57.2 | 2.60.1 |
| Compose BOM | 2025.12.01 | 2025.12.01 |
| Navigation Compose | 2.9.8 | 2.9.8 |

AGP 9.3.2 is the requested patched AGP 9.3 release with API 37 support and the documented JDK 17 lint correction. Gradle 9.5.0 is its required wrapper version. No Build Tools version is pinned; AGP may select its supported default.

## AGP 9 DSL and built-in Kotlin

The project already used the public `android` DSL, modern `buildFeatures`, standard build types, and standard ProGuard declarations. It has no legacy variant APIs, internal AGP classes, custom generated-source registration, custom source sets, old packaging syntax, or `android.kotlinOptions` block to migrate.

AGP 9 built-in Kotlin now supplies Android Kotlin integration. The `org.jetbrains.kotlin.android` alias was removed from the app, root plugin declaration, and version catalog. No `android.builtInKotlin=false` or `android.newDsl=false` escape hatch was added. The Kotlin compiler configuration uses `kotlin.compilerOptions` with `JvmTarget.JVM_17`; Java source and target compatibility remain 17.

The Kotlin version remains intentionally present solely for the separately applied `org.jetbrains.kotlin.plugin.compose` plugin. Compose compiler integration therefore remains explicit while the redundant Android Kotlin plugin is gone. The existing Compose BOM and all Compose/AndroidX runtime versions are unchanged.

## Hilt and KSP

Hilt's Gradle plugin, runtime, and compiler share version 2.60.1. The existing `@HiltAndroidApp` application and `@AndroidEntryPoint` activity remain intact. Hilt processing continues through `ksp(...)`; kapt was not introduced. KSP is updated once to 2.3.10, with no old KSP alias or manual generated-source directories retained.

## API and application configuration

`compileSdk` and explicit `targetSdk` are 37. `minSdk` remains 24. Namespace and application ID remain `com.droidnova.screenrecorder`; version code 4 and version name 0.004 are unchanged. Release minification/resource shrinking and ProGuard inputs are unchanged. No signing configuration or machine-specific SDK/JDK path was added.

## Android 17 behavior audit

| Behavior change | Applicable | Repository evidence | Required change | Validation |
|---|---|---|---|---|
| MessageQueue implementation | No | No reflection or private MessageQueue access | None | Source search |
| Static-final reflection restrictions | No | No reflection, JNI, or static-final mutation | None | Source/build search |
| Local-network permission | No | No LAN discovery, sockets, networking library, or network permission | Do not add `ACCESS_LOCAL_NETWORK` | Manifest/source search |
| Background Activity Launch hardening | Yes, notification reopen path | Notification uses an explicit `MainActivity` intent and immutable `PendingIntent`; service does not launch activities | Preserve existing safe reopen flow | Source inspection |
| Certificate transparency | No current network behavior | No upload/network stack or network security configuration | None | Dependency/source search; graph execution blocked |
| Native dynamic-code loading | No | No JNI, native library, `System.load`, or writable-code loader | None | Source search |
| Large-screen orientation/resizability | Yes, platform behavior; no conflict | No orientation/aspect/resizability restriction; adaptive Compose navigation remains | None | Manifest/UI source inspection; device regression pending |
| Background audio restrictions | No | Milestone 5 is video-only; no audio focus/playback/capture or audio FGS permission | None | Manifest/source search |
| RemoteViews memory limits | No custom RemoteViews | Ordinary `NotificationCompat` text and a small local vector icon | None | Notification source inspection |
| Contacts, SMS, and OTP changes | No | No APIs or permissions for these data classes | None | Manifest/source search |
| Content-capture behavior | Potentially relevant to recording; no compatibility change needed | Activity does not set `FLAG_SECURE` | Do not add a global secure flag | Source search |

## Manifest and permission audit

Source manifest permissions before and after migration are identical:

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`

The sole recording service remains non-exported with `foregroundServiceType="mediaProjection"`. No local-network, storage, media-read, audio, microphone-FGS, overlay, phone, contacts, SMS, or call-log permission was added. Gradle could not run, so no new merged manifest was generated for Codex inspection; source-manifest inspection passed and owner build inspection remains required.

## Dependency graph audit

No AndroidX, Compose BOM, Navigation, Material 3 Adaptive, or test dependency was changed. Only AGP, Hilt, and KSP were migrated. Static catalog/build-script inspection finds one Hilt version used consistently, one KSP plugin declaration, no Kotlin Android plugin remnant, no Android Support Library coordinate, and no Jetifier property. The executable `:app:dependencies` graph was blocked before Gradle startup, so duplicate transitive Kotlin libraries and resolved graph conflicts require owner verification.

## Codex validation results

All Gradle commands used the available JDK 17. Earlier migration validation identified
that `gradle-9.5-bin.zip` does not exist, and the wrapper now points to the valid
`gradle-9.5.0-bin.zip` distribution. Codex then reran `./gradlew --version`,
`:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`,
`:app:assembleRelease`, `:app:bundleRelease`, and `:app:dependencies`; each exited 1
before Gradle launched because the environment proxy rejected the valid distribution
download with `HTTP/1.1 403 Forbidden`. No Gradle task is claimed to have passed.

The wrapper JAR was not fabricated or replaced because the distribution could not be downloaded to run the official `wrapper` task. The existing wrapper bootstrap JAR and scripts are retained; the wrapper metadata points to the required binary distribution. No checksum was added because its official value could not be retrieved and must not be guessed.

## Owner environment

Use Android Studio Panda 3 Patch 1 or newer; current stable Quail 4 is preferred. Install Android SDK Platform 37 and select JDK 17 as the Gradle JDK. Run the wrapper from a network that can reach the official Gradle distribution, and regenerate wrapper metadata with Gradle's official wrapper task if it reports any bootstrap metadata change. Do not commit `local.properties`, `.idea` machine settings, absolute SDK/JDK paths, signing material, Gradle caches, or build outputs.

## Manual regression checklist

1. Sync with JDK 17.
2. Confirm SDK Platform 37 is installed.
3. Build debug.
4. Run all JVM tests.
5. Run lint.
6. Build release APK and AAB.
7. Install debug over Milestone 5 without uninstalling.
8. Confirm normal launch.
9. Confirm Home, Recordings, and Settings navigation.
10. Check light and dark mode.
11. Check portrait and landscape.
12. Check split screen.
13. Press Start.
14. Confirm fresh MediaProjection consent.
15. Cancel and confirm no output.
16. Start again and grant consent.
17. Record moving content for at least 30 seconds.
18. Reopen and press Stop.
19. Confirm finalization.
20. Confirm the MP4 is visible and playable.
21. Confirm video has no audio track.
22. Confirm duration and orientation are reasonable.
23. Repeat Start/Stop three times.
24. Confirm fresh consent every time.
25. Confirm no duplicate files or services.
26. Stop another session through the privacy chip where supported.
27. Confirm return to a valid state.
28. Confirm no microphone, storage, overlay, or local-network permission request.
29. Confirm no UI redesign.
30. On an API 37 device/emulator, repeat the core recording flow.

Owner-run results must remain separate from Codex results.

## Deferred work

Service/lifecycle hardening, pause/resume, audio, countdown/settings, recording-library integration, orientation handling, migration, crash recovery, and later product milestones remain deferred. This migration adds no product behavior.
