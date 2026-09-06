# Feature matrix

| Feature | Status | Evidence / qualification |
|---|---|---|
| Home / Recordings / Settings tabs | Present | `nav_graph.xml`, `menu_bottom_home.xml`, `MainActivity.setUpNavigation` |
| Storage indicator | Present | `HomeScreenFragment.updateStorageInfo`; circular percentage and free GB |
| Quality / resolution / FPS | Present | Selector dialogs; 3–15 Mbps, 360p–1080p, 30/60 fps |
| Timer and Start/Stop | Present | `tv_recording_time`; service callback; one text button |
| Recording list / empty state | Present | `RecordingsScreenFragment`, `VideoAdapter`, `tv_no_videos` |
| Batch selection/delete | Present | long press selection and `deleteSelectedVideos` |
| Play / save folder / share / rename / delete | Present | recording-options bottom sheet handlers |
| Permission sheet | Present | audio, overlay, notifications; hides granted rows |
| Notification actions | Present | Pause/Resume and misleadingly titled Save (actually stop) in `ServiceHelper.createNotification`; custom layout is unused |
| Pause/resume | Present, notification only | Service actions; no visible in-app pause control |
| Internal audio | **Not active** | `setupAudioCapture()` is commented out; active recorder always uses microphone |
| Rate / Share / Privacy / Report Bugs / Version | Present | Settings layout and fragment; version is display-only |
| WhatsApp | Hidden/unavailable | row `visibility="gone"`; divider hidden; listener exists |
| Configurable countdown | Missing | fixed `CountDownTimer(3000, 1000)` |
| Thermal/storage/long-duration safeguards | Missing | no engine checks, limits, callbacks, or recovery implementation |

This matrix describes code paths only; availability and rendering must be device-tested.
