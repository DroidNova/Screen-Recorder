# Manual capture checklist

## Protocol

Use an installed, provenance-recorded legacy APK on a non-personal test device/account. Record app version, APK checksum, device/OEM, API, resolution, orientation, theme, font scale, permission state, and timestamp in a companion log. Remove notifications/personal content and redact system identifiers. Do not fake or edit UI beyond redaction. Use prefix `legacy-v0004_<api>_<device>_` and sequential two-digit numbers; PNG for screens and MP4 for flows.

## Required stills

- [ ] `01_home_idle_light.png`, `02_home_idle_dark.png` — Home defaults and banner area.
- [ ] `03_permissions_all_missing.png` — sheet with audio, overlay, notifications all visible (API 33+).
- [ ] `04_audio_denied.png`, `05_audio_denied_permanent.png`, `06_notification_denied.png`, `07_notification_denied_permanent.png`, `08_overlay_denied.png` — return to app after each denial.
- [ ] `09_mediaprojection_dialog.png` — untouched OS consent dialog (wording varies by OS).
- [ ] `10_countdown_3.png`, `11_countdown_2.png`, `12_countdown_1.png` — exact overlay frames.
- [ ] `13_recording_active_home.png`, `14_recording_paused_home.png`, `15_recording_notification_active.png`, `16_recording_notification_paused.png`.
- [ ] `17_recordings_empty.png`, `18_recordings_populated.png`, `19_recordings_multi_select.png` (one and multiple counts).
- [ ] `20_recording_options_sheet.png`, `21_rename_dialog.png`, `22_delete_one_confirmation.png`, `23_delete_many_confirmation.png`.
- [ ] `24_save_info_dialog.png`, `25_save_folder_picker.png`, `26_save_success.png`, `27_save_failure.png`.
- [ ] `28_settings_light.png`, `29_settings_dark.png` (include hidden/absent WhatsApp observation in log).
- [ ] `30_home_landscape.png`, `31_recordings_landscape.png`, `32_settings_landscape.png`.
- [ ] `33_home_tablet.png`, `34_recordings_tablet.png`, `35_settings_tablet.png` if hardware exists; record “not available” rather than simulate.
- [ ] `36_home_font100.png`, `37_home_font150.png`, `38_recordings_font150.png`, `39_settings_font150.png`.
- [ ] Capture quality, resolution and FPS selectors as `40_quality_selector.png`, `41_resolution_selector.png`, `42_fps_selector.png`.

## Required videos

- [ ] `50_success_start_to_playback.mp4`: cold start → permissions already granted → consent → 3/2/1 → target app → 15 seconds → pause → resume → stop in app → Recordings → playback.
- [ ] `51_notification_stop.mp4`: start → background app → pause/resume → notification “Save”/stop → reopen and play.
- [ ] `52_denied_recording_flow.mp4`: deny each permission/consent separately and capture feedback/recovery.
- [ ] `53_system_projection_stop.mp4`: stop from OS privacy/projection control; capture app and notification aftermath.
- [ ] `54_save_share_rename_delete.mp4`: export (including first folder grant), verify public file/name, share, rename/collision, delete one and many.
- [ ] `55_rotation_process_failure.mp4`: rotate before/during recording, background/return, then controlled process kill where test tooling permits.
- [ ] `56_large_recording_export.mp4`: create >100MB non-sensitive capture, report size/duration/free space, export and verify checksum; do not upload it.

## State/behavior log for every run

Record whether Start changes before capture truly begins; timer continuity; audio actually heard (mic versus internal); notification visibility/actions; target content vs setup frames; output path/name/size/duration/playability; orientation/crop; Toast/dialog wording; permission recovery; app relaunch/process-death state; incomplete files; export duplicate extension; TalkBack labels/focus order; clipped/overlapping text at 150%; light/dark contrast. Also test consent denial, immediate stop, unsupported settings, revoked overlay mid-countdown, notification denial, low storage, screen lock, incoming audio interruption, and system projection termination. Mark each as observed, failed, or not run—never infer device behavior from code.
