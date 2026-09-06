# Project inventory

## Build configuration

| Item | Verified value | Evidence |
|---|---|---|
| Project/modules | `Screen Recorder`; `:app` | `settings.gradle.kts` |
| AGP / Kotlin / Gradle | 8.7.3 / 1.9.0 / wrapper 8.9 | `gradle/libs.versions.toml`; `gradle/wrapper/gradle-wrapper.properties` |
| SDKs | min 24, target 34, compile 34 | `app/build.gradle.kts` |
| Identity | application ID and namespace `com.droidnova.screenrecorder` | `app/build.gradle.kts` |
| Version | code 4; name 0.004 | `app/build.gradle.kts` |
| JVM | Java source/target 1.8; Kotlin JVM target 1.8 | `app/build.gradle.kts` |
| Features | View Binding enabled; no Compose | `app/build.gradle.kts` |
| Build types | implicit debug; release minifies and shrinks resources | `app/build.gradle.kts` |
| R8 | optimized default rules plus essentially placeholder `app/proguard-rules.pro` | both build files |
| Major UI | Core KTX, AppCompat, Material, Activity, ConstraintLayout, Navigation Fragment/UI | version catalog and app build |
| Media/UI helper | Glide 4.14.2 plus kapt compiler | `app/build.gradle.kts` |
| Firebase | Google services plugin, Crashlytics plugin/library, Analytics via BoM 33.5.1; config file presence not documented with values | root/app build |
| Advertising | Google Mobile Ads 23.2.0; banner initialized in `MainActivity.initAdview`; manifest app identifier exists (redacted) | app build, `MainActivity.kt`, manifest |
| Tests | JUnit 4.13.2; AndroidX JUnit 1.2.1; Espresso 3.6.1 | version catalog/app build |

No README or CI configuration is present in `rg --files`. Root and app `.gitignore` exclude normal build/IDE files. Manifest backup/data-extraction resources remain sample-like and do not define explicit include/exclude policy.

## Implementation catalogue

| Role | Components and evidence |
|---|---|
| Activity/navigation | `MainActivity`; `nav_graph.xml` with Home start, Recordings, Settings; bottom menu mirrors IDs |
| Fragments | `HomeScreenFragment`, `RecordingsScreenFragment`, `SettingsScreenFragment`, `PermissionBottomSheetFragment` |
| State | Activity-scoped `MainViewModel` loads/deletes/renames files; LiveData and `MutableSharedFlow`; no repository layer |
| Service | `ScreenRecordingService` (local binder, sticky start) dispatches actions to `ServiceHelper` |
| Recording | `ServiceLauncher`, `ServiceHelper`, `MediaSettingsUtil`, `FileUtil` |
| Encoding/muxing | `EncodingUtil`, `MuxingUtil`, `RealTimeMuxerUtil`; internal-audio entry is not called |
| Files/UI | `VideoAdapter`, `VideoModel`, `VideoUtils`, `StorageUtils`, `PermissionUtils`, `DialogUtil` |
| Persistence | singleton `PreferenceUtil` backed by named SharedPreferences |
| Concurrency | ViewModel/lifecycle coroutines; unowned main-scope timer; raw encoding threads (currently unused path) |
| Lifecycle/error/logging | service binding and callbacks; projection callback; scattered catches/Toast/Crashlytics; extensive Log statements and some `printStackTrace`; no unified error model |

All production Kotlin resides under `app/src/main/java`; no `app/src/main/kotlin` exists. There are no repositories. Test files are only generated examples: arithmetic and package-name assertions; they provide no feature coverage.
