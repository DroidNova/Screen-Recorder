# Project rules

- Preserve the `com.droidnova.screenrecorder` application ID.
- Work milestone by milestone, and read `docs/legacy-audit/` when legacy compatibility is relevant.
- Use Jetpack Compose and Material 3; do not reintroduce the legacy recorder.
- Use immutable UI state and `StateFlow` when state is added.
- Keep the recording service authoritative once it is implemented.
- Never log filenames, URIs, projection tokens, or captured content.
- Never request all-files access, and do not add ads to recording states.
- Run build, test, lint, and diff checks before completion.
- Never commit secrets or signing files.
