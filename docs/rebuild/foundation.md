# Milestone 2: Compose foundation

## Baseline and toolchain

Work began on the managed-workspace branch `work` at
`5a1966850e0f5ae188f0570d623aca76dcb6f633`. That workspace represents the
selected `rebuild/compose` GitHub branch.

The foundation selects stable releases that share the Kotlin 2.2 toolchain and
meet the Android Gradle Plugin compatibility requirements:

| Component | Version |
|---|---:|
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin and Compose compiler plugin | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Compose BOM | 2025.12.01 |
| Material 3 | Managed by the Compose BOM |
| Hilt | 2.57.2 |

Kotlin's Compose compiler Gradle plugin is paired with the same Kotlin version.
Hilt annotation processing uses KSP rather than kapt. Every plugin and library
version is declared in `gradle/libs.versions.toml`.

The application namespace and ID remain `com.droidnova.screenrecorder`.
`compileSdk` and `targetSdk` are 36, `minSdk` is 24, and Java/Kotlin use JVM 17.
The repository's existing version code 4 and version name 0.004 are retained
temporarily; the highest Play Console production version code is unknown and is
a release blocker. Play Console usage data is also required before deciding
whether API 24 and 25 can be dropped.

## Application structure

New application code lives under
`app/src/main/kotlin/com/droidnova/screenrecorder/`:

- `ScreenRecorderApplication` is the Hilt application entry point.
- `MainActivity` is the only activity and enables edge-to-edge rendering.
- `ui/ScreenRecorderApp.kt` owns the minimal Compose root and foundation screen.
- `ui/theme/Theme.kt` supplies system-selected Material 3 light and dark themes.

The foundation screen deliberately contains only the application name and a
short rebuild subtitle. Both are string resources. There is no navigation shell,
record control, or recording behavior.

## Removed legacy surface

The active Fragment/XML runtime, navigation graph, layouts, menus, dialogs,
bottom sheets, ViewModel, adapter, recording service, launch/helper classes,
recording and storage utilities, FileProvider configuration, generated example
tests, Lato fonts, unused artwork, and committed release AAB were removed.
Firebase, Crashlytics, Analytics, Google services, advertising, Glide, AppCompat,
Views, ConstraintLayout, and Fragment Navigation are no longer plugins or
dependencies. The dormant `app/google-services.json` remains tracked but is not
read or referenced by the build.

Only the adaptive launcher icon XML and density-specific launcher WebP resources
were retained for application identity. The Play Store source image and all other
legacy assets were removed. Lato and other asset licensing still require a
documented decision before any future reuse.

Room, Media3, CameraX, WorkManager, Firebase, advertising, Billing, DataStore,
recording libraries, migration code, navigation, and recording capabilities are
intentionally deferred.

## Verification

The intended verification commands are:

```text
./gradlew --version
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
git diff --check
git status --short
```

In this workspace, every Gradle command above exited 1 before configuration
because the Gradle 8.13 distribution download could not tunnel through the
environment proxy (`HTTP/1.1 403 Forbidden`). No SDK installation was exposed
through `ANDROID_HOME` or `ANDROID_SDK_ROOT`. The project was not downgraded to
work around these environment constraints; build, test, lint, and APK manifest
verification remain blocked until the wrapper and Android SDK 36 are available.
