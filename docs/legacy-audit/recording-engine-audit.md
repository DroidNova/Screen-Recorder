# Recording engine audit

## Verified trace and format

Home gates on microphone, notifications (API 33+), and overlay in `ServiceLauncher.areAllPermissionsGranted`, then requests MediaProjection. Accepted result starts `ScreenRecordingService` with `ACTION_START_SERVICE`. The sticky service calls `ServiceHelper.startForegroundServiceWithNotification`, enters foreground, overlays a fixed countdown, obtains a projection, registers `MediaProjection.Callback.onStop`, prepares `MediaRecorder`, creates a virtual display, starts, and reports timer callbacks.

| Property | Verified implementation |
|---|---|
| Container/codecs | MPEG-4 `.mp4`; H.264 video; AAC audio (`setupMediaRecorder`) |
| Active audio | `MediaRecorder.AudioSource.MIC` always; preference `selected_audio_input_type` and `video_microphone_state` are not consulted |
| Internal audio | Not active: `setupAudioCapture()` call is commented. Dormant playback capture supports API 29+ conceptually but is not integrated/muxed. `CAPTURE_AUDIO_OUTPUT` cannot make it active. |
| Defaults/options | 12 Mbps (options 3/6/8/10/12/15), 720p (360/480/720/1080), 60fps (30/60) |
| Dimensions | `getAdjustedResolution` modifies target to screen aspect. Recorder uses adjusted size, but virtual display is created with physical display dimensions; surface scaling is implicit. Rotation is not handled. |
| Output | `getExternalFilesDir(Environment.DIRECTORY_MOVIES)/ScreenRecording<epoch>.mp4` |
| Threading/timer | setup/actions on service main thread; `CountDownTimer`; timer is a new unowned `CoroutineScope(Dispatchers.Main)` loop; dormant audio encoder uses raw Thread |
| Foreground/version | notification channel API 26+; typed microphone+mediaProjection `startForeground` API 30+, ordinary call below; manifest types declared; min API 24 |
| Pause/resume | `MediaRecorder.pause/resume` called without API guard (methods require API 24, equal to min); notification actions only |
| Restart | returns `START_STICKY`, but projection consent extras/state are not reconstructable from a null restart; null intent does nothing |
| Finalization | try recorder stop/release and dormant AudioRecord release, then unconditional virtual display release, projection stop, callbacks, foreground removal/self-stop |

## Classified findings

### Verified implementation facts

* Storage/list refresh is pull-based when Recordings opens; no observer is actually initialized despite fields in `MainViewModel`.
* Notification offers Pause/Resume and a “Save” action that dispatches stop. `openHomeIntent` and `custom_notification.xml` are unused.
* No storage-space check, maximum-duration enforcement, thermal callback, orientation response, segmentation, process recovery, or explicit incomplete-file cleanup exists.

### Verified defects

| Severity | Defect / user impact | Evidence |
|---|---|---|
| Critical | Partial setup can call `stopScreenRecording`, then execution continues and accesses uninitialized recorder/surface/display/projection; cleanup also unconditionally releases late-init display/projection. A startup failure may crash and leave an incomplete file/FGS. | `setupMediaRecorder`, `setupVirtualDisplay`, `stopScreenRecording` in `ServiceHelper.kt` |
| High | Internal audio claim is false in active behavior; microphone is always recorded, while playback capture is commented out. Users expecting device audio receive mic audio instead. | `ServiceHelper.setupMediaRecorder/startScreenRecording` |
| High | Sticky restart has no restoration path, so killed-process recording/state cannot safely resume. | `ScreenRecordingService.onStartCommand` returns `START_STICKY`; null intent ignored |
| High | Virtual display requested at physical dimensions while encoder surface is configured to adjusted dimensions, increasing device/codec incompatibility risk. | `setupMediaRecorder` vs `setupVirtualDisplay` |
| Medium | Timer totals are not reset at stop (`totalElapsedTime`, `isPaused`), so a second recording in the same helper can inherit elapsed state; unscoped loops are not explicitly cancelled. | timer fields and stop method |

### Probable risks requiring device testing

* Unsupported size/FPS/bitrate combinations may fail `prepare` or `start`; no capability negotiation.
* `MediaRecorder.stop` can throw for short/invalid output; file is not removed or marked incomplete.
* Overlay add/remove can throw if permission changes or WindowManager state races.
* Recursive projection-stop callback/release ordering and pause/stop races may double-enter cleanup.
* Long recording may exhaust storage, exceed receiving-app/share constraints, overheat, or be killed; no guard exists.

### Missing features

Configurable countdown, internal-audio production path, audio-off selection, storage threshold, thermal/low-storage response, long-record segmentation/protection, robust atomic finalization, recovery marker, rotation handling, and process-death restoration are absent.

### Unknown

Actual codec acceptance across devices, microphone inclusion during pause, notification behavior when denied, MediaProjection policy/dialog wording by OS, screen rotation output, partial-file playability, and behavior under memory/storage/thermal pressure require the manual matrix.
