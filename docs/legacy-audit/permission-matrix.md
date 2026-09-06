# Permission matrix

| Manifest permission | Type/version | Current reason and exact code path | Blocks? / necessary? | Rebuild decision |
|---|---|---|---|---|
| `RECORD_AUDIO` | Dangerous runtime, API 1+ | `ServiceLauncher.areAllPermissionsGranted`; sheet `requestAudioPermission`; active `ServiceHelper.setupMediaRecorder` uses MIC | Yes. Necessary only when microphone capture is enabled, yet it is always enabled today. | **Request contextually** after explicit mic choice. |
| `POST_NOTIFICATIONS` | Dangerous runtime, API 33+ | aggregate gate and permission sheet; recording notification | Yes on 33+ by app policy. FGS can start without runtime grant but visibility differs; user controls still need an accessible design. | **Request contextually**; do not silently make capture depend on unexplained denial. |
| `SYSTEM_ALERT_WINDOW` | Special app access | `Settings.canDrawOverlays`; settings intent; countdown overlay | Yes. Necessary only for this overlay countdown, not MediaProjection itself. | **Remove** if rebuild uses an in-app/system-compliant countdown; otherwise needs policy review. |
| `FOREGROUND_SERVICE` | Normal, API 28+ | foreground recording service | Not runtime; necessary for long-running capture. | **Keep**. |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Normal/type-specific, API 34 | service type and typed `startForeground` | Not runtime; necessary at target 34 for capture FGS. | **Keep**. |
| `FOREGROUND_SERVICE_MICROPHONE` | Normal/type-specific, API 34 | service declares/starts microphone type | Not runtime; only needed with microphone path. | **Request contextually** is not possible for normal permission; **keep** if mic feature retained, otherwise remove. |
| `MEDIA_PROJECTION` | Declared normal/system-associated | Manifest only; real authorization is OS consent from `MediaProjectionManager.createScreenCaptureIntent` | Manifest declaration does not replace consent; platform necessity/status needs SDK-policy verification. | **Needs policy review** against target SDK docs. |
| `CAPTURE_AUDIO_OUTPUT` | Signature/protected | Manifest with lint suppression; dormant code instead uses AudioPlaybackCapture API | No functional grant to ordinary Play app; not needed for playback capture API. | **Remove**. |
| Storage permissions | None declared | App-specific external storage and SAF tree URI require no broad storage runtime permission | No | **Keep absent**. |
| `com.google.android.gms.permission.AD_ID` | Normal, API/policy dependent | Ads dependency/integration | No recording block; only relevant to ad behavior and disclosures. | **Needs policy review**; remove if ads/ID use removed. |

MediaProjection consent is requested in `HomeScreenFragment.requestScreenRecordingPermission` and is always system-mediated. The sheet does not request it. No permission rationale, “don’t ask again” recovery, denial message, or reduced-function path exists. Confirm Android 14 foreground-service start/consent timing and Play policy on real devices and against current official policy during rebuild planning.
