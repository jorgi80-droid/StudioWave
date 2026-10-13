# StudioWave — P0/P1 Status Audit

## Implemented in the current Android-native source
- Kotlin/Jetpack Compose landscape workspaces and the Arrangement timeline.
- Shared musical timebase/viewport helpers for PPQ, meter, snap divisions, coordinate mapping and zoom anchors.
- JSON project model with entity limits, autosave to app-private storage, and bounded .sdaw UTF-8 JSON import/export.
- Arrangement playback path that decodes source-linked audio off the real-time thread and schedules audio clips, synthesized MIDI notes and drum steps.
- Per-track volume, pan, mute/solo and master gain state in the arrangement mixer.
- Audio-source PCM waveform preview decoding with cancellation and caching.
- Arrangement ruler seeking that resumes playback from the selected musical tick.
- Playhead position from the AudioTrack playback head, ruler/track cursor rendering and horizontal follow-scroll during playback.
- CI workflow that builds the debug APK and runs local unit tests.

## Remaining gaps (not implied to be complete by a successful build)
- Audio recording and recording permissions/workflow.
- Loop/selection regions, split/trim/time-stretch and clip looping.
- Production instrument/plugin hosting and production DSP effect chains.
- Low-latency Oboe backend (current output still uses AudioTrack), measured underrun/latency tests and Eco/Standard performance profiles.
- Project bundle with copied/embedded source media; .sdaw currently stores JSON and source URI references only.
- Mixdown/WAV export, recovery/versioned backups, settings/log screen, and real-device lifecycle/audio tests.
- Final UX verification on Android hardware, especially long projects, large source files and repeated start/stop/import operations.

## Acceptance and verification
1. assembleDebug must pass for the exact integration commit.
2. testDebugUnitTest must pass for the same commit.
3. Device-only checks must be recorded separately: landscape/system bars, imported URI access, play/stop, lifecycle cleanup, audio glitches and long-session memory.
4. Do not report latency, crash-free operation or production readiness without measured device evidence.

## Integration strategy
Group related changes into coherent commits. A single integration commit is reasonable for project I/O, model validation, regression tests and documentation; completing every production DAW feature in one commit is not safe or realistic.
