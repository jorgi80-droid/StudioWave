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

## Official references
- https://www.ableton.com/en/live-manual/12/arrangement-view/
- https://www.ableton.com/en/live-manual/12/clip-view/
- https://www.ableton.com/en/live-manual/12/audio-clips-tempo-and-warping/
- https://www.ableton.com/en/live-manual/12/editing-midi/
- https://www.ableton.com/en/live-manual/12/mixing/
- https://www.ableton.com/en/live-manual/12/working-with-instruments-and-effects/
- https://www.ableton.com/en/live-manual/12/instrument-drum-and-effect-racks/
- https://www.ableton.com/en/live-manual/12/live-concepts/
