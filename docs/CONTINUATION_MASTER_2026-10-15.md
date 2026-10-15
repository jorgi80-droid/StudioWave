# StudioWave Integrated UI + Device Batch — Continuation Record
Date: 2026-10-15

## Source of truth
- User-supplied APK: app-debug-1.apk
- SHA-256: 3a396f0f8dd4d26669527f816e0dd8b84d38d8040c3addf5cf2ed90d310b4919
- APK is behavioral/UI baseline, not original Kotlin source. Do not claim byte-identical source reconstruction.
- Repository: https://github.com/jorgi80-droid/StudioWave
- Default main head observed: 70f034049db33bfcf6adab4c9ca183020c72f096
- PR #14 arrangement-coordinate repair merged at d921d8b1c255b10e1776ea3567385b6d17104e49; head was 73cace47a4660d34604ed68cae83ed3b281bdbf6.
- PR #8 branch ui/ableton-inspired-mobile-workspace head 96a2bbbdfba29e60e3bafbe23bd4e7c7fb65aae3; contains the APK-matched UI and track-device/DSP data model work, but head build/test status was not verified in prior PR description.
- PR #12 branch fix/ableton-ui-piano-roll-batch head dd16868a18778e07a5e0f561d6160cb0fa6aa9ed; draft and overlaps MainActivity/TimelineViewport files. Contains workspace/Piano Roll integration improvements; do not merge blindly.
- PR #11 branch fix/ci-directwave-and-live-ui-integration head fa4e837f30544e6e6857e093da394454e94a82a2; draft. Adds sampler UI and SoundPool path, not native low-latency Oboe processing.
- Current main CI workflow was observed to trigger only on main push, PR-to-main, and manual dispatch; PR #14 changes it to push on main/fix/**/ui/**/perf/**.
- Correct package path on APK-matched branch: app/src/main/java/com/studiowave/app/.

## User design intent
- Use Ableton Live 12 as explicit visual language reference (graphite surfaces, tight clip grid, device chain panels, clear meters, compact transport, encoders and useful value labels), while keeping FL Studio Mobile quick-touch workflow.
- Native Android, landscape-first, immersive, mobile-scaled, not a shrunken desktop.
- Coherent workspaces: Arrangement, Piano Roll, Drum/Step Sequencer, Mixer, Device View, DirectWave/sampler, Audio Clip View, browser, settings/transport.
- Screenshots show shortcomings: Arrangement clips are simple colored rectangles with limited content, Mixer controls look placeholder-like, DirectWave has no sample loaded, track-add popup text wraps awkwardly, Browser is project-only, settings need integrated Live-style language.
- FX requested: Equalizer, Parametric EQ 8 band, Reverb, Delay, Limiter, Saturator, Compressor, Filter, Full Analyzer, Multi FX. Instruments: DirectWave, SuperSaw, MiniSynth, GMS.
- No decorative/inert controls. Do not claim pixel-identical proprietary clone or copy proprietary assets. Do not claim unsupported audio DSP is working.

## Confirmed code facts from GitHub
- ProjectModel.kt on ui/ableton-inspired-mobile-workspace has TrackDevice, device serialization, EFFECT_DEVICE_TYPES currently EQ, PARAM_EQ_8, REVERB, DELAY, COMPRESSOR, SATURATOR, FILTER, LIMITER; instrument set DIRECTWAVE, SYNTH_SINE, SYNTH_SAW, SYNTH_SQUARE, SYNTH_TRIANGLE, FM_SYNTH, DRUM_RACK. SuperSaw/MiniSynth/GMS/Full Analyzer/Multi FX are not in these sets yet.
- TrackDeviceDsp.kt contains some original/basic processors. Audit whole file and native render path before asserting audible realtime behavior. No confirmed Full Analyzer or Multi FX implementation here.
- MainActivity.kt on APK-matched branch is a large existing Compose app; do not wholesale replace.
- TimelineViewport.kt uses shared TimelineTimebase/TimelineViewport and 1800-bar max; PR #14 fixed Arrangement pane-local coordinate issue in main.
- Branch ui/ableton-inspired-mobile-workspace has 46 changed files (+6460/-841 versus its base), materially more APK-aligned than old main. Do not overlay stale MainActivity from another branch without diff/conflict audit.

## Reference URLs
- Ableton Live 12 manuals: https://www.ableton.com/en/live-manual/12/ (Arrangement View, Clip View, Audio Clips/Tempo/Warping, Editing MIDI, Mixing, Instruments and Effects, Racks, Live Concepts).
- FL Studio Mobile manuals: https://www.image-line.com/fl-studio-learning/fl-studio-mobile-online-manual/html/plugins/ (Playlist, Rack, Mixer, Editors, Controllers, HomePanel, Settings).

## Batch requirements
1. Audit PR #8/#12/#11 diff and conflict status against merged main after PR #14.
2. Select one integration branch based on latest main, port compatible source changes, and make one coherent integrated PR. Avoid many tiny pushes.
3. Redesign actual panels: Arrangement clip MIDI/drum/waveform previews; dark graphite surfaces, flat dividers; compact fixed transport; functional mixer; Device View serial chain with Add Device; working knobs/sliders; fix track creation menu wrapping.
4. FX: EQ Three, Parametric EQ Eight, Reverb, Delay, Limiter, Saturator, Compressor, Filter. Full Analyzer must use measured FFT/spectrum data. Multi FX must serially compose child processors or remain honestly unavailable.
5. Instruments: DirectWave within existing sampler path and limitations; add SuperSaw/MiniSynth/GMS-style engines only through real oscillator/synth voice and audio render path.
6. Ensure UI state updates model and DSP; validate order, bypass, parameter persistence and range validation. No fake device controls.
7. Tests: device serialization, FX bypass/on/response, finite stereo output, delay continuity, EQ response, compressor/limiter safety, filter response, analyzer data, Multi FX order, synth pitch/shape/envelope/output; timeline/Piano Roll zoom/scroll/snap/move/resize; debug build and unit tests.
8. Build debug APK via CI, verify artifact, record SHA/source commit. Physical Android touch/audible testing remains required.
9. Update this record each batch so next chat continues from branch/source/hash rather than zero.

## Honest status rules
- CI success is not proof of correct audible FX on hardware.
- Do not state all features work until DSP integration, tests and device verification are done.
- Do not share an old APK as latest.
- User is Android-only beginner; provide clear mobile-friendly links/steps while doing GitHub actions directly when available.
