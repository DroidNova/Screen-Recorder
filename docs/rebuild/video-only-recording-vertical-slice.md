# Milestone 5: video-only screen recording vertical slice

## Scope and exclusions

This milestone implements one Android screen-video path: fresh system consent, a media-projection foreground service, one AVC surface encoder, one MP4 muxer, and safe output finalization. It has no audio, pause/resume UI or execution, notification actions, overlay, settings, recording-list integration, playback, migration, ads, analytics, or alternate recording engine.

## Consent and foreground-service startup

Start is available only for authoritative Idle state. `MainActivity` uses the Activity Result API with the unmodified platform capture intent and a narrow in-flight launcher guard. Cancellation starts neither service nor output. A successful result is passed once, in-memory, to an explicit service intent and is never logged or persisted. The app then backgrounds its task.

The service rejects a start unless its domain state is Idle, moves to Preparing, immediately creates its channel and calls `startForeground`, and only then calls `getMediaProjection` once on its encoder worker. It returns `START_NOT_STICKY`; there is no restart recovery. Android 14's app-window/full-display system choice is not overridden.

## Ownership and one-session invariants

`ScreenRecordingService` owns the `StateFlow<RecordingState>` and elapsed `StateFlow`. Compose observes these read-only flows; button behavior comes from the sealed state rather than button text. Elapsed time derives from service-owned `SystemClock.elapsedRealtime`. Consent data, projection, codec, surfaces, output identifiers, and Context never enter observable state.

Each accepted grant creates one projection and one fixed-canvas virtual display. The projection callback is registered before `createVirtualDisplay`, whose dimensions exactly match the selected encoder configuration. Projection callback Stop and Home Stop enter the same atomic, once-only finalization path. No display is recreated after rotation.

## AVC configuration and pipeline

The service enumerates AVC encoders, requires `COLOR_FormatSurface`, and obtains published video capabilities. Hardware encoders rank first; API 24–28 use a deterministic generic software-codec-name classification rather than a manufacturer allowlist. Equal classes sort by codec name. Pure selection logic aspect-fits landscape within 1280×720 or portrait within 720×1280, aligns dimensions downward, never upscales, tries lower aligned candidates, requires size/rate support, and clamps the 4 Mbps target within codec bitrate bounds. The fixed video configuration is 30 FPS, two-second key frames, no audio, no countdown, and unlimited domain duration.

The codec input surface feeds the sole virtual display. A single worker drains with bounded 10 ms dequeue waits. Codec-config buffers are skipped. The muxer receives its video track and starts only after output-format change; samples are written only afterward with monotonic presentation timestamps, and every buffer is released exactly once. Stop signals surface EOS and drains to EOS with a five-second deadline off the main thread.

## Output lifecycle

On Android 10+, output is inserted in primary `MediaStore.Video` under `Movies/Screen Recorder` as `video/mp4` with `IS_PENDING=1`. A descriptor remains open for the muxer lifetime. The pending entry is published only after EOS, at least one video sample, a video track, successful muxer stop, and descriptor closure. Failure deletes only that in-process pending entry.

On API 24–28, output uses the app-specific external Movies directory without broad storage permission. It is application-owned and is not claimed to appear in the public gallery. Public legacy-library integration is deferred.

The centralized generator produces safe ASCII `ScreenRecording_<epoch>_<sequence>.mp4` names with exactly one extension. Names, paths, and URIs are never logged.

## Stop, failure, and cleanup

Normal Stop transitions Recording → Stopping, requests encoder EOS, completes muxer finalization, then transitions to Completed. Projection revocation uses typed `ProjectionRevoked` and the same bounded path. Atomic stop admission prevents repeated Stop from finalizing twice.

The pipeline owns nullable resources rather than unsafe `lateinit` fields. Central release handles virtual display, callback/projection, codec/surface, muxer, descriptor, and output at most once. Muxer and codec are stopped only after reaching their started states. Invalid/empty output is discarded. Runtime categories map to safe typed domain failures; raw exception messages and sensitive values are not logged or shown. The UI surfaces localized terminal status before acknowledging back to Idle.

## Privacy and known limitations

There are no application logs, uploads, analytics, captured-app names, filenames, paths, URIs, tokens, pixels, or sample contents exposed. The notification contains only generic localized recording text and an immutable reopen intent.

The canvas is fixed for the session. Rotation/content resizing can letterbox and is not reconfigured. Process death may leave an unpublished pending row until Milestone 11 reconciliation. Milestones 6–12 retain pause/resume synchronization, audio, configurable countdown/settings, recordings library/playback/actions, orientation adaptation, migration, robust crash recovery, and long-session certification.

## Codex validation

Codex attempted `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and
`:app:assembleRelease` with JDK 17. Each exited 1 before Gradle started because the
environment proxy rejected the Gradle 8.13 download with `HTTP/1.1 403 Forbidden`.
Consequently no generated merged-manifest output was available; the source manifest was
inspected instead. `git diff --check` passed. Physical codec, projection, gallery, and
playback correctness is not claimed until the owner completes the checklist below.

## Physical-device checklist

1. Install/update debug without changing the package ID.
2. Open Home.
3. Confirm Start is enabled.
4. Press Start.
5. Confirm system MediaProjection consent appears.
6. Cancel consent.
7. Confirm no recording and no output.
8. Press Start again.
9. Grant consent.
10. On Android 14+, keep and test the default system app/full-display choice.
11. Confirm the app backgrounds promptly.
12. Confirm the foreground recording indication appears as allowed by the device.
13. Record moving content for at least 30 seconds.
14. Reopen the app.
15. Confirm Recording/Stop derives from the service.
16. Confirm elapsed time progressed.
17. Press Stop.
18. Confirm finalization does not freeze.
19. On Android 10+, confirm the MP4 appears in the gallery/media collection.
20. Play the complete recording.
21. Confirm video exists and there is no audio track.
22. Confirm duration is reasonable.
23. Confirm the file is non-zero and uncorrupted.
24. Confirm portrait content is not stretched.
25. Confirm return to Idle and fresh consent on the next session.
26. Repeat Start/Stop three times.
27. Confirm one output per session.
28. Rapidly tap Start and confirm one consent/session.
29. Stop another recording from the privacy chip where available.
30. Confirm service exit and ability to begin fresh consent.
31. Lock during recording on a platform that revokes projection on lock.
32. Confirm the app does not remain falsely Recording.
33. Swipe away the Activity during recording and confirm the service continues.
34. Confirm no microphone permission request.
35. Confirm no storage or overlay permission request.
36. Confirm Home, Recordings, and Settings navigation.
37. Confirm light/dark UI was not unintentionally redesigned.

Rotation during recording, pause/resume, notification actions, audio, and two-hour stability are not acceptance requirements for this milestone.
