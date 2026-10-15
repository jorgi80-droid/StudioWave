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
