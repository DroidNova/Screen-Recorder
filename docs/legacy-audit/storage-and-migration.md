# Storage and migration

## Verified current behavior

| Concern | Finding / evidence |
|---|---|
| Recording location | App-specific external Movies: `context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)` in `FileUtil.createVideoFile` |
| Names/formats | `ScreenRecording<epoch>.mp4`; dormant audio `audio_<epoch>.aac`; list includes case-insensitive mp4/wav/aac (`MainViewModel.loadVideosFromFolder`) |
| User visibility | App list and FileProvider/external player/share. App-specific directory is not a normal public gallery destination; explicit SAF copy exposes a duplicate externally. |
| Update/uninstall | Same-package update ordinarily preserves app data/app-specific files. Uninstall normally removes app-specific external files; backup behavior is not explicitly scoped and must be tested. |
| SAF | OpenDocumentTree; calls `takePersistableUriPermission` read+write and stores URI string in `folder_uri`. `PermissionUtils` verifies matching persisted read/write grant. |
| Export | `DocumentsContract.createDocument` plus 4 KiB stream copy on IO dispatcher; resulting display name erroneously appends `.mp4` to the existing filename. Selecting a folder only stores it; user must invoke Save again. |
| Sharing | FileProvider authority from string; `file_paths.xml` exposes external-files `Movies/` and all cache. Read URI grant with SEND. |
| Rename/delete | `File.renameTo`; forces `.mp4`. Single delete checks return. Batch ignores return values and always emits success. |
| Incomplete output | File is allocated before prepare/start; failure/short stop has no deletion/quarantine. It may remain and be listed. |

## SharedPreferences migration ledger

The file name is a private opaque constant in `PreferenceUtil`; preserve/read it in-place during migration without publishing it externally. Every declared key follows; unused keys still matter because installed users may carry values from earlier releases.

| Key | Default | Current relevance / migration disposition |
|---|---:|---|
| obfuscated launch-count key (`LAUNCH_COUNT`) | -1 | Declared getter/setter; unused in current source. Retain only for compatibility analysis. |
| `video_microphone_state` | true | Declared but active recorder ignores it. Migrate as user audio intent, then validate. |
| `isAppLocked` | none (no accessor) | Constant only. Search found no current use; investigate older production APK/schema. |
| `appPassword` | none | Constant only; sensitive legacy possibility. Do not migrate plaintext without security review. |
| `showRateUsCard` | true | Accessor exists; current settings does not consult. Probably obsolete. |
| `show_preview` | none | Constant only; investigate older builds. |
| `max_video_time` | 2700 seconds | Accessor exists but engine does not enforce. Preserve intent; define new semantics. |
| `folder_uri` | null | Active SAF grant pointer. Migrate/reconcile with persisted grants. |
| `free_premium_count` | 0 | Accessor unused; product decision required. |
| `show_scoped_storage_dialog` | true | Active one-time Recordings info. Migrate to avoid re-show unless onboarding changes intentionally. |
| obfuscated bought-premium key (`KEY_BOUGHT_PREMIUM`) | false | Accessor unused; entitlement must be validated against authoritative billing backend/history, not blindly trusted. |
| `security_question_answer` | none | Constant only; sensitive legacy possibility; security review before any import. |
| `selected_audio_input_type` | `Mic` | Accessor exists but ignored. Migrate as intent only. |
| `video_quality` | `12Mbps` | Active; migrate to DataStore with validated enum/value. |
| `video_resolution` | `720p` | Active; migrate with validation/fallback. |
| `video_fps` | `60fps` | Active; migrate with validation/fallback. |

## Rebuild migration requirements

1. Preserve package ID `com.droidnova.screenrecorder` and Play signing continuity; obtain/upload-key and signing facts through secure owner channels.
2. Obtain highest production version code from Play Console—repository code 4 must not be assumed highest—and ship a strictly higher code.
3. Test an actual production old APK → new APK update without uninstalling across API/device tiers; keep originals immutable during discovery/import.
4. Detect legacy app-specific Movies recordings, including mp4/wav/aac and malformed/double extensions; expose or import idempotently without deletion, record provenance, validate media, and handle partial files.
5. Migrate legacy SharedPreferences once to typed DataStore, retaining a migration marker and safe rollback/read behavior. Resolve obsolete/security/entitlement keys deliberately.
6. Enumerate persisted URI grants, reconcile `folder_uri`, validate read/write/create capability, and prompt contextually when revoked; never assume string presence equals access.
7. Test update, backup/restore, uninstall warning, large files, low space, rename collisions, corrupt/unfinished output, and external folder/provider removal.
