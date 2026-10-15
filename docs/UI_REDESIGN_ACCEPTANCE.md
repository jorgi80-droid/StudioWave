## Full UI redesign implementation contract

This document records the complete direction for the current StudioWave UI overhaul. It is an acceptance contract, not a claim that every feature is already implemented.

### Design direction
Ableton Live 12 is the control-surface and information-hierarchy reference. FL Studio Mobile is the interaction-speed and production-workflow reference. StudioWave must stay native, landscape-first, touch-optimized and usable on a phone. Do not copy proprietary assets; draw original components that follow the same concepts.

### One coherent state model
Arrangement, Clip View, Piano Roll, Drum Sequencer, Audio Clip Editor, Mixer, Device View, Browser, Home and Settings must share the same project/track/clip selection. A double-tap on a clip opens the correct editor for its type; long press exposes contextual actions; the selection inspector always refers to the current selected clip and track. Timebase, clip duration, ruler, grid, notes, waveform, zoom and playhead must remain mathematically consistent.

### Required UI systems
- **Transport:** compact tempo/time signature, play/pause/stop/record where supported, position, undo/redo, open/save, master level and measured master meter.
- **Arrangement:** dominant canvas; track headers, bar ruler, shared horizontal zoom/scroll, vertical track scrolling/resizing, clip select/move/resize/split/duplicate/delete, snap/division, seek and playback-follow. Respect the 1800-bar maximum.
- **Clip View / Inspector:** current clip identity/type, owning track, position and duration, plus only properties supported by the project model and engine.
- **Clip previews:** MIDI clips show note content, drum clips show active steps, audio clips display a decoded source waveform. Every preview is clipped to the clip's true bounds and scales from shared tick/source calculations.
- **Piano Roll:** full useful canvas, scrollable pitch range, consistent horizontal/vertical pinch zoom anchored to the gesture centroid, note add/select/move/resize, snap, clipboard and velocity lane connected to persisted MIDI values.
- **Drum Sequencer:** selected clip's actual pattern, clear/all/step toggle, horizontal lazy scrolling for long patterns, pattern length preserved through edits.
- **Audio Clip Editor:** decoded waveform and source trim/offset. Do not label trim as warping; tempo-synced time stretching must wait until the engine has a real processor.
- **Mixer:** fixed-width horizontally scrollable channel strips; real model-backed level, pan, mute/solo; actual per-channel peak/RMS readings and master peak/RMS/LUFS.
- **Device View / racks:** only display devices represented in persistent state and processed by actual DSP. No dummy insert knobs or fake effect rack controls. Keep an honest empty/limited state until a real registry, serialization, and processing exist.
- **Browser:** project-local search and real installed sample library, plus Android audio picker; do not imply cloud/sample-library functionality that does not exist.
- **Home/Settings:** real project open/save and persistent theme, contrast, UI scale and grid preferences. Scale must change actual layout.
- **Consistent control language:** shared typography, surface tiers, separators, toggles, fader/knob geometry, meter markings, selected/disabled feedback and touch target sizing across screens.

### Required validation
Every cohesive batch runs Gradle debug build and unit tests. Real Android testing separately checks touch targets, layout overflow, gesture anchoring, scrolling, playback and edit concurrency, audio underruns, and frame pacing. CI success is not a substitute for real-device verification. Only share an APK when the uploaded artifact exists.
