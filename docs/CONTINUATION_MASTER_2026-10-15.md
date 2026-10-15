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
- Latest corrected viewport test commit: `d980bd7729a9a92cc975c5cb6b328673c0cc22f5`; outdated `visibleSubdivisionTicks()` calls were replaced with `gridSubdivisionTicks(beatPixels)`.
- Track FX render integration commit: `0e034fc56a8ca1ba8e2a7ce01802eabe7b65848d`; duplicate buffer declaration was removed at `4d2ac86d77b416d81634b13aced3bf17f2aeb32b`. Latest visible engine blob SHA is `c10548572bdb35ba9cda068a3b031f50f8cf0bcd`.
- Last polled CI runs: viewport test run `38741454039` and DSP integration run `38741469140`. Both were still running; build steps had not produced a final result at checkpoint time.
- Prior CI run `38735345435` had successful APK compilation but unit tests failed due to the obsolete viewport API; the source test was since fixed.
- Historical build failures also included an absent `R.raw.one_shot`; latest MainActivity generates the sample via the offline factory pack.
- A successful historical baseline existed at `8b9ec03b23d734d3c7d379f5b2a058351a0577ce`, run `38678231952`, job `116236609946`.
- Do not call current branch green until the newest build AND unit tests pass. Do not provide an APK link unless a successful workflow artifact exists and its exact path is verified.

## Next actions (resume here)
1. Poll run `38742059249`; the previous CI failure was a test-only numeric assertion error. Confirm both APK build and unit tests pass.
2. If unit tests fail, inspect DSP assertions after viewport tests pass; ensure enabled insert effects really modify the per-track signal in chain order and do not make master double-count the dry signal.
3. Ensure Full Analyzer/Multi FX/SuperSaw/MiniSynth/GMS are not shown as working until there are engine-backed implementations and tests.
4. Continue visual pass against the submitted Ableton Live screenshots: arrangement clip detail/waveforms, device chain/effect graphs and knobs, mixer channel strips/meters, responsive horizontal navigation.
5. Keep PR #15 draft until CI is green, update this checkpoint after each stable batch, and report only verified artifacts.
6. Physical Android touch/audio test is still required after CI.

## Honest status
As of this checkpoint, the integration is not yet build-verified on the latest commit. Do not merge PR #15, do not claim 1:1 Ableton parity, and do not claim all requested devices work.
