# Ableton-Inspired HTML Reference Audit — 2026-10-16

Repository: https://github.com/jorgi80-droid/StudioWave
Working branch: `ui/ableton-batch-viewport`
Related draft PR: #15
Audit basis: five user-uploaded HTML files; this is an implementation audit, not an assertion of proprietary Ableton source.

## 1. Source inventory

| File | Approx. size | Design focus | Implementation characteristics |
|---|---:|---|---|
| `ableton_live_interface_mobile_replica.html` | 22.8 KB / 469 lines | Compact mobile Arrangement, Mixer, FX tabs | Tailwind CDN + Inter font import; inline JS; canvas waveforms, synth and EQ curves |
| `ableton_live_interface_replica.html` | 55.2 KB / 977 lines | Broad desktop-style Live workspace | Tailwind CDN + Inter; transport, Session/Arrangement switch, overview, tracks, devices and canvas-based visualizations |
| `ableton_live_interface_1_1_replica.html` | 38.8 KB / 629 lines | Dense dark Live-like control surface | Tailwind CDN; CSS variables, compact transport, status toast, knob and LED styling; many controls report status rather than perform DAW operations |
| `daw_landscape.html` | 36.9 KB / 641 lines | Landscape-first DAW layout with arrangement and resizable detail area | Self-contained CSS/JS; explicit browser/main/split/detail grid; clip editor, Piano Roll, velocity lane, device cards and mixer |
| `daw_mobile.html` | 23.2 KB / 530 lines | Compact mobile DAW with timeline and horizontal device strip | Self-contained CSS/JS; responsive compact toolbar, overview, arrangement, mixer, device cards and canvas sketches |

The three Ableton-named files load Tailwind from `https://cdn.tailwindcss.com`; two also import the Inter font from Google Fonts. Those remote dependencies are not appropriate as a runtime requirement for an offline native Android DAW. The two `daw_*.html` files have no external script or font dependency in their markup.

## 2. Reusable visual system

- Dark industrial surfaces: near-black workspace, graphite panel surfaces, thin dark borders, compact spacing, subdued secondary text.
- Warm orange focus/accent with stateful indicators; track colors distinguish clips and tracks rather than coloring the entire workspace.
- Compact transport cluster: tempo, play/stop/record, position readout, loop/metronome and view switching.
- Fine ruler and arrangement grid, overview/minimap, clip labels and waveform/pattern previews.
- Device cards with title strip, enable indicator, grouped parameters, rotary knobs, buttons, and compact numeric readouts.
- Mixer strips with track color, mute/solo/arm state, volume/pan controls and level-meter region.
- Dense landscape workspace: browser rail, arrangement, track headers, bottom detail/device area and a draggable split in `daw_landscape.html`.
- Touch-first behavior in the DAW examples: large enough interactive regions, horizontal device scrolling, mobile toolbar overflow, and portrait guard in `daw_landscape.html`.

These are visual and interaction references, not proof of Ableton's proprietary implementation. Use an original StudioWave palette and component implementation.

## 3. Component inventory and native mapping

| Area | HTML reference evidence | StudioWave adaptation |
|---|---|---|
| Arrangement/ruler/grid | `daw_landscape.html`: `#timeRuler`, `#lanes`, `#playhead`, `#loopBand`; `daw_mobile.html`: `.rulerbar`, `.lanes`, `.playhead` | Keep native Compose rendering and the shared `TimelineViewport`/musical timebase. Never copy HTML pixel offsets as clip data. |
| Track headers/clips | `.trackHead`, `.trackRow`, `.clip`, color classes, clip canvases | Match typography, borders, clip color hierarchy and compact control grouping; retain existing clip model and gesture hit-testing. |
| Audio waveform | Canvas elements `wave1` / `wave3`; `drawWave` functions | Use actual decoded audio peaks, not decorative randomized waveform. Existing native waveform path should remain authoritative. |
| MIDI/piano roll | `#rollcv`, `drawRoll`, `#veloCv`, `#velWrap` | Reuse existing native Piano Roll model and editing gestures; use the HTML only as visual guidance for ruler, keyboard rows and velocity lane. |
| Mixer | Channel strip cards, `.mixer`, `.trackhead`, fader and VU styles | Adapt compact strip geometry, track colors, readable values and real meters. Do not add fake meter animation or disconnected controls. |
| Device view | `#pDev`, `.devCard`, device tabs, instrument cards | Native horizontally scrolling insert chain; fixed add-device affordance; persisted enabled state and parameters. |
| DirectWave/sampler | `simplerCard`, sampler/wavetable card patterns, knobs, ADSR sliders and sample display | Preserve the current native `DirectWaveDevicePanel`, sample import, real waveform and sampler behavior. Improve grouping/knob hierarchy without nesting helpers inside the sample list. |
| FX/EQ | `eqCard`, `compCard`, `reverbCard`, `drawEQ` / `drawEqCurve` | Parameter UI must map to `TrackDevice` and DSP keys. EQ response curve only when calculated from actual processor parameters. |
| Synth/LFO/analyzer | `drawSynthWave`, `drawFilterCurve`, `drawLfoWave`, canvas panels | Render only actual state. A decorative canvas is not an analyzer or proof of audio processing. |
| Transport | `togglePlay`, `toggleRecord`, `rewindTransport`, loop/metronome and time readout | Keep native transport/audio-engine state as the source of truth. Avoid HTML mock timing or independent playhead clocks. |
| Browser | `#browser`, categories, search/list rows | Native offline sample browser with categories and search; respect asset licenses and import permission lifecycle. |
| Split/detail navigation | `#split`, `#detail`, `#dtabs`, mobile tabs | Use Compose state and measured pane dimensions; preserve landscape-first layout and hide status/navigation bars as already supported. |

## 4. Interaction and JavaScript findings

- `ableton_live_interface_mobile_replica.html` provides mobile tabs, transport toggles, time display/playhead animation, and canvas waveform/synth/EQ drawing.
- `ableton_live_interface_replica.html` adds Session/Arrangement switching, loop/metronome controls and additional filter/LFO/EQ canvas drawing.
- `ableton_live_interface_1_1_replica.html` includes simple knob/LED state interactions, track selection and toast messages; status-message handlers are not equivalent to a DAW audio engine.
- `daw_landscape.html` has a richer local prototype architecture: browser and arrangement builders, clip selection/editor rendering, waveform/Piano Roll drawing, device-card builders, mixer/session builders and playback tick logic.
- `daw_mobile.html` has responsive arrangement/device/mixer builders, canvas waveform/oscillator/EQ drawings, knob gestures and a mock playback tick.

The HTML examples are prototypes. Their canvas graphics and timer-driven playback should not be copied as proof of native DSP, MIDI playback, persistence or low-latency audio.

## 5. Existing StudioWave source comparison

Inspected the current `ui/ableton-batch-viewport` source around `DeviceDock`, `DirectWaveDevicePanel`, `BuiltInStudioSample`, `TrackEffectDeviceCard`, and `DeviceParameterSlider`.

- `DeviceDock` is already a standalone composable; it selects a track, presents the instrument/device chain, and routes device updates through the project-change callback.
- `DirectWaveDevicePanel` is a separate composable. The built-in sample list is a local value inside it; `BuiltInStudioSample` and `TrackEffectDeviceCard` are top-level declarations after the sampler panel. This is the correct scope arrangement for the previously reported helper-inside-sample-list regression.
- FX cards persist their `TrackDevice` values through the track's device list and expose enable/remove actions.
- The current Parametric EQ Eight UI shows eight frequency-labelled gain sliders (`band1`…`band8`). It does not yet expose per-band frequency, Q/bandwidth, or filter type in this UI; do not label this a complete eight-band parametric EQ until the model and DSP implement those parameters.
- Current FX cards use sliders for most parameters despite the HTML references showing compact knobs. A visual pass can use native rotary controls, but must preserve accurate parameter ranges, labels and accessibility.
- Current DirectWave sample UI saves sampler values to SharedPreferences and has its own sampler path. Do not claim that these settings are part of the serialized project or that its voices are integrated into arrangement MIDI rendering without verifying that path.

## 6. Recommended implementation sequence

1. Preserve the currently green DirectWave/FX scope structure; add regression checks so helpers remain top-level and sample selection/import remains intact.
2. Build shared native visual primitives for compact device headers, state LEDs, rotary controls and parameter readouts. Keep controls tied to existing model/DSP keys.
3. Improve FX cards as one coherent pass: use compact knobs where suitable; add an EQ response graph only from the real current parameters; preserve sliders where fine touch precision benefits.
4. Reconcile the Parametric EQ Eight model, defaults, serialization and DSP before exposing frequency/Q/type controls. Add response and parameter-change tests.
5. Compare arrangement and Piano Roll against the HTML's grid/ruler/clip structure while retaining the shared viewport transform, stable musical coordinates and 1,800-bar limit.
6. Continue with real meter/analyzer data and actual audio-engine routing; never substitute canvas animation for signal analysis.
7. Run unit tests and Android debug build in GitHub Actions, then test playback, touch gestures, save/reopen and audible processing on an Android landscape device.

## 7. Licensing and dependency notes

- The HTML files are user-provided references; their authorship and licensing are not established by the files alone. Treat them as visual references unless the user confirms usage rights.
- Do not copy proprietary Ableton code/assets or imply official affiliation. Reimplement design principles and original StudioWave components.
- Avoid introducing Tailwind CDN or remote font loading into the native app. Prefer bundled/system fonts and native Compose drawing.
- No conclusion about third-party asset licensing can be made from the HTML markup alone.

## 8. Current verification snapshot

- Current branch head at audit time: `c61feedbaf83411a7afde5bdac362a5b2a104670`.
- GitHub Actions run `38748749046`: completed successfully; Build debug APK, Run unit tests and Upload debug APK all passed.
- PR #15 remains open and draft. Green CI is not evidence of on-device audio/touch correctness.
- Physical Android validation remains pending.


## Integrated deep-reference mapping and current implementation

The eight official Ableton Live 12 manual areas supplied by the project owner were reviewed as workflow, interaction and information-hierarchy references. StudioWave remains an original native Android DAW/instrument. No proprietary Ableton assets or pixel-identical implementation are claimed.

| Reference | StudioWave mapping and current acceptance boundary |
| --- | --- |
| Arrangement View | Keep one shared ruler/grid/time transform, viewport overview, zoom/scroll and clip gesture anchors. This batch does not rewrite the already-stabilized arrangement timebase or the 1,800-bar bound. |
| Clip View | Clip data remains source-specific. MIDI note pitches, positions, durations and velocity feed an instrument renderer; audio trim and offset refer to actual source time. |
| Audio Clips, Tempo and Warping | Display and playback use real audio sources/waveforms where available. Source trim is distinct from tempo warp/time-stretch; a tested warp-marker model is not claimed by this batch. |
| MIDI Editor/Piano Roll | MIDI notes remain the score. Tuned frequency calculations now affect the rendering path, not just the displayed scale name. |
| Mixing | Meter/loudness values remain derived from processed audio. New spectrum bars consume the post-master PCM snapshot rather than random or synthetic animation. |
| Device View, Instruments and Effects | Device parameters live in project state. Existing serial insert order and bypass remain owned by `TrackDeviceDsp`; EQ graph values are computed from peaking-biquad response equations. |
| Instrument, Drum and Effect Racks | A persisted synth device and serial effect chain do not imply a complete parallel rack/Macro implementation; those unsupported behaviors are not represented as finished. |
| Live Concepts | Compact transport, clear workspace switching, coherent grouping and legible contrast inform the mobile-landscape layout. |

### UI issues addressed from supplied screenshots
- Separated room/canvas/panel/control tiers in graphite and improved muted/text contrast.
- Increased Arrangement clip title and metadata size and added truncation instead of allowing text to spill.
- Reduced resize grip fill/glyph prominence while keeping the existing touch hit areas and geometry.
- Replaced the local-only MELD-inspired demo and animated fake spectrum with original StudioWave Dual OSC controls, project-backed parameter writes, explicit synth/sample mode and actual master-PCM analyzer values.

### Audio data path and verification boundaries
- MIDI event schedule → project-backed oscillator/filter/envelope/tuning parameters → configured renderer → existing track/master processing.
- Post-master PCM → bounded non-blocking offer into preallocated slots → worker-thread 1024-point FFT/Hann window → 64 logarithmic display bands → Compose snapshot.
- EQ node drag → persisted `PARAM_EQ_8` gain/frequency values → existing serial effect DSP. Graph response is computed for the same eight peaking bands, including real insert bypass state.
- Regression tests cover tuning intervals, voice envelopes, FFT bins, EQ response/bypass, and device JSON round-trip. Android physical testing is still required for touch ergonomics, audible consistency, microphone lifecycle and reopened recorded takes.
