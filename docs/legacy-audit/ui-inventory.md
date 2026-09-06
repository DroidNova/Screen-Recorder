# UI inventory

## Application shell and destinations

`MainActivity` inflates `activity_main.xml`: Navigation host above a persistent bottom navigation and banner-ad container. It initializes preferences, NavigationUI, Mobile Ads, and a banner retry loop. Theme is Material 3 DayNight with Lato; explicit light and night palettes exist. Most literal UI text is hard-coded in XML/Kotlin, limiting localization.

| Surface | Source / layout | Entry, controls and states | Navigation/actions | Ads/accessibility/theme |
|---|---|---|---|---|
| Home (“Video Recorder”) | `HomeScreenFragment.kt`; `fragment_home_screen.xml` | Default free-storage ring, 12Mbps, 720p, 60fps, 00:00, Start. Dialog selections persist. Button changes to Stop; timer updates only while bound. No loading/empty/error view. | Start → permission sheet or MediaProjection; Stop → service. Quality/resolution/FPS dialogs. | Persistent shell banner. DayNight colors. Button/text semantics only; decorative/status views largely lack content descriptions and storage ring has no stated semantics. |
| Recordings (“Recordings”) | `RecordingsScreenFragment.kt`; `fragment_recordings_screen.xml`, `item_video_details.xml` | Loading dialog; empty message; populated list with thumbnail, duration/name/date/size. Long press enters selection; count/delete toolbar. | Tap → options sheet. Long press → selection. Bottom tab navigation. | Banner. Video thumbnail says only “Video”; delete icon lacks content description; hard-coded text. Selection colors have night overrides but contrast needs testing. |
| Settings (“Settings”) | `SettingsScreenFragment.kt`; `fragment_settings_screen.xml` | Rate Us, Share App, Privacy Policy, Report Bugs, Version Info. WhatsApp row and “Others” heading are gone. No loading/error states. | External market/browser/share/email; WhatsApp code unreachable from hidden row; version has no click action. | Banner. Setting icons have string content descriptions; rows need TalkBack/order testing. DayNight resource colors. |

## Dialogs, sheets, and system surfaces

| Surface | Entry/title | Controls/default/selection | Outcomes and state gaps |
|---|---|---|---|
| Permission sheet (`bottom_sheet_permissions.xml`) | Home Start when aggregate check fails; “Permissions Required” | Missing Audio, Overlay, Notification rows each with Allow; already-granted rows hidden; auto-dismiss when all pass | Launches runtime/system settings. Denials merely leave row visible; no rationale/permanent-denial recovery. Overlay explanation incorrectly equates overlay with capture. |
| Quality selector | Home quality | 3/6/8/10/12/15 Mbps; stored default 12; radio selection | Saves preference; no capability validation. |
| Resolution selector | Home resolution | 360/480/720/1080p; default 720p | Saves preference; no codec/device validation. |
| FPS selector | Home FPS | 30/60; default 60fps | Saves preference; no device validation. |
| MediaProjection consent | System dialog from `createScreenCaptureIntent` | OS-controlled | Accept starts service; denial has no user feedback. |
| Countdown overlay | `ServiceHelper.showCountdownOverlay` | Full screen, green 3/2/1 on translucent black; fixed 3 seconds | Removed then starts recording. Add/remove exceptions are not handled; requires overlay permission. |
| Recording notification | `ServiceHelper.createNotification` | Pause or Resume; “Save” action | Save dispatches STOP, not export. No content title/text/content intent; all action icons incorrectly use delete. `custom_notification.xml` is unused. |
| Options sheet | `bottom_sheet_more_option.xml` | thumbnail/name/date; Play, Save to Folder, Share, Rename, Delete | No explicit failure state; folder picker may require user to tap Save again after granting. |
| Rename dialog | `dialog_rename.xml` + Material builder | current basename, Rename/Cancel | Empty ignored. Failure only logged; collision/path validity not surfaced. |
| Delete dialogs | Material builders | one or selected-many confirmation | Single reports success/failure Toast; batch reports success regardless of individual deletes. |
| First Recordings info | Material builder | “Video Saved to App Storage!”, Got it | controlled by `show_scoped_storage_dialog`; not shown again after acknowledgement. |
| Loading/save/delete progress | `dialog_progress_loading.xml`, `dialog_progress.xml` | non-cancelable spinner/message | list load can remain indefinitely if videos directory is null because LiveData is not posted. |
| SAF folder picker/player/share chooser | Android/external apps | system-controlled | Behavior depends on providers and installed apps; device test required. |

There is no dedicated in-app playback screen, onboarding screen, settings detail destination, recording-error screen, or explicit landscape/tablet layout. Light/dark behavior is resource-inferred only.
