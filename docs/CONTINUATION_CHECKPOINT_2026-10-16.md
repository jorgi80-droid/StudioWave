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
