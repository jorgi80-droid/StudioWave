# StudioWave Continuation Master — 2026-10-15

## Current working state
- Repository: https://github.com/jorgi80-droid/StudioWave
- Branch: `ui/ableton-batch-viewport`
- Draft integration PR: https://github.com/jorgi80-droid/StudioWave/pull/15 — keep it draft until further functional/UI validation. Do not merge solely because CI is green.
- Latest code commit with green CI: `8a2cf36ab8ca200e0fa209a72854845f04fae282`
- Green CI run: https://github.com/jorgi80-droid/StudioWave/actions/runs/38748221560
  - `Build debug APK`: passed
  - `Run unit tests`: passed
  - `Upload debug APK`: passed
- Artifact: `StudioWave-debug-apk`, artifact ID `11979427813`, size 17,508,045 bytes; extracted APK length 18,154,440 bytes.
- APK SHA-256: `01778e3d85f8f71c3def99af5b75e2a52bafe7725538e84ace070516453a6dfc`
- Extracted APK for this build: `/mnt/data/StudioWave-debug-2026-10-15.apk` (local assistant runtime only). The canonical repository artifact is the GitHub Actions artifact from run `38748221560`.
- Key file blobs at this commit:
  - `MainActivity.kt`: `c7d0b087e4c87cc632c1f584bcfe60ad4c8ef085`
  - `ProjectModel.kt`: `79cfa53a9b627cb60d481273afac91c1ba9eb606`
  - `ArrangementPlaybackEngine.kt`: `c10548572bdb35ba9cda068a3b031f50f8cf0bcd`
  - `TrackDeviceDsp.kt`: `1c8db6204cc4ce02a1d2b6bbdfab205847bec919`
  - `TimelineViewport.kt`: `339ecd9899d5e33341a4514e705c4e7da614d132`
  - `TimelineViewportTest.kt`: `3e1f284d9c0fedffcff7c62269b2b9a5dcce3b1e`

## Root cause resolved in this turn
- Earlier device panel commits inserted composables inside the DirectWave factory-sample loop. That moved declarations into a local scope, causing unresolved `TrackDevice`/device symbols and `private is not applicable to local function` compile errors.
- Another commit accidentally reintroduced the broken `MainActivity.kt` because its Git tree was created from an older base tree. The branch was repaired by creating a new commit with the correct current HEAD parent/tree.
- Latest commit is based on the known-green MainActivity baseline; new effects composables now sit after the full DirectWave sampler and the `BuiltInStudioSample` data class, before the next independent top-level DirectWave helper.
- A structural pre-commit check verified the brace balance is zero for DeviceDock, DirectWaveDevicePanel, and the new FX composable block.
- Prior viewport-test failure has also been corrected: obsolete `visibleSubdivisionTicks()` was replaced by `gridSubdivisionTicks()`; mixed Double/Float assertion and inverse tick conversion were corrected in `TimelineViewportTest.kt`.

## What is implemented now
- Existing StudioWave workspaces and DirectWave panel retained.
- Device View has an `ADD FX` dialog and horizontal insert cards connected to each track's persisted `devices` list.
- FX UI exposes Equalizer, Parametric EQ Eight, Reverb, Delay, Compressor, Saturator, Filter and Limiter. Each card offers ON/OFF, removal and parameters matching the existing DSP's keys.
- `ProjectModel.kt` serializes devices/enabled state/parameters.
- `ArrangementPlaybackEngine.kt` routes enabled track insert processors into the render path using `TrackDeviceDsp`, maintaining per-track DSP state and adding the processed-vs-dry delta to the master bus.
- Arrangement timebase / pane viewport supports shared time mapping, anchored zoom, and 1800-bar cap.
- The APK and CI build have been verified by Android CI, but real device behavior still needs a physical Android test.

## Known incomplete items — do not misrepresent as finished
- New FX insert controls are not a claim of full Ableton Live effect parity; the DSP implementations are basic StudioWave algorithms.
- Full Analyzer and Multi FX are deliberately *not exposed as active* yet. Analyzer needs an actual FFT/spectrum data path; Multi FX needs a tested serial composition.
- SuperSaw, MiniSynth, and GMS do not yet have complete engine-backed instrument implementations and must not be presented as working synths. The current DirectWave UI previews/plays samples through its own sampler, and integration of its voices into arrangement MIDI rendering remains a next step.
- Current device view styling is a step toward the supplied Ableton reference; it is not pixel-identical or yet a complete Ableton Live Mobile-level UI.
- On-device tests are still needed for: install/startup, landscape immersive mode, playback/audio output, audible FX parameter response, project save/reopen, and touch-target usability.

## Next actions for the next chat
1. Start from branch `ui/ableton-batch-viewport`, commit `8a2cf36ab8ca200e0fa209a72854845f04fae282`. Do not reset to main and do not start over.
2. Install/test the APK from green run `38748221560` on Android. Verify arrangement playback continues while editing and FX parameter changes audibly affect the chosen track.
3. Add DSP-focused tests for EQ response, compressor gain reduction, limiter ceiling, FX order, no dry-signal double count, parameter persistence and render-path routing.
4. Improve FX cards toward the screenshot: compact knobs, response curves for EQ, a visible level/gain-reduction meter where data exists, stronger Live-style panel hierarchy, narrow separators and keyboard/touch-friendly controls.
5. Implement Full Analyzer only once a real FFT/spectrum path and frame update policy exist. Implement Multi FX only after serial routing tests.
6. Design SuperSaw/MiniSynth/GMS as genuine instruments and connect MIDI note events to actual instrument voices in arrangement playback, including note-off/envelope behavior and tests. Don't add merely decorative selectors.
7. Refine Arrangement, Clip View, Piano Roll, Mixer and sample browser against the user's Ableton Live design references while preserving FL Studio Mobile's fast touch workflow.
8. After a stable next batch, update this file and PR #15 body with tested scope, commit SHA, CI run, and known limitations.

## Honest status
Latest code/test commit `8a2cf36ab8ca200e0fa209a72854845f04fae282` builds and passes the repository unit tests in CI run `38748221560`. The DirectWave-scope regression was corrected and standalone FX insert controls were added. Full Analyzer, Multi FX, SuperSaw, MiniSynth, GMS, true DirectWave-to-MIDI render integration, visual parity and physical-device behavior are still incomplete.
