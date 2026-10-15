# StudioWave Continuation Master — 2026-10-15

## Active work
- Repo: https://github.com/jorgi80-droid/StudioWave
- Active branch: `ui/ableton-batch-viewport`
- Consolidation PR: https://github.com/jorgi80-droid/StudioWave/pull/15 (draft; do not merge while CI fails).
- User-uploaded APK baseline SHA-256: `3a396f0f8dd4d26669527f816e0dd8b84d38d8040c3addf5cf2ed90d310b4919`. This is a runtime/reference baseline, not original source.
- Latest UI blob checked: `MainActivity.kt` `905398c13c7e3a88ed064dbbbedd767ce988bed5`.
- Latest audio-engine blob checked: `ArrangementPlaybackEngine.kt` `248b8ab1a646625f5a08819b05272045e39e9ccd`.
- Project/device data: `ProjectModel.kt` `79cfa53a9b627cb60d481273afac91c1ba9eb606`.
- Track DSP: `TrackDeviceDsp.kt` `1c8db6204cc4ce02a1d2b6bbdfab205847bec919`.
- Pane-local viewport: `TimelineViewport.kt` `339ecd9899d5e33341a4514e705c4e7da614d132`; includes `arrangementPaneViewport` and the 1800-bar bound.
- Test blobs: `TimelineViewportTest.kt` `b806ca2b56af93c5482aa758444ad1bc8fa17aba`; `TrackDeviceDspTest.kt` `0b7213fb619e4201f8d24524a51495507f03cd9e`; `TrackDeviceProjectTest.kt` `6e8dc24029c0d2d72bd5b74f4a58a7f7a91995d3`.
- CI workflow blob: `c02978240db3fe1c13dc4856c14a7706d0b31d9f`, triggers on `main`, `fix/**`, `ui/**`, and `perf/**`.

## User’s design and functional requirements
- Make a native, landscape-first, mobile-friendly DAW whose visual language is strongly Ableton Live-inspired (graphite surfaces, dense but legible lane grid, clip content previews, device chain, mixer strips/meters, useful knobs/encoders and labels), while retaining FL Studio Mobile’s fast touch workflow.
- Workspaces must remain coherent: Arrangement, Piano Roll, Drum/Step Sequencer, Mixer, Device View/DirectWave, audio clip editor, Browser and Settings.
- Device controls must not be decorative. Required FX: EQ, Parametric EQ Eight, Reverb, Delay, Limiter, Saturator, Compressor, Filter, Full Analyzer, Multi FX. Required instruments: DirectWave, SuperSaw, MiniSynth, GMS.
- Use original Ableton-inspired components, not copied proprietary assets or a claim of pixel-identical product clone.
- User is Android-only/beginner; after work, give direct links and concise install/testing steps.

## What currently exists in the branch
- `MainActivity.kt` has the integrated Live-inspired workspace and DeviceDock. DirectWave panel uses `DirectWaveSampler`/SoundPool, factory library and user sample import, with pitch/fine-tune/ADSR/loop/output controls.
- `StudioWaveSettings.kt` defines `StudioWavePalette` and `LocalStudioWavePalette`.
- `ProjectModel.kt` persists `TrackDevice` records (id/type/name/enabled/parameters) per track; known effect types are EQ, PARAM_EQ_8, REVERB, DELAY, COMPRESSOR, SATURATOR, FILTER, LIMITER. Known synth types currently include DIRECTWAVE, basic oscillator shapes, FM_SYNTH, DRUM_RACK.
- `TrackDeviceDsp.kt` has basic stateful algorithms for the effects listed above except Full Analyzer and Multi FX. Don’t expose those as active until implemented.
- The arrangement engine now accumulates per-track buses for audio, notes and drums and runs enabled devices in chain order through `TrackDeviceDsp`, adding wet/dry delta into master. Needs CI verification and DSP regression test expansion.
- `DirectWaveSampler.kt` integrated. Factory sample generation can produce WAV into cache so runtime is offline.
- Timeline fixes: shared `TimelineViewport` mapping, pane-local factory, anchor zoom test, max 1800 bars.

## Latest CI status and known compile failures
- Most recent run launched for commit `2ff63d87f5eeeb2ef7d2b5a89636f24d142a3f49`: https://github.com/jorgi80-droid/StudioWave/actions/runs/38735040285
- At last check it was still in progress at setup-gradle; poll before further assumptions.
- Older build failures included: missing `DeviceModuleCard` helper (resolved by returning MainActivity to previously CI-tested baseline at `8b9ec03b23d734d3c7d379f5b2a058351a0577ce`), missing `R.raw.one_shot` (latest MainActivity change generates this sample through FactorySampleLibrary instead), and audio engine interface mismatches (latest engine returned to tested native-queue baseline and then minimal device DSP routing was added; test via the latest CI).
- A successful historical baseline existed at `8b9ec03b23d734d3c7d379f5b2a058351a0577ce`, run `38678231952`, job `116236609946`.
- Do not call current branch green until the current build and unit tests complete successfully. Do not provide an APK link unless artifact exists and exact path/asset is verified.

## Next actions (resume here)
1. Poll run `38735040285` and inspect job logs if failed; fix only exact compiler errors.
2. Ensure new arrangement render routing compiles and TrackDeviceDsp tests pass.
3. Make sure all device changes survive JSON save/load and effect enabled/parameters are actually sent into render.
4. Add/expand tests: processor response and bypass, EQ response, finite output, delay state continuation, limiter safety, device persistence and ordered chain.
5. Keep Full Analyzer/Multi FX/SuperSaw/MiniSynth/GMS visibly unavailable until real measured FFT / serial chain / synth voice implementations exist. Then implement and add tests before exposing as enabled.
6. Update this file and PR body after each stable batch; PR stays draft until build/tests pass.
7. Only after CI + verified artifact, report APK path and note that physical Android touch/audible validation is still necessary.

## Honest status
As of this checkpoint, the integration is not yet build-verified on the latest commit. Do not merge PR #15, do not claim 1:1 Ableton parity, and do not claim all requested devices work.
