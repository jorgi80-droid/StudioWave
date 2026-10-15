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
