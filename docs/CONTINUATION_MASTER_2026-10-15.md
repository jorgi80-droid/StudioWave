# StudioWave Continuation Master — 2026-10-15

## Active project
- Repository: https://github.com/jorgi80-droid/StudioWave
- Working branch: `ui/ableton-batch-viewport`
- Draft integration PR: https://github.com/jorgi80-droid/StudioWave/pull/15. Keep draft; do not merge while CI is not green.
- User-uploaded APK baseline is a visual/runtime reference, not source.
- Current checkpoint commit before this document update: `140d430eeba3b435e58ce26167e8552117b68383`.
- Current tracked key files:
  - `MainActivity.kt`: `905398c13c7e3a88ed064dbbbedd767ce988bed5`
  - `ArrangementPlaybackEngine.kt`: `c10548572bdb35ba9cda068a3b031f50f8cf0bcd`
  - `ProjectModel.kt`: `79cfa53a9b627cb60d481273afac91c1ba9eb606`
  - `TrackDeviceDsp.kt`: `1c8db6204cc4ce02a1d2b6bbdfab205847bec919`
  - `TimelineViewport.kt`: `339ecd9899d5e33341a4514e705c4e7da614d132`
  - viewport tests: `3e1f284d9c0fedffcff7c62269b2b9a5dcce3b1e`
  - DSP tests: `0b7213fb619e4201f8d24524a51495507f03cd9e`
  - project persistence tests: `6e8dc24029c0d2d72bd5b74f4a58a7f7a91995d3`

## User's design direction
Adopt Ableton Live's design language as the clear visual reference: dark graphite panels, clip colors, compact parameter strips, device-chain blocks, knobs/dials, EQ/analyzer graphs, meter strips and clear typography. Keep FL Studio Mobile's practical mobile workflow: touch-first arrangement editing, accessible piano roll/drum sequencing, clear browser, pinch zoom and compact navigation. Treat screenshots and linked manuals as specifications to implement against, not just reading material. Do not claim literal 1:1 parity; implement an original and legally distinct UI inspired by the reference.
Core views: Arrangement, MIDI/Piano Roll, Drum Sequencer, Audio Clip View, Mixer, Device View, Browser, Settings. Controls exposed as active must have actual backing behavior; unimplemented devices should be labeled unavailable/inactive rather than presented as working.
Requested effects: Equalizer, 8-band Parametric EQ, Reverb, Delay, Limiter, Saturator, Compressor, Filter, Full Analyzer, Multi FX.
Requested instruments: DirectWave, SuperSaw, MiniSynth, GMS.
Project is Android-only for the user (AndroidIDE), beginner friendly. Provide direct APK/artifact link only after CI confirms the artifact exists.

## Existing implementation checked
- `StudioWaveSettings.kt` supplies shared palette and theme controls.
- `MainActivity.kt` has a large Live-inspired workspace, transport, arrangement/piano-roll/drum/audio/mixer/browser/settings navigation, a DeviceDock and a DirectWave sampler UI using `DirectWaveSampler` and generated factory samples.
- The current device panel is not yet a full device rack with functional per-track effect cards. Source audit found `TrackEffectDeviceCard` is absent in the active MainActivity, despite the data model/DSP processor existing separately.
- `ProjectModel.kt` persists per-track `TrackDevice(type,name,enabled,parameters)` through JSON and validates known device types.
- `TrackDeviceDsp.kt` contains basic DSP for EQ, PARAM_EQ_8, FILTER, SATURATOR, COMPRESSOR, DELAY/REVERB, LIMITER.
- `ArrangementPlaybackEngine.kt` snapshots track device lists, creates per-track audio buses for audio/MIDI/drums, and runs enabled insert DSP in chain order, adding the wet/dry delta into master output.
- `DirectWaveSampler.kt` and `FactorySampleLibrary.kt` support offline sample generation/load. Main UI now generates its built-in one-shot in app cache instead of relying on missing `R.raw.one_shot`.
- `TimelineViewport.kt` has shared time mapping, pane-local viewport conversion, zoom anchor preservation, and the 1800-bar extent.
- Existing tests: 12 viewport tests, 5 DSP tests, 5 device persistence tests (check current exact counts if changed).

## CI checkpoint
- CI on commit `7b0a2471c90790ff02bb1ac3dac2af3717c3481e` completed successfully, including build, unit tests, and APK artifact upload: https://github.com/jorgi80-droid/StudioWave/actions/runs/38741833854
- Artifact from that successful run: `StudioWave-debug-apk`, artifact ID `11976422706`, 17,478,001 bytes, not expired at last check. This artifact predates the last viewport assertion fix and checkpoint-doc-only changes, so prefer waiting for the latest run before sharing an APK as current.
- Latest code/test commit verified green: `2049b9b36c33721babcbbe5a02806582a8523bff`, run `38742059249`: https://github.com/jorgi80-droid/StudioWave/actions/runs/38742059249. Build debug APK, unit tests and artifact upload all succeeded.
- Historical known errors have been addressed: missing `R.raw.one_shot`, stale `visibleSubdivisionTicks()`, wrong numeric assertion in viewport tests, duplicate per-track buffer declaration.

## Next actions (start here in a new chat)
1. Latest verified CI run `38742059249` passed. Artifact ID: `11975929414` (`StudioWave-debug-apk`, 17,478,006 bytes, unexpired at checkpoint). Obtain/use this exact run artifact if sharing the APK.
2. If CI passes, download and share only the artifact for the current green run; then request physical Android checks for launch, landscape, sample note output, insert effects audible response, timeline gestures and saved-project reopening.
3. Implement the actual DeviceRack UI connected to `project.tracks[selectedTrackId].devices`. Provide Add/Remove/Enable/Reorder; device parameter changes must update model and be audible through `ArrangementPlaybackEngine`. Make UI visual language closer to screenshots: narrow device headers, knobs, graph/curve surfaces, compact chain blocks and audio levels.
4. Add real implementations/tests before adding active controls for Full Analyzer (windowed FFT/bins), Multi FX (serial chain composition), SuperSaw, MiniSynth, and GMS. Keep unavailable/inactive states honest until then.
5. Fix instrument audio routing: `DirectWaveSampler` UI currently previews samples via a separate SoundPool; ensure actual MIDI clips route notes into track sampler voices or document the limitation and integrate it next.
6. Add regression tests for EQ frequency response, compressor gain reduction, limiter ceiling, DSP order, no duplicate dry signal/master gain, persistence of each parameter, and test a full track through actual render path.
7. Refine Ableton-inspired Arrangement/ClipView/Piano Roll/Mixer and validate phone-sized landscape touch targets. Never change unrelated audio-engine sections without tests.
8. Keep this master updated after stable batches. PR #15 stays draft until all CI checks are green.

## Honest status
CI is green for code/test commit `2049b9b36c33721babcbbe5a02806582a8523bff` (run `38742059249`). This does not certify all requested device features. This is not yet a fully implemented Ableton/FL Studio Mobile competitor. FX routing exists underneath, but the full device UI and requested synth/analyzer/multi-FX features are still incomplete. Real device audio and touch behavior still require on-device validation.
