# StudioWave Continuation Checkpoint — 2026-10-16

## Repository state verified at continuation start
- Repository: https://github.com/jorgi80-droid/StudioWave
- Branch: `ui/ableton-batch-viewport`
- Head at audit: `a6efe041597407f503bf71cedc1a2c2f02cb4bea`
- Draft PR: https://github.com/jorgi80-droid/StudioWave/pull/15
- PR remains draft; do not merge until physical-device validation is complete.

## Latest verified CI for the audited HEAD
- Run: https://github.com/jorgi80-droid/StudioWave/actions/runs/38788281307
- Head SHA: `a6efe041597407f503bf71cedc1a2c2f02cb4bea`
- Conclusion: success.
- Build debug APK: passed.
- Unit tests: passed.
- APK artifact upload: passed.
- Artifact: `StudioWave-debug-apk`, ID `11998583840`, 17,535,086 bytes, SHA-256 digest `ca3fd2fec4e298004cd25e4dc374f9f3cb5e0cf57d2f71e68f5a5b83f2a37886`.
- This proves CI build/tests only; physical Android landscape validation remains outstanding.

## Source audit findings
- The current MainActivity source contains standalone `DeviceDock`, `DirectWaveDevicePanel`, `TrackEffectDeviceCard`, and `DeviceParameterSlider` composables.
- FX insert cards are connected to the track's device list and project-change callback. Preserve those callback and serialization paths during visual changes.
- The FX parameter UI still uses generic sliders for several controls, while the supplied HTML references emphasize compact rotary knobs, grouped values, state LEDs, and fine dividers. Next visual batch should upgrade suitable FX controls to compact rotary controls without changing parameter keys/ranges or DSP routing.
- Parametric EQ Eight model/DSP supports frequency, Q, and gain per band, but its complete UI and processor behavior must be audited together before claiming full parametric EQ parity.
- Keep Full Analyzer and Multi FX unavailable as active processors until real FFT and serial processing are implemented and tested.
- Keep the shared TimelineViewport/timebase and 1,800-bar arrangement cap unchanged unless a separately tested functional defect requires a change.
- Do not claim DirectWave settings are project-serialized or its voices are integrated into arrangement MIDI rendering without proof.

## Next implementation batch
1. Make a targeted, reviewable FX/device control visual pass: compact rotary controls where they suit the parameter, readable numeric values, clear enabled/bypass state, consistent graphite surfaces and thin borders.
2. Preserve existing parameter keys, update callbacks, device enable/remove behavior, project serialization, and audio processing order.
3. Add or update tests for parameter normalization/persistence and relevant DSP behavior if functional paths change.
4. Verify the diff and ensure the change is based on the latest branch HEAD, then confirm Android CI's debug build, unit tests, and APK upload.
5. Physical-device checks remain required for landscape layout, touch accuracy, save/reopen, playback, and audible FX response.

## Visual and licensing constraints
- Ableton Live is the visual reference; FL Studio Mobile informs touch workflow.
- Reimplement using native Compose and original StudioWave code. Do not copy proprietary Ableton code or assets.
- Do not use decorative graphs as proof of real audio analysis, and do not present incomplete instruments/processors as fully functional.

## FX device faceplate visual pass — 2026-10-16
- Commit: pending CI for the FX device faceplate pass.
- The insert card now uses a compact squared faceplate with a 3 dp enable strip, bordered header, tighter padding, and explicit enabled/bypassed footer state.
- Rotary controls now use the Live-orange value arc and a bordered numeric readout plate. Parameter ranges, parameter keys, and update callbacks are unchanged.
- EQ Eight's band-gain overview connects bands in ascending frequency order so moving band frequencies cannot make the line double back. It remains an overview of stored band gain, not an FFT or a summed filter response.
- TimelineViewport, arrangement timebase, clip coordinates, and the 1,800-bar limit were not changed.
- CI status before this pass: run [38789057427](https://github.com/jorgi80-droid/StudioWave/actions/runs/38789057427) failed at Android SDK/NDK setup before Gradle build/tests; inspect/retry this infrastructure failure separately. The new visual commit requires its own complete build, unit-test, and APK-artifact run.
- Physical-device visual/touch validation remains outstanding.



## Arrangement track/clip visual hierarchy pass — 2026-10-16
- Commit: [6bc05a51](https://github.com/jorgi80-droid/StudioWave/commit/6bc05a51529f18522c74c6492f6c16caf2866acf).
- Track-name labels now use the graphite raised surface with a fine border; track identity remains visible through the dedicated color rail rather than a full-width saturated name block.
- Arrangement clips now use a restrained tinted body, a stronger track-color title strip, and a clearer non-selected accent outline; selected clips retain the orange focus border. This follows the Live-inspired hierarchy of colored clip headers over darker clip content while preserving the existing note/drum/audio preview rendering.
- Only Compose surface styling changed. No timeline timebase, viewport transforms, clip hit-testing, snapping, drag/resize callbacks, playback, or 1,800-bar limit was intentionally changed.
- The source edit is committed, but CI for this commit is pending. Verify debug APK build, unit tests, and artifact upload before treating the batch as validated. Physical landscape-device visual/touch checks remain outstanding.


## Mixer channel header hierarchy pass — 2026-10-16
- Commit: [9edbfa0f](https://github.com/jorgi80-droid/StudioWave/commit/9edbfa0fff61e1f3fba3a1f181ca8d16f02d4e3b).
- Mixer track headers now use the graphite canvas surface, a thin border, neutral readable track-name/type labels, and a narrow rail using the track's existing color. This avoids full-width saturated header blocks while preserving track identity.
- Only header presentation changed. Mute/solo callbacks, volume mapping, fader interaction, meter rendering, pan and project model behavior were not intentionally changed.
- CI and physical landscape-device verification for this commit are still pending; do not mark this batch validated until debug APK build, unit tests and artifact upload succeed.


## FL Studio Mobile touch-workflow pass — 2026-10-16
- Source references reviewed: official Playlist, Channel Rack, Editors, Controllers, Home Panel and Settings manuals supplied by the user. Key interaction patterns adapted for StudioWave are direct tap actions, long-press contextual actions, double-tap clip-to-editor navigation, generous clip/note resize hit areas, draggable velocity controls, and horizontally reachable editor controls on narrow landscape screens.
- Commit [dd70dab6](https://github.com/jorgi80-droid/StudioWave/commit/dd70dab64e7b68cbc6dd1ac248bcc64a2cdc3978) makes the Piano Roll top action strip horizontally scrollable instead of compressing/overflowing actions, increases its primary touch controls from 23–25 dp to 32 dp, and adds a visible trailing grip to selected MIDI notes to communicate resize affordance. The existing resize hit-zone, gesture transform, snap mapping, note model and project callbacks are unchanged.
- This batch is based on the actual branch source at `app/src/main/java/com/studiowave/app/MainActivity.kt`; no timeline viewport/timebase or 1,800-bar limit changes were made.
- CI for the implementation and checkpoint must pass debug APK build, unit tests, and APK artifact upload before the batch is considered validated. Physical landscape-device touch/visual validation remains outstanding.
- Reference URLs: Playlist https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_Playlist.htm ; Rack https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_Rack.htm ; Mixer https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_Mixer.htm ; Editors https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_Editors.htm ; Controllers https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_Controllers.htm ; Home Panel https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_HomePanel.htm ; Settings https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/FL%20Studio%20Mobile_HomePanel_Settings.htm .


## Piano Roll visual hierarchy pass — 2026-10-16
- Commit [de57caac](https://github.com/jorgi80-droid/StudioWave/commit/de57caac6f428b985a48fd7833f63e5f940aa3ea) adds subtle pitch-row shading for black-key semitones, faint octave-C landmarks, alternating phrase-bar tint, and clearer orange bar boundaries in the Piano Roll grid.
- This is a paint-only change: note coordinates, hit-testing, selection, resize gestures, snapping, MIDI model updates, and timeline viewport math were not intentionally changed.
- Visual principles follow Ableton Live 12's MIDI Note Editor/grid hierarchy while keeping the mobile touch controls from FL Studio Mobile. No Ableton assets or code are copied.
- Validation is pending: latest-head Android CI must pass debug APK build, unit tests, and artifact upload. Physical Android landscape validation remains required.


## Device-chain visual pass — 2026-10-16
- Commit [31d096c4](https://github.com/jorgi80-droid/StudioWave/commit/31d096c40030a4bbf5da1f3c111341338c6a6c6c) adds compact numbered FX insert slot markers and directional chain cues before each effect card in Device View.
- The markers clarify the serial order of the existing instrument/source followed by effect inserts, using the existing palette and narrow landscape-friendly spacing. They do not add, remove, reorder, enable, or process devices; existing device callbacks, parameter model and audio routing are unchanged.
- Latest-head CI lookup returned no workflow runs for this commit, so build, unit tests and APK artifact upload are not verified. Physical Android landscape testing remains outstanding; keep PR #15 as draft.


## Mixer / Arrangement / Device Chain hierarchy pass — 2026-10-16
- Mixer strip width is reduced from 164 dp to 142 dp so more channels are visible in landscape while the strip still preserves its touchable fader and pan controls. Track headers now include a small color-linked index.
- Stereo peak and RMS meter graphics now ease toward incoming meter readings (85–100 ms). This is presentation-only smoothing; it does not alter the audio signal, meter source, gain, fader mapping, or DSP.
- Device Chain now displays a numbered `01 SRC` source slot before the instrument/drum/audio source module, followed by numbered `02 FX`, `03 FX` insert slots. Existing device order, add/remove/toggle callbacks, saved parameters, and routing remain unchanged.
- Arrangement track headers now show a compact zero-padded track index alongside the track name, improving lane scanning without changing track IDs or timeline geometry.
- Implementation commits: [09c028fd](https://github.com/jorgi80-droid/StudioWave/commit/09c028fdbd4f23b4ff0a892688b9062d02d2a92e), [9990e826](https://github.com/jorgi80-droid/StudioWave/commit/9990e82615ba8fda8b39417c79d1bfff53109f0d), [a61fda6a](https://github.com/jorgi80-droid/StudioWave/commit/a61fda6a0afc8be5b51b0f8c5806f73646e9d277), [9ab12106](https://github.com/jorgi80-droid/StudioWave/commit/9ab12106fbfea16fbf5b374d96261ba9af369e1).
- Validation is pending for the latest source head: debug APK build, unit tests, and APK artifact upload must pass. Physical Android landscape testing remains outstanding; PR #15 stays draft.


## Arrangement / Device enabled-state animation pass — 2026-10-16
- Commit [0ee396c2](https://github.com/jorgi80-droid/StudioWave/commit/0ee396c22757394f449574c22c163b8744784868) adds a 120 ms eased color transition to the Arrangement clip-selection outline, in addition to the existing selection-fill transition.
- Effect device faceplates now ease the active top strip and enabled-state indicator between enabled and disabled colors over 120 ms. The existing ON/OFF control and device model callback are unchanged.
- These are presentation-only Compose animations. No timeline coordinates, clip drag/resize/snap mapping, audio processing, device enable semantics, parameter persistence, or routing were intentionally changed.
- Latest-head CI and physical landscape Android validation remain pending. Keep PR #15 as draft until debug APK build, unit tests, artifact upload, and device checks are complete.


## Ableton-style Clip View content overview — 2026-10-16
- Commit [cbb0eae7](https://github.com/jorgi80-droid/StudioWave/commit/cbb0eae71ccfd5520b787077b00f7787f9996d06) adds a compact content overview inside the Arrangement Clip View inspector.
- MIDI clips render their existing note pitch/time/duration data in a miniature note overview; drum clips render active steps from the existing step array. Audio clips show whether a source is attached and its filename, without fabricating a waveform or implying warp support.
- The preview is paint-only. Clip start/length controls, editor routing, project persistence, note/step data, timeline mapping, snap, playback, DSP, and the 1,800-bar cap are not intentionally changed.
- This follows Ableton Live 12's Clip View concept of presenting clip-specific content and properties alongside a dedicated editor, adapted for compact native Android landscape touch use. References: https://www.ableton.com/en/manual/clip-view/ and https://www.ableton.com/en/live-manual/12/editing-midi/
- Validation remains pending for this source head: check Android debug APK build, unit tests, and APK artifact upload. Physical Android landscape checks are still required. Keep PR #15 as draft.



## Sample Editor import workflow — 2026-10-16
- [f181d69b](https://github.com/jorgi80-droid/StudioWave/commit/f181d69b2406e2b931493e421161f6d28a5cee4e) adds an **IMPORT AUDIO** action inside the Audio Clip Sample Editor. It reuses the existing Android document picker and existing audio import/decode/project update path instead of creating a parallel import implementation.
- After a successful import launched from the Sample Editor, StudioWave selects the newly created clip and returns to the Sample Editor. The import is placed on the selected audio track at the current playhead tick when available; existing project limits, URI permission handling, duration checks, and playback engine paths are reused.
- [2e43de96](https://github.com/jorgi80-droid/StudioWave/commit/2e43de96e48913b992077560e84454974a4b160a) clears the editor-return flag if import fails, preventing a later unrelated import from navigating to the wrong workspace.
- The start/end trim sliders are disabled when no audio source is attached. Existing waveform decoding and trim model fields remain in use; this does not add time-stretch/warp processing, reverse audio, or loop playback.
- The change does not alter the shared timeline timebase/viewport, audio DSP routing, or 1,800-bar arrangement limit.
- Validation is pending for the latest source head: verify Android debug APK build, unit tests and artifact upload; then test importing audio from inside Sample Editor, waveform generation, trim persistence after closing/reopening the project, and playback on a physical landscape Android device. Keep PR #15 as draft until validation is complete.
- Ableton reference: https://www.ableton.com/en/manual/clip-view/ and https://www.ableton.com/en/live-manual/12/audio-clips-tempo-and-warping/.

## Audio trim persistence + failed-import recovery — 2026-10-16
- Commit [c409c623](https://github.com/jorgi80-droid/StudioWave/commit/c409c623ed2457b03a61c3c5ea566fe00332107c) reconstructs Sample Editor trim handles from persisted `sourceOffsetTicks` and `lengthTicks` against the original audio duration, rather than always opening at 0–100% or calculating offsets relative to an already-trimmed clip.
- Trim controls stay disabled until source duration metadata is available; Reset Range requires a valid source duration.
- Audio import now clears pending track/start/editor-return state in a `finally` block, preventing a failed attempt from leaking its target into the next import. Exceptions return to Arrangement and preserve an error message.
- Static review of the commit diff confirms only Sample Editor trim UI/model updates and the import exception/finally branch changed; the shared timeline mapping, arrangement cap, native DSP implementation and project schema were not intentionally changed.
- Workflow query for commit `c409c623` returned no associated PR workflow runs. Build, unit tests, APK artifact upload, actual save/reopen trim round-trip, and physical-device playback still require verification; do not mark this fix validated until those checks pass.


## Audio trim logic hardening + regression coverage — 2026-10-16
- Implementation commit [dbd81ddc](https://github.com/jorgi80-droid/StudioWave/commit/dbd81ddc837f13b75bbcb072c14d6b3c7271c4bf) extracts trim fraction restoration and fraction-to-tick conversion into pure helpers.
- Restoration now clamps source offset to source duration and caps length to the remaining source range without adding potentially overflowing Long values.
- Fraction-to-tick conversion rejects unavailable/too-short sources, normalizes NaN/infinite inputs, and guarantees a non-empty source range bounded by duration.
- Regression tests added in [ad1c85b6](https://github.com/jorgi80-droid/StudioWave/commit/ad1c85b6220bd225f5694f4d64c3e1367f9b728d) for persisted range restoration, out-of-range offsets, overflow-sized lengths, invalid fractions and too-short sources.
- These changes only harden Sample Editor range calculations and tests. No timeline viewport, arrangement cap, DSP, source URI, project schema, or device routing changes were intended.
- Validation still required: Android CI debug build, unit tests and APK artifact upload for latest branch head; then physical-device save/reopen and playback tests. Do not claim runtime audio behavior passed based on pure helper tests alone.
- Next feature work should wait until CI verifies this logic; then audit that playback plan consumes sourceOffsetTicks and lengthTicks consistently, especially after BPM changes and project reload.

- Follow-up precision fix [5a7b9f9b](https://github.com/jorgi80-droid/StudioWave/commit/5a7b9f9ba2a5b6c388b24027f5a4207dc9d2822d) removes the old 0.99 start cap and instead bounds the start to one tick before source end, so a trim near 100% can round-trip without jumping to 99%.
- Additional regression assertions [5bf0579c](https://github.com/jorgi80-droid/StudioWave/commit/5bf0579c686a744a14e5bea9dc003d8e273fc3e0) cover near-end trim restoration and exact final-tick behavior.
- Workflow lookup for the latest implementation/test commits returned no associated PR-triggered runs; latest-head build/test/APK upload remain unverified.


## Playback trim audit — 2026-10-16
- Audited `ArrangementPlaybackEngine` audio event construction and source-frame selection.
- Fixed a playback defect: an extended audio clip previously wrapped over the entire decoded source, which could reintroduce audio outside the selected trim range. The loop now wraps only within the clip's selected source window, bounded by the remaining decoded asset frames.
- Normalized source offset and window length to the decoded asset sample-rate domain before the mixer reads samples; this avoids mixing output-rate and source-rate frame coordinates.
- Added pure helper `ArrangementTiming.sourceFrameWithinTrim` and unit regression coverage for window wrapping, end-of-source clamping, and invalid assets.
- Commits: `4f054f0b`, `93e5bb71`, `dc3f0906`, `db4e36ff`, `95bac3c6`.
- Validation: source review and test cases added; latest-head GitHub Actions workflow run and combined status returned no results. Gradle build, unit-test execution, APK upload, and physical Android playback validation remain unverified. Do not mark this batch green without a completed CI run.


## Playback tick arithmetic follow-up — 2026-10-16
- Branch HEAD was rechecked before edits: `567f61920602db58105fdbbd671c2f576038e01c`. PR #15 remains open and draft.
- `ArrangementPlaybackEngine.buildPlan` previously calculated the requested source extent with raw `sourceOffsetTicks + lengthTicks`, then clamped the result. For values near `Long.MAX_VALUE`, the addition could overflow before clamping and produce a negative/incorrect requested decode duration.
- Added `ArrangementTiming.addTicksSaturated` to perform non-negative, overflow-safe tick addition, added unit tests for negative inputs, ordinary sums, and overflow, and updated playback-plan source extent calculation to use the helper.
- Changes are limited to arithmetic used to request source duration; they do not change project schema, trim values, shared TimelineViewport, snap/drag/resize mapping, or the 1,800-bar cap.
- Commits: [37fdd6cb](https://github.com/jorgi80-droid/StudioWave/commit/37fdd6cb008d0c14efb14d48390b0890b632969c), [c8469f47](https://github.com/jorgi80-droid/StudioWave/commit/c8469f47fbda300aa93bd6ddd89bf60e7d200348), [17eea786](https://github.com/jorgi80-droid/StudioWave/commit/17eea786848b09bfa0e427c04bccd93670308758).
- Important unresolved semantic finding: `sourceOffsetTicks` and `lengthTicks` are musical-tick coordinates, while the Sample Editor converts source duration milliseconds into ticks using the current BPM and the playback engine converts those ticks back to seconds using BPM. Therefore changing BPM can change the absolute source-time represented by an existing trim. Correcting this requires a backward-compatible source-time representation/migration and synchronized updates to the editor, waveform, playback plan, and project JSON; do not attempt a partial one-site conversion.
- The latest code commits require their own Android CI run. Unit tests, debug APK build/artifact upload for the new head, and physical-device audio behavior are not yet verified. Keep PR #15 as draft.


## Absolute audio source-time pass — 2026-10-16
- Added optional sourceOffsetMicros and sourceLengthMicros to WaveClip. Existing sourceOffsetTicks and lengthTicks remain present for arrangement coordinates and legacy project compatibility.
- StudioProject.fromJson migrates older clips without absolute trim fields using the BPM/PPQ saved in that project; subsequent saves persist the migrated absolute values without changing the project schema version.
- Imported audio stores its measured source duration in microseconds. Sample Editor trim updates now persist absolute source offset/window as well as legacy tick values.
- Playback planning converts source trim microseconds directly to source PCM frames. Arrangement clip start/length still use musical ticks and the shared tempo timebase; source trim position/window no longer moves when BPM changes.
- Arrangement waveform selection reads absolute source coordinates when available, and invalid source offsets at or beyond decoded source duration are skipped instead of clamping to/repeating the final source sample.
- Clip split/edge-resize paths were updated to keep source-time metadata aligned with the edited audio range.
- Regression tests cover absolute source-time conversion, overflow/invalid inputs, JSON round-trip, and migration of a legacy trim using saved BPM/PPQ.
- Implementation commits: [b896b37b](https://github.com/jorgi80-droid/StudioWave/commit/b896b37b22b7485cb982998068c7721b9bd9b8bf), [1311f1ce](https://github.com/jorgi80-droid/StudioWave/commit/1311f1ce65ae5ab2873f5d82e9bcaed4e998d8ff), [6668aaca](https://github.com/jorgi80-droid/StudioWave/commit/6668aacac64720c28dee2d6c4b9bec0d975b2b64), [903c0ad0](https://github.com/jorgi80-droid/StudioWave/commit/903c0ad00abde9249186f3e4918dd0365fffeed6), [9dce5e26](https://github.com/jorgi80-droid/StudioWave/commit/9dce5e26ed6d3f093e788324467f4e576d7a059c), [b42467ad](https://github.com/jorgi80-droid/StudioWave/commit/b42467add888e2353ff86938e4bde0d19073a030), [59424037](https://github.com/jorgi80-droid/StudioWave/commit/594240377f5a55d8b56838c604aa98c848d23191), [7f74e48d](https://github.com/jorgi80-droid/StudioWave/commit/7f74e48d949e0255d05fc917767fad597d40f225), [19ad989a](https://github.com/jorgi80-droid/StudioWave/commit/19ad989a6bababf1a8be5a9cb1e871732a3055dc), [35eacfc8](https://github.com/jorgi80-droid/StudioWave/commit/35eacfc8a46e26161d622163ff0336d3102d3119).
- Validation status: source changes and regression test definitions are committed. The GitHub connector returned no workflow runs for the latest test commit at the time of the audit; build, unit-test execution, and APK artifact upload are therefore not yet verified. Physical Android playback/save-reopen tests remain required.
- Next validation gate: inspect the branch current HEAD and PR #15, run Android CI on the latest source, fix any compile/test failures, then validate a saved/reopened trimmed clip at multiple BPM values on a landscape Android device. Keep PR #15 in Draft.

- Follow-up [5195cc55](https://github.com/jorgi80-droid/StudioWave/commit/5195cc55de4d2b55852e88b9b72b490147bd77a6) preserves the exact absolute source offset during a right-edge resize instead of round-tripping it through ticks. This prevents small trim-position drift from repeated edits.

- Follow-ups [4b3546d4](https://github.com/jorgi80-droid/StudioWave/commit/4b3546d4cdb42066aaaaeb6240416315ecaccdfa) and [0d699c87](https://github.com/jorgi80-droid/StudioWave/commit/0d699c87201c006a59962656ae40c209b9e922b6) calculate trim slider positions directly in source microseconds rather than quantizing the saved source range through musical ticks. A regression test covers exact fractional conversion and invalid slider values.


## High-refresh UI animation pass — 2026-10-16
- Source commit [23ed641a](https://github.com/jorgi80-droid/StudioWave/commit/23ed641a832f7ba24ad67d33bc164f94ace71579) adds a frame-synchronized entrance reveal for the decoded Sample Editor waveform and short eased transitions for the rendered Start/End trim handles.
- Waveform bars still use decoded source peaks; the animation reveals those actual peaks and does not fabricate a moving audio signal or imply playback.
- Existing Compose `withFrameNanos` playback-follow loop, Mixer peak/RMS smoothing, and the Activity's preferred 90–120 Hz display mode request are retained. Android's display/power/thermal policy remains authoritative; 60 Hz devices continue to run normally.
- No musical tick mapping, source trim persistence, DSP routing, or audio engine logic was intentionally changed in this visual batch.
- Validation gate: run Android CI for the latest branch head (debug APK build, unit tests, APK artifact upload) and test Sample Editor trim response, waveform reveal, Arrangement pinch/scroll/resize and Mixer meter responsiveness on a physical landscape Android device. PR #15 remains Draft until these checks pass.

- Follow-up [8fc7446e](https://github.com/jorgi80-droid/StudioWave/commit/8fc7446ef3a6483f5d12eee8d79a38d1bb871156) adds unit coverage for choosing the highest supported 90–120 Hz display mode and safely falling back when only 60 Hz or out-of-range modes are available.


## Release-candidate validation gate — 2026-10-16
- Validation baseline inspected: branch `ui/ableton-batch-viewport`, source commit `9028dada14df40dd4dd066ec4c4b07f771b6e6c3`; PR #15 remains Draft.
- Confirmed source includes the FL Studio Mobile-inspired in-app tutorial workspace, About StudioWave page with repository/developer GitHub links, device-aware audio profile settings, performance-profile unit tests, native AAudio output, incremental arrangement plan refresh, and background audio source preparation.
- Audio engine audit: incremental plan updates are coalesced and atomically published while the active AAudio stream remains open; new audio sources can still require asynchronous decoding before their audio becomes audible. Immediate audible playback for 50 newly added tracks has NOT been demonstrated and must not be claimed from source inspection alone.
- Validation must run on this exact source line: Android debug APK build, all JVM unit tests, and artifact upload. Any compile/test failures must be fixed before distributing an APK.
- Manual device validation still required: cold launch; create/save/reopen .sdaw; import WAV and verify waveform; play/pause/stop/seek; add one track while playing; batch-add 50 audio tracks during playback; verify existing audio continues without transport reset; verify newly added tracks join when prepared; check mute/solo/fader/pan, MIDI/drum playback, device bypass, tutorial navigation, About links, and each audio profile.
- Performance acceptance: report measured first-audio latency, project load duration, peak memory, underrun count and render-block overruns for a reproducible 50-track / 40–60-device / five-minute project. Do not treat JVM unit tests as proof of physical audio performance.
- Integration gate: keep PR #15 Draft and do not merge until CI passes and physical-device checks are recorded.


## Integrated CI/source recovery — 2026-10-16
- Verified branch HEAD before recovery: `325e64dab1f4debcf9d98a6027725ae078e2ecc4`. PR #15 remains open and Draft.
- Latest run [38892941955](https://github.com/jorgi80-droid/StudioWave/actions/runs/38892941955) failed before Gradle: `android-actions/setup-android@v3` invoked `sdkmanager tools`, which failed with `Warning: Failed to find package 'tools'`. This is a toolchain provisioning failure; build/tests/artifact steps were skipped.
- Repeated compile failures in [38892317613](https://github.com/jorgi80-droid/StudioWave/actions/runs/38892317613) were separate source errors: an invalid explicit Compose `weight` import in `StudioWaveTutorial.kt`; `currentCoroutineContext()` invoked inside `synchronized` in `ArrangementPlaybackEngine.kt`; and two unresolved `gain` references in `TrackDeviceDsp.kt` where the function parameter is `masterGain`.
- Integrated repairs prepared: remove the invalid scoped extension import; read the current coroutine Job before entering the monitor; use `masterGain` for both master-send channels; restore explicit SDK/NDK provisioning; run `testDebugUnitTest` before APK packaging so compile/test failures block artifact creation.
- Validation status at checkpoint update: source/workflow repair candidate committed, but fresh CI is required to establish build, unit-test, and APK-artifact results. Physical Android playback, save/reopen, memory/latency and underrun measurements remain outstanding. Keep PR #15 Draft.


## Integrated P0 transport and project-recovery batch — 2026-10-16
- Shared `clampArrangementTick` now gives transport seek/start/progress a single 1,800-bar endpoint matching the Arrangement viewport.
- Progress updates within the active playback session are no longer blocked by a monotonic-only guard. This prevents the playhead freezing when a live arrangement edit shortens the scheduled content; session IDs continue to reject stale events from older sessions.
- Opening a project invalidates the previous playback session and resets the cursor to bar 1, preventing queued progress from the old project from moving the new project's playhead.
- Autosave restore/write now uses the bounded `.sdaw` codec, applying the same 16 MiB limit and schema validation to private autosaves as portable project files.
- Added regression coverage for negative, in-range, and over-limit transport ticks.
- Validation gate: run Android CI on this exact commit. Source changes do not replace physical-device checks for seek while playing, live edits that shorten an arrangement, and cold-launch save/reopen.


## P1 batch — live playback WAV capture groundwork (2026-10-16)
- Added bounded asynchronous PCM-to-WAV writer utility and regression tests for the RIFF/PCM header, stereo format, sample rate, and size bounds.
- The writer avoids file/document I/O on the playback producer by using a bounded queue and writes a finalized WAV header after capture ends.
- This utility is not yet wired to a user-facing transport action; do not describe WAV export as available until UI/engine integration and CI verification land.


## P1 live WAV bounce implementation — 2026-10-16
- Added a bounded asynchronous PCM capture writer that writes a valid stereo 16-bit PCM RIFF/WAV file and finalizes its header after playback stops or completes.
- Wired a WAV action into the compact transport. It opens Android's Create Document picker, captures the actual post-master-limiter PCM during arrangement playback, and finalizes the WAV asynchronously so the Stop action does not wait for document I/O.
- Added WAV-header regression tests for RIFF length, stereo PCM format, sample rate, and file-size validation.
- The WAV capture queue is bounded; an overflow is logged and drops a block rather than blocking the realtime producer. Physical device checks must confirm that no overflow occurs on target phones.
- Latest implementation commit: `1b8f2ffb2fd18a0e31e44b6caa5625d541ed0d9c`; run CI against the final head before treating this batch as validated.


## Integrated microphone recording batch — 2026-10-16
- Added native Android microphone capture using `AudioRecord`, with PCM writing on a dedicated worker thread and a finalized mono PCM16 WAV file.
- Added a compact REC control to the transport. It requests runtime microphone permission, stops arrangement playback before recording, and on stop creates an Audio track + clip at the current playhead, bounded by the existing 1,800-bar arrangement limit.
- Recorded takes are saved in the app's private recordings directory and referenced by their file URI in the arrangement; capture works offline.
- Added RIFF/WAV header regression tests for mono channel count, sample rate, byte rate, bit depth and data size. Also moved playback-bounce writer startup after constructor validation to avoid starting a worker before rejecting an invalid sample rate.
- Validation gate: this batch is not verified by Android CI yet. Require debug APK build, unit tests and APK artifact upload before calling it green. Physical device testing is required for microphone permission, actual input level, recording start/stop, and playback of the recorded clip.


## Integrated professional audio + Ableton-reference pass

Baseline for this batch: branch HEAD `b36db6883886ec3c4ce481afb7c56a2dded8caf4`. PR #15 remains Draft and has not been merged.

### Integrated changes
- Added project-backed `STUDIO_SYNTH` parameters for an original dual-oscillator instrument: waveform A/B, mix/gain, tuning, filter cutoff/resonance, ADSR and LFO. Reused the existing `TrackDevice.parameters` persistence format to preserve compatibility with older projects.
- Connected scheduled MIDI events to configured voice rendering. Pitch mapping includes 12-TET, Just Intonation and Pythagorean ratios anchored to a selected root MIDI note. Note-off release tails extend the transport render plan but do not expand the bar-limited arrangement coordinate system.
- Bounded the active MIDI voice set to 128 voices with deterministic oldest-start voice stealing. Kept DirectWave as a separate sample source and preserved its existing sampled ADSR; its synth controls are not shown when the sample-source mode is active.
- Added a bounded, preallocated handoff of post-master PCM blocks to a background Hann-windowed radix-2 FFT worker. The device UI consumes spectrum snapshots from real rendered PCM; FFT/windowing/band aggregation do not execute inside the render loop.
- Added eight-band EQ response math based on the peaking-biquad equations used by `TrackDeviceDsp`. Graph node gestures write persisted `PARAM_EQ_8` gain/frequency parameters and the insert enable toggle controls the actual device bypass.
- Improved Live-inspired graphite/light palette contrast, Arrangement clip fill/title/detail legibility, and reduced resize-handle visual noise without rewriting time transforms, zoom/scroll math, gesture hit bounds or the 1,800-bar cap.
- Replaced the previous local-state instrument/spectrum mock panel with project-backed StudioWave controls and the real PCM spectrum. Unsupported tuning names and fake spectrum animation are not presented as working features.

### Added regression coverage
- DSP: tuning intervals, finite oscillator output, envelope/release and peak behavior.
- Analyzer: FFT tone/bin math and frequency-to-bin mapping.
- EQ: flat response at unity, gain/frequency response and bypass.
- Persistence: StudioWave Dual Synth parameters round-trip through project JSON.
- Existing tests remain for timeline viewport, audio trim/playback, serial effects/bypass and WAV header behavior.

### Remaining validation boundaries
- GitHub Actions for the newly pushed source must pass before this batch is reported green. The CI state and APK artifact must be confirmed from the commit's actual workflow run.
- No physical Android 16 device test is claimed. Microphone capture code and WAV header tests exist, but runtime permission/lifecycle, recording take insertion into Arrangement, and take save/reopen/playback remain physical-device acceptance tests.
- True audio tempo warp/time-stretch with warp markers and full parallel Instrument/Effect Rack chains/Macros are not claimed complete here. Clip trimming is not the same feature as tempo warping.
- UI fidelity is a reference-informed original native adaptation, not a claim of pixel-identical Ableton implementation or use of proprietary Ableton assets.

See `docs/ABLETON_HTML_REFERENCE_AUDIT_2026-10-16.md` for the manual-to-source mapping.


## Audio Clip View tempo-sync follow-up — 2026-10-16
- Added a persisted per-clip warpEnabled field without changing the project schema version; older projects default to Warp Off.
- Audio Clip View exposes WARP ON/OFF and explicitly labels the mode VARISPEED. With Warp enabled, the selected source window maps across the arrangement clip duration using bounded source-frame mapping; changing BPM therefore changes playback rate while keeping the clip aligned to the arrangement.
- Added timing tests for source mapping boundaries/extreme values and project JSON persistence/legacy default behavior.
- This is the first tempo-sync mode, not pitch-preserving time-stretch; warp markers and advanced Ableton warp algorithms remain outstanding.
- The compile correction commit de75304d88f450dcd4b9efc4c8c773ef37e37d72 passed GitHub Actions unit tests and debug APK build.


## Audio clip gain follow-up — 2026-10-16
- Added per-clip clipGainDb (−60 to +12 dB), persisted in project JSON with a backward-compatible 0 dB default.
- Audio Clip View includes a gain slider/readout; the audio engine computes the linear scalar once per clip/block, then applies it to actual decoded PCM before track volume/pan and master processing.
- Added dB conversion and project persistence regression coverage. This is separate from track fader gain and source trim.


## Clip waveform/pattern visibility and top Master gain — 2026-10-16

- Arrangement clip faces no longer spend a full extra line on technical metadata. The clip name stays in the colored title strip; the remaining body shows decoded audio peaks, MIDI-note maps or drum-step patterns. Clip outer padding is reduced from 7 dp to 3 dp. Musical time coordinates, viewport math, snapping, drag/resize mapping and the 1,800-bar cap remain unchanged.
- Audio waveform decoding is keyed to the clip source URI, not playback state. Previously, the playback guard canceled analysis on Play, explaining why audio clips stayed blank during playback. A bounded 240-peak preview remains on Dispatchers.IO, outside the real-time audio callback.
- The top Master gain now uses a compact flat rail with cyan level fill, ticks and square thumb instead of the default Material Slider. Tap/drag still calls the same 0–1 gain callback.
- Mixer channel strips are 136 dp, pan knobs 50 dp and the Master strip 112 dp to fit more landscape channels without changing gain/pan/mute/solo behavior.
- This is a native Ableton Live-inspired adaptation, not exact pixel parity or proprietary asset reuse. CI for this head and physical Android 16 landscape validation are mandatory before calling the application ready.


## Device-chain de-duplication follow-up — 2026-10-16

- Removed the large text-only Audio Source card from audio-track Device View. The audio signal is represented by the track input marker and then the actual serial FX inserts, instead of a fake source device occupying hundreds of dp.
- The synth faceplate no longer draws a second EQ Eight or exposes EQ controls that bypass the serial chain. EQ Eight is shown and edited only by its actual effect insert card after the selected instrument.
- MIDI clip note thumbnails now use high-contrast note ink against the track-colored clip fill so pitch/velocity patterns stay legible at Arrangement scale.
- This keeps the instrument controls and measured master-PCM spectrum; only the duplicated EQ insert presentation was removed. Verify CI and landscape touch behavior before calling the UI finished.


## Arrangement waveform / Master fader / drum clip thumbnail batch — 2026-10-16
- Batch based on source head `295cfc1141cea40386a605db8e1fa31de4efeeb8`.
- Arrangement audio clip peaks now normalize within the visible source window for display. Relative dynamics remain intact while quiet valid audio becomes visibly bipolar instead of a row of near-zero peak ticks. Audio samples and playback gain are unchanged.
- Drum clips render saved step events as a compact beat-grid thumbnail with distinct active-hit energy and stronger beat boundaries. No hits are invented or animated.
- The Mixer Master fader now has a wider outlined cap and a high-contrast orange position line on a slightly stronger rail. Existing dB taper and drag/tap callbacks remain unchanged.
- Arrangement viewport/timebase, clip hit testing/resize/snap, MIDI note model, DSP routing, master gain mapping, and the 1,800-bar limit are unchanged.
- Validate this commit with Android CI unit tests, debug APK build, and APK artifact upload. Physical-device playback/layout checks remain required.
