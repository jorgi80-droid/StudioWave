# StudioWave — MobileDAW v39 Reference Audit & Integration Plan
Date: 2026-10-28

## Source and intent
- Target repository: https://github.com/jorgi80-droid/StudioWave
- Reference package supplied in chat: `MobileDAW-Web_v39_ghibli.zip` (5,849,617 bytes).
- Reference package is a browser-based JavaScript ES-module DAW/PWA; StudioWave is native Android using Kotlin, Jetpack Compose, Gradle and a JNI/C++ audio path. Treat the ZIP as a feature/interaction/design reference, not as a drop-in source replacement.
- Reviewed ZIP structure: `index.html`, two CSS files, 51 JavaScript files, service worker, PWA manifest, icons, demo projects, and a 5.97 MB SoundFont. JavaScript syntax check passed for all 51 JS files; local import references checked had no missing modules. This is static validation, not runtime/audio QA.
- Current repository's native integration PR is #15 on `ui/ableton-batch-viewport`; this audit branch is based on that branch to preserve its UI/device work and existing CI baseline. Do not merge straight to main until CI, tests, and physical Android checks are complete.

## What the reference ZIP contains (capability inventory)
- Arrangement/timeline, transport, tempo/time signature, loop, snap, follow, undo/redo, clip split/trim/stretch operations and multi-track project state.
- MIDI editor/piano roll, MIDI input, note editing, velocity, drum pads/step sequencer.
- Browser and local storage abstraction for Projects/Samples/Recordings/Exports/Presets; sample/file import; project JSON plus .mdaw / ZIP export.
- WebAudio engine with AudioWorklet path, instrument synthesis (including MiniSynth, SuperSaw, GMS-style synth), sampler and SoundFont SF2 parser.
- Track mixer, effects and effect tracks, automation, WAV export, waveform display/audio editing and offline/PWA cache.
- These are inventory findings from the ZIP's source files. Each capability still needs runtime verification before being treated as fully working.

## Mapping to StudioWave (native implementation target)
- Preserve StudioWave's Kotlin/Compose UI, project model, timing/viewport helpers, playback engine, JNI/C++ path and .sdaw compatibility. Port behavior and design concepts into native controls; do not insert the web app wholesale.
- Design language target: graphite / Live-inspired control surfaces, compact transport, timeline hierarchy, readable clips, crisp dividers, functional track headers, selected-clip inspector, compact faders/meters and device cards. Use original Compose drawing; do not copy proprietary Live assets.
- Reference workflow parity: FL Studio Mobile-speed direct-touch editing. Keep layout landscape-first and responsive to phone dimensions.
- New visible controls must be wired to real project state and real audio processing; otherwise show an explicit unavailable/limited status rather than fake controls.

## Known confirmed StudioWave gaps from current audit/docs
1. Production effect/device registry and full serial DSP device-chain features are incomplete; analyzer/Multi FX cannot be represented as complete without measured FFT data and actual serial processor behavior.
2. Synth coverage/integration is incomplete: reference contains SuperSaw/MiniSynth/GMS-style engines, while the native model/audio path must be checked per actual voice/render implementation before claiming parity.
3. Audio recording workflow and lifecycle handling need implementation and device validation.
4. Tempo-aware time-stretch/warping and looping/split/crossfade workflows need corresponding model and DSP semantics; trim alone is not warp.
5. WAV mixdown/export and robust project bundles with embedded/copied media are not confirmed implemented.
6. Recovery/versioned backups, diagnostics/settings details and physical-device performance/lifecycle/audio underrun verification remain required.
7. The .sdaw project format is JSON plus URI references; imported source media may not travel with a project to a different device.

## Implementation batches (do not claim the full DAW is fixed until every relevant batch is verified)
### P0 — build stability and regression gate
- Build debug APK and run Gradle unit tests on the exact branch/PR head.
- Keep project schema backward-compatible; add migration/version tests before changing persisted structures.
- Run current tests for timeline geometry, clip operations, MIDI editing, audio meter and project file import/export.
- Acceptance: CI shows both build and tests passing on the exact code commit and the APK artifact exists.

### P1 — UI and direct-touch parity
- Arrangement previews must display MIDI notes, drum steps and source-derived audio waveform, clipped to exact clip bounds.
- One shared selection across Arrangement, Clip View, Piano Roll, Drum Sequencer, Audio Editor, Mixer and Browser.
- Consistent pointer gestures, context actions, snap/zoom/scroll anchor and usable touch targets.
- Mixer faders/pan/mute/solo/meters stay connected to actual mix model.
- Acceptance: regression tests plus physical Android landscape check for overflow, reachability, pinch anchoring, clip/note resize and editing during playback.

### P2 — instruments, device chain and actual DSP
- Compare each native instrument/effect against ZIP inventory one by one; use shared native render path, not UI-only placeholders.
- Prioritize sample/sampler path and synth voices (saw/unison SuperSaw-like, MiniSynth, FM/GMS-style voice) with pitch/envelope/output tests.
- Effects roadmap: EQ Three, Parametric EQ Eight, Reverb, Delay, Compressor, Saturator, Filter, Limiter. Verify finite stereo output, bypass equivalence, processor order, parameter persistence/ranges and response.
- Full Analyzer requires true measured FFT/spectrum data. Multi FX requires real serial child-processor composition; otherwise remains explicitly unavailable.
- Acceptance: DSP unit tests and listening/device validation. CI passing alone is not audible-quality proof.

### P3 — production workflows and project portability
- Record audio with permission, input route and lifecycle-safe stop/release.
- Implement model/engine-backed loop region, split/trim/fades and quality-preserving tempo warping.
- Implement offline mixdown to WAV with format options and cancellation/progress.
- Add project bundle export/import that includes every referenced sample/media file, validates size/path, and restores URI references safely.
- Add crash recovery/versioned backups and user-visible diagnostics.
- Acceptance: tests for file corruption/size boundaries, missing sources, lifecycle stop/resume, long projects, WAV header/duration/sample peak, and transfer to another device.

## Safety and release rules
- Do not wholesale replace StudioWave with the ZIP web source; architecture mismatch would lose native behavior.
- Do not claim “all features” from static inspection or a successful compile alone.
- Do not ship unsupported buttons/controls that look active but do nothing.
- Keep PR #15 and this audit branch as drafts until CI and physical device testing pass.
- The next implementation should be one coherent functional batch at a time and record code SHA, CI run, artifact ID and device test evidence.

## Validation state at audit time
- ZIP static JavaScript syntax: PASS (51/51 modules).
- ZIP relative JS imports: PASS (no missing local imports detected).
- ZIP browser/PWA execution and audio output: NOT run in a browser; therefore unverified.
- StudioWave PR #15 last queried head: `04c8f7ecc6a1334aca8cb8f918c6a94562445113`; run `38932922153` reports success for unit tests, debug APK build and artifact upload.
- PR #15 is a draft, not merged. No physical Android landscape/audible QA was available in this run.
