# Milestone 4: recording domain contracts

## Scope and exclusions

This milestone adds a pure Kotlin recording domain under
`com.droidnova.screenrecorder.domain.recording`. It defines configuration, capability,
input, failure, and lifecycle contracts only. It does not use Android APIs and does not
record, request consent or permissions, start a service, schedule timers, access storage,
or change the accepted Compose UI and navigation.

## Domain model

`RecordingSettings` is the complete immutable request: preset identity, audio mode,
resolution, frame rate, bitrate in bits per second, countdown, and maximum-duration
policy. Preset identities are Data saver, Balanced, High quality, and Custom. They do
not imply device support; every resolved settings instance must be validated.

Resolution, frame rate, bitrate, countdown duration, maximum duration, and bitrate
ranges enforce their structural invariants at construction. Audio deliberately supports
only None, Microphone, and Device audio; mixed capture is not an option. Countdown None
still travels through the Countdown state so a future runtime can immediately report
completion without adding a second state path.

`RecordingCapabilities` contains resolution-specific video profiles. Each profile binds
its supported frame rates to a bitrate range, avoiding the false cross-product produced
by unrelated flat lists. Audio modes and pause/resume availability are explicit.
Validation returns either `Supported` or a typed unsupported resolution, frame-rate,
bitrate, or audio reason. It never changes or downgrades requested settings. Structural
invalidity is distinct: invalid value types cannot be constructed.

## Commands and events

Commands express caller intent: Start, Cancel, Pause, Resume, Stop with a reason, and
Acknowledge. Events express engine lifecycle facts: preparation ready, countdown
finished, finalization succeeded/failed, and fatal failure. Keeping both families under
a common reducer input while retaining separate sealed types prevents runtime facts
from being confused with user requests.

Stop reasons cover user request, countdown cancellation, maximum duration, low storage,
projection revocation, screen lock/system capture ending, thermal protection, and
application shutdown. Failures cover invalid/unsupported settings, consent, required
permission, projection, video/audio initialization or runtime, output initialization,
storage, finalization, and unexpected internal domains. They contain enums or typed
reasons only—never exception text, paths, filenames, URIs, tokens, app names, or content.

## Authoritative state machine

| Current state | Accepted input | Next state |
|---|---|---|
| Idle | Start | Preparing |
| Preparing | Preparation ready | Countdown |
| Preparing | Cancel | Idle |
| Preparing | Fatal failure | Failed |
| Countdown | Countdown finished | Recording |
| Countdown | Cancel | Stopping (countdown cancelled) |
| Countdown | Stop(reason) | Stopping(reason) |
| Recording | Pause | Paused |
| Recording | Stop(reason) | Stopping(reason) |
| Recording | Fatal failure | Failed |
| Paused | Resume | Recording |
| Paused | Stop(reason) | Stopping(reason) |
| Paused | Fatal failure | Failed |
| Stopping | Finalization succeeded | Completed |
| Stopping | Finalization failed | Failed |
| Completed | Acknowledge | Idle |
| Failed | Acknowledge | Idle |

The reducer is side-effect-free and deterministic. Every accepted result contains the
previous and next immutable state. Session settings remain attached from Preparing
through terminal state, with stop reasons retained by Stopping and Completed and typed
failures retained by Failed.

Any input absent from the table returns `Rejected` with
`InputNotAllowedInCurrentState`, the rejected input, and the exact unchanged state.
It does not throw. This defines repeated Stop, Pause, and Resume behavior along with
premature events and acknowledgement. There are no parallel recording booleans.

## Immutability and session lifecycle

Contracts use data/value classes, enums, objects, and sealed interfaces. Capability
collections are defensively copied and exposed through read-only collection types.
There are no timestamps, generated IDs, mutable state, parcelables, or global state, so
equality is deterministic.

Completed and Failed can only return to Idle through Acknowledge. Start is rejected in
both terminal states. A later runtime must therefore acknowledge the old session and
obtain fresh MediaProjection consent before dispatching a new Start; these contracts do
not retain consent.

## Consumption by later milestones

An Android implementation should translate platform capability discovery into
`RecordingCapabilities`, validate explicit settings, and feed commands and lifecycle
events to this reducer. It should perform side effects only after an accepted transition
and keep `RecordingState` as its sole lifecycle authority. Platform exceptions must be
sanitized into the typed failure taxonomy without carrying sensitive values.

## Validation

The JVM suite covers every accepted table row; invalid inputs in every state; repeated
Stop, Pause, and Resume; terminal acknowledgement/fresh-start rules; deterministic
results and equality; value boundaries; defensive copies; capability combinations;
audio choices; and the absence of output identifiers. Codex attempted
`:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and
`:app:assembleRelease` with JDK 17. Each exited 1 before Gradle started because the
environment proxy rejected the Gradle 8.13 download with `HTTP/1.1 403 Forbidden`; no
task result is claimed. `git diff --check` passed. Before the focused commit, status
contained only the new Milestone 4 domain, test, and documentation paths.
