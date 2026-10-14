# StudioWave — Ableton Live 12 UI integration map

This implementation uses the Ableton Live 12 Reference Manual as a workflow and interaction reference while keeping StudioWave's existing Kotlin/Compose UI, project model, and audio engine intact.

## Implemented in this increment
- Arrangement keeps the existing timeline geometry, zoom/scroll mapping, clip selection, playback-follow behavior, and track operations.
- A compact, dismissible Clip View inspector can be opened from the Arrangement floating controls for the selected clip.
- The inspector displays the owning track, clip type, bar range, and duration derived from the shared musical timebase.
- Open Editor routes to the existing audio editor, drum sequencer, or MIDI piano roll; Duplicate uses the existing clip operation. These controls are connected to real app actions.
- Device-chain visibility remains available as a separate compact control.

## Reference mapping and integration order
1. Arrangement View: overview/navigation, beat-time ruler, follow playback, locators, automation and track controls.
2. Clip View: clip properties, loop region, start/end, and editor mode.
3. Audio Clips, Tempo and Warping: tempo-aware audio alignment and genuine time-stretch/warp processing; UI must not imply processing exists before the engine supports it.
4. Editing MIDI: note editing, velocity lane, snap, zoom and scroll.
5. Mixing: channel faders, pan, mute/solo, meters and routing.
6. Instruments and Effects: ordered device chain and track-specific device insertion.
7. Instrument, Drum and Effect Racks: chains, drum pads and mapped macro controls.
8. Live Concepts: project/track/clip/device separation and Arrangement-first workflow.

## Safety constraints
- Do not replace or rewrite the existing audio engine or `.sdaw` project serialization as part of UI work.
- Prefer small additive changes that preserve existing handlers and project data.
- Do not add inert controls. Any new visible control must invoke an implemented operation or be omitted.
- Validate with Android CI and device-level touch testing before calling the UI complete.

## Current implementation status — mobile workspace pass

### Working UI backed by existing project or engine state
- **Arrangement:** retains the shared `TimelineViewport` and `TimelineTimebase`, clip selection/context actions, clip move/resize, playback seek/follow, track rename/color/mute/solo/reorder/resize, Fit, snap toggle and division selector. The selected-clip **Clip View inspector** now reports clip type, track, start/end bars and clip-specific note/step/source information. Editor, duplicate and rename controls call real handlers.
- **MIDI clip thumbnails:** display clip-relative note positions, pitch bands, beat/bar lines and velocity-sensitive contrast.
- **Drum clip thumbnails and Step Sequencer:** expose existing trigger steps; CLEAR/ALL and individual steps persist through the project model.
- **Piano Roll:** uses the project PPQ and time signature, supports horizontal/vertical pinch zoom and drag-to-scroll, a pitch ruler, note insertion, non-destructive note selection, drag move/pitch, right-edge length resize, explicit delete, copy/paste and note-length actions. Velocity mode updates persisted velocity. The compact virtual **note input** row inserts MIDI note events into the selected clip; it does not claim to be a live-playable synth keyboard.
- **Audio Clip Editor:** decodes source waveform peaks and allows trim-in/trim-out/reset ranges to persist to the current clip model. It is not a time-stretch/warp engine.
- **Clip View inspector:** reports selection-aware clip metadata and offers working start-bar controls for every clip plus length-by-bar controls for MIDI/drum pattern clips. Audio source boundaries remain controlled by the Audio Editor and clip resize gestures.
- **Mixer:** track volume/pan/mute/solo/master gain are backed by the mix model. Channel stereo meters now publish independent left/right peak and RMS readings measured from each track's interleaved stereo bus. The master meter uses the engine's peak/RMS/LUFS snapshots.
- **Browser:** searchable current-project track/clip lists; selecting/opening items routes to existing editors. Audio import launches Android's document picker. The built-in device library is honestly empty until a device registry exists.
- **Device Dock:** track-selection, volume, pan, mute and solo work against the real track model. The Device View uses explicit Available/Limited/Unavailable status cards, not inert FX knobs.
- **Audio settings:** inactive output mode/sample rate/buffer selectors have been removed. The screen reports the real 44.1 kHz stereo PCM16 output and 256-frame mix block used by the playback engine.

### Remaining scope that must not be represented as implemented
- **Audio warp/time-stretch and tempo-following source alignment:** no quality-preserving time-stretch processor exists yet.
- **Instrument / effect racks, device insertion, effect order, macros, parameters, wet/dry, bypass and routing:** the project model has no serializable device chain and the audio engine has no matching DSP processor registry. Do not expose active-looking fake controls until both exist.
- **Actual acoustic/sample instrument loading and drum sample voices:** the current arrangement renderer is still a limited basic oscillator/percussion implementation, not a full instrument engine.
- **Automation lanes/locators, crossfades and complete Ableton clip loop controls:** need matching project data and engine semantics before controls can be added.
- **Real-device verification:** touch reachability, layout at supported landscape resolutions, sustained playback while editing, frame pacing, and accessibility still require checking on the target Android device.

### CI / safety
- Build the debug APK and run `testDebugUnitTest` before considering a change validated.
- Keep the current Kotlin/Compose UI additive. Do not rewrite the audio engine or `.sdaw` schema as part of a styling-only change.
- CI's Android SDK/NDK setup uses `sdkmanager` with retry because a previous runner attempt received a truncated NDK archive. A setup failure before the Gradle step is environmental, not a Kotlin compile result; still require a successful new run.
- Ableton Live is a design/workflow reference here; do not bundle proprietary Ableton images, icons, fonts or extracted assets without permission. Use native vector/Compose drawing for original, Live-inspired control surfaces.

## Official references
- https://www.ableton.com/en/live-manual/12/arrangement-view/
- https://www.ableton.com/en/live-manual/12/clip-view/
- https://www.ableton.com/en/live-manual/12/audio-clips-tempo-and-warping/
- https://www.ableton.com/en/live-manual/12/editing-midi/
- https://www.ableton.com/en/live-manual/12/mixing/
- https://www.ableton.com/en/live-manual/12/working-with-instruments-and-effects/
- https://www.ableton.com/en/live-manual/12/instrument-drum-and-effect-racks/
- https://www.ableton.com/en/live-manual/12/live-concepts/


## Integration status after the latest workspace pass

- **Arrangement previews:** MIDI mini-notes are clipped to their own clip rectangle; drum previews display persisted active steps; audio thumbnails decode cached source peaks asynchronously and, when media duration metadata is available, map source offset/clip length through the same BPM/PPQ tick-to-seconds conversion as the playback engine. If duration metadata is unavailable, the preview falls back to the full decoded source rather than guessing frame offsets.
- **Piano Roll:** 120 MIDI pitches remain reachable through the pitch ruler and vertical scrolling. Horizontal/vertical pinch zoom and drag-scroll are retained; note events are clipped to pattern duration when patterns are shortened. Note selection, move/pitch drag, right-edge resize, delete, copy/paste and velocity updates modify the real project model. The visible range calculation must remain shared by toolbar zoom and gestures.
- **Clip View:** selected clips expose model-backed start-bar controls; MIDI/drum patterns expose length-by-bar controls. Shortened pattern notes are clipped/dropped safely. Audio trim sliders update source offset and length in the existing model and show the selected trim range in the waveform.
- **Mixer:** channel fader, pan, mute/solo and master gain are model-backed; track meters read independent left/right peak and RMS snapshots from the playback engine. Preserve engine meter publication and do not fake stereo values.
- **Browser:** project search/category controls and track/clip open actions must use the currently selected category's result list. Audio import invokes the system picker. Device Library remains an honest empty state until a real instrument/processor registry is connected.
- **Theme/layout:** Live Light is the new-install default; Compose Material uses the chosen light/dark scheme, while native-drawn surfaces use the matching palette. Keep landscape-first compact spacing and the main arrangement viewport intact.
- **Drum Step Sequencer:** long patterns up to the project safety cap (4096 steps) remain editable through a lazy horizontal row. CLEAR/ALL preserve the pattern's existing length. `normalizeDrumPatternForEditor` is shared by the editor and four regression tests.
- **Selected-clip routing:** opening a clip from Arrangement, Clip View or Browser passes its exact clip ID to the target editor; track-only navigation falls back to the first clip only when no selected clip is provided.
- **Device and rack processing:** no serializable device/effect chain, sample instrument registry or warp/time-stretch processor exists yet. Do not mark unavailable effects or warp controls as working; model and audio-engine implementation are required first.
- **Validation:** CI #516 passed Browser category/filter empty states, #519 passed selected-clip routing and four drum-pattern normalization tests, and #520 passed lazy horizontal step-cell rendering. CI #522 passed source-duration range calculation, #523 passed Arrangement waveform integration, and #524 passed four `AudioWaveformRangeTest` cases with APK upload. CI #525 is verifying the documentation refresh; the newest head remains pending until that run finishes. Temporary callback compile failures in #510–#514 were corrected; #515 and later code/test runs through #524 passed.
- **References:** retain the official Live 12 manual links below and use the interface as a reference, not as a source for redistributing proprietary images/fonts/assets.
