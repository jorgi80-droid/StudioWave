# StudioWave Continuation Master — updated 2026-10-16

## Current working state
- Repository: https://github.com/jorgi80-droid/StudioWave
- Branch: `ui/ableton-batch-viewport`
- Draft integration PR: https://github.com/jorgi80-droid/StudioWave/pull/15 — keep it draft until further functional/UI validation. Do not merge solely because CI is green.
- Latest code baseline with verified green CI: `c61feedbaf83411a7afde5bdac362a5b2a104670` (documentation-only checkpoint commit; the prior implementation baseline `8a2cf36ab8ca200e0fa209a72854845f04fae282` also passed).
- Latest verified green CI run before the HTML audit documentation commit: https://github.com/jorgi80-droid/StudioWave/actions/runs/38748749046 (commit `c61feedbaf83411a7afde5bdac362a5b2a104670`; Build debug APK, Run unit tests, Upload debug APK all passed). Documentation and UI commits were followed by additional workflow runs; latest UI implementation run is recorded below.
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

## 2026-10-16 continuation update
- HTML reference audit committed: `docs/ABLETON_HTML_REFERENCE_AUDIT_2026-10-16.md` (commit `1d433eb1271e0f66999599669665f0ea0d01bb88`).
- Compact native rotary controls were added to EQ Three, Reverb and Delay FX cards while preserving their existing parameter keys and update callbacks (implementation commit `b229d7364cc9c0eb76869b64c753e6796d497f26`).
- PR #15 body was refreshed to reflect the actual integration state; PR remains open/draft.
- Latest CI run for the rotary-control implementation: `38778727549`, URL https://github.com/jorgi80-droid/StudioWave/actions/runs/38778727549. It was `in_progress` at the checkpoint update; check its final result before claiming the new change passes.
- Latest known completed-green workflow before these changes: `38748749046` on `c61feedbaf83411a7afde5bdac362a5b2a104670`; debug build, unit tests and APK upload passed.

- Audited the five uploaded HTML references and committed `docs/ABLETON_HTML_REFERENCE_AUDIT_2026-10-16.md` on this branch (commit `1d433eb1271e0f66999599669665f0ea0d01bb88`). It inventories external dependencies, UI elements, interactions, native Compose mapping, and limitations.
- The latest verified code branch head before this documentation commit is `c61feedbaf83411a7afde5bdac362a5b2a104670`; PR #15 remains open/draft and targets `main`.
- Re-inspection of the current `MainActivity.kt` confirms `DeviceDock`, `DirectWaveDevicePanel`, `BuiltInStudioSample`, and `TrackEffectDeviceCard` are separate declarations; the helper-inside-sample-list scope regression is not present in the current branch snapshot. The latest green CI validates compilation/tests for the prior head, not the newly added audit docs commit.
- HTML-to-source finding: Parametric EQ Eight currently exposes eight frequency-labelled gain sliders, but not per-band frequency/Q/type. It must not be described as a full parametric eight-band EQ until model + DSP support those parameters. DirectWave parameters currently persist in SharedPreferences; project-level persistence and arrangement MIDI voice integration remain unverified/incomplete.

## Root cause resolved in the earlier implementation
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

## Next actions
1. Continue on `ui/ableton-batch-viewport` and PR #15. Current audit-document commit: `1d433eb1271e0f66999599669665f0ea0d01bb88`; do not reset to main or start over.
2. Install/test the APK from green run `38748221560` on Android. Verify arrangement playback continues while editing and FX parameter changes audibly affect the chosen track.
3. Add DSP-focused tests for EQ response, compressor gain reduction, limiter ceiling, FX order, no dry-signal double count, parameter persistence and render-path routing.
4. Improve FX cards toward the screenshot: compact knobs, response curves for EQ, a visible level/gain-reduction meter where data exists, stronger Live-style panel hierarchy, narrow separators and keyboard/touch-friendly controls.
5. Implement Full Analyzer only once a real FFT/spectrum path and frame update policy exist. Implement Multi FX only after serial routing tests.
6. Design SuperSaw/MiniSynth/GMS as genuine instruments and connect MIDI note events to actual instrument voices in arrangement playback, including note-off/envelope behavior and tests. Don't add merely decorative selectors.
7. Refine Arrangement, Clip View, Piano Roll, Mixer and sample browser against the user's Ableton Live design references while preserving FL Studio Mobile's fast touch workflow.
8. After a stable next batch, update this file and PR #15 body with tested scope, commit SHA, CI run, and known limitations.

## Honest status
Implementation baseline `c61feedbaf83411a7afde5bdac362a5b2a104670` passed debug build, unit tests and artifact upload in CI run `38748749046`. The HTML audit documentation commit `1d433eb1271e0f66999599669665f0ea0d01bb88` was created afterward; its CI status must be checked separately. The DirectWave-scope regression was corrected and standalone FX insert controls were added. Full Analyzer, Multi FX, SuperSaw, MiniSynth, GMS, true DirectWave-to-MIDI render integration, visual parity and physical-device behavior are still incomplete.


## Visual design continuation — 2026-10-16
- Updated shared native palette in `StudioWaveSettings.kt` on commit `80e98ae94aa981a4414821cdee9efff5ebd3eb19`: graphite/near-black surfaces, thin dark borders, Ableton-inspired orange active accent, and distinct signal-state colors. The light and mid themes keep their own surface contrast while using an orange family accent.
- Refined `TrackEffectDeviceCard` in `MainActivity.kt` on commit `7f9a5d759be0c49ac273553a27f977032788fec2`: raised device header, enabled-state orange strip, square compact panel geometry, and orange EQ slider active track. Existing parameter keys, callbacks, enable/remove actions, and DSP routing were preserved.
- CI run for palette commit: https://github.com/jorgi80-droid/StudioWave/actions/runs/38780665798 (check final status before claiming pass).
- The latest UI card commit was pushed after that run was queued; its own workflow must be checked separately.
- Design implementation is now more consistently graphite/orange but is not claimed pixel-identical to Ableton Live. The user-provided HTML remains a visual reference; no proprietary Ableton source/assets were copied.
- Next: verify CI for the latest head; continue the same coherent pass across Arrangement/ruler/track headers and Piano Roll, preserving the shared musical viewport, clip data, hit-testing, and 1,800-bar cap. Then compare the running APK on a landscape Android device.


## Ableton-inspired track header pass — 2026-10-16
- Updated `TrackHeader` in `app/src/main/java/com/studiowave/app/MainActivity.kt` on branch `ui/ableton-batch-viewport`.
- Track headers now use a narrow full-height track-color rail, a track-color name strip with contrast-aware text, compact square M/S states, a small audio scratch affordance, and square reorder/resize handles. This follows the uploaded HTML reference's dark industrial surfaces, thin borders, colored track headers and compact controls while retaining existing callback wiring and track model state.
- Preserved track double-tap/long-press entry to Device View, mute/solo model updates, audio scratch gesture, track reorder and track-height resizing. No timeline tick/viewport math or clip storage was changed.
- Validation required for this commit: Android CI debug build + unit tests, then visual and gesture checks on a landscape Android device. Do not claim pixel-identical Ableton parity or physical-device validation before those checks.

## Arrangement ruler refinement — 2026-10-16
- Updated the ruler's bar-label density threshold from 94 dp to 52 dp in `MainActivity.kt`, so individual bar numbers become visible at a more practical medium zoom instead of only appearing every fourth bar at common zoom levels.
- This is a rendering-only adjustment; musical ticks, viewport mapping, clip positions, snap, gestures, and the 1,800-bar cap are unchanged.
- Commit: `0a7bc028dbcbb104d9e7f434549003f0ca1807f9`.
- Validation: check the commit's Android CI result before treating it as verified; physical-device comparison is still pending.


## Integrated Arrangement + Piano Roll visual pass — 2026-10-16
- Source changes on `ui/ableton-batch-viewport`:
  - `de9461f73b542befccd0883c1dc1df638b1750a4`: tightened Arrangement clip body/outline geometry to square Live-inspired edges; MIDI clip thumbnails now include a bar line at tick zero and stronger bar guides; Piano Roll notes use flat rectangular geometry and velocity-dependent opacity while selected notes remain orange.
  - `45f7932659c367b3003b0377d31cd067a4ee7ece`: added regression coverage for zoom after horizontal scroll, fractional anchor preservation, tick/pixel round trips, clip width invariance, and playhead visibility near arrangement endpoints.
- The changes do not modify clip musical positions/durations, PPQ, snap math, hit testing, the shared viewport transform, or the 1,800-bar limit.
- CI must be checked for the latest branch head before reporting build/tests/artifact success. Physical Android landscape checks remain required for clip visual fit, touch interactions, playback-follow and Piano Roll note editing.
- Current scope remains an Ableton Live-inspired native implementation, not a claim of pixel-identical proprietary UI. Do not copy Ableton code/assets or claim unsupported DSP/instruments as complete.
