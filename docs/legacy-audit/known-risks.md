# Known risks

## Verified risks

| Severity | Risk and evidence | User impact | Preservation/rebuild requirement |
|---|---|---|---|
| Critical | Non-state-aware cleanup accesses `lateinit` recorder/display/projection after partial setup (`ServiceHelper`) | Startup/encoder failures may crash, strand notification, or leave corrupt files | Preserve failing traces; rebuild as explicit state machine with idempotent cleanup |
| High | Active audio is always microphone; internal capture setup is commented and audio preferences ignored | Unexpected ambient/private audio; promised system audio absent | Clearly label modes, request mic contextually, device-test each mode |
| High | Recordings live in app-specific external storage and uninstall normally removes them | Users can lose all unexported recordings on uninstall | Warn users; non-destructive legacy discovery/export; migration/update tests |
| High | No low-space, thermal, duration, segmentation, or partial-file safeguards | Long/large recordings can fail late, overheat, fill disk, or become unusable | Add future acceptance criteria and failure recovery; retain original files |
| High | Sticky service cannot reconstruct projection or state after process death | Capture/control state may disappear or restart inertly | Test kill paths; design explicit session persistence/recovery |
| High | Batch delete ignores delete results and announces success | Users may believe files are gone when some remain | Preserve evidence; rebuild per-item result reporting |
| Medium | SAF export produces `.mp4.mp4` and does not resume after first folder choice | Confusing filenames; extra user steps; reported large-file friction | Preserve both legacy name patterns; test large streaming export |
| Medium | Consent denial has no feedback; permission sheet has no rationale/permanent denial recovery | Users are confused or cannot discover how to proceed | Capture denial states; contextual education in rebuild |
| Medium | Encoder/virtual display dimensions differ and choices lack capability checks | Device-specific glitches or recording failure | Device compatibility matrix and negotiated settings |
| Medium | WAV is listed but player rejects it; uppercase extension behavior differs; rename can relabel audio as MP4 | Files appear but cannot play or become misleading | Migration classifier must inspect media, not trust extensions |
| Low | Notification says “Save” but merely stops; icons are delete icons | Controls seem unclear/unavailable | Capture notification; use semantic action labels/icons later |

## Unknowns requiring evidence

Crash/ANR prevalence, affected devices, Play production version/history, permission denial rates, background/lock-screen stability, actual large-file ceiling, codec combinations, rotation, incoming calls/audio focus, backup restore, Android 14+ FGS policy behavior, and accessibility must not be treated as verified defects until device/console evidence exists.
