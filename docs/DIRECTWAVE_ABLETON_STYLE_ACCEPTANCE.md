# DirectWave — Live-inspired control surface acceptance

This is an implementation checklist for the DirectWave screen, not a claim that every point has been device-verified.

## Visual system
- Use a single Live-inspired palette and hierarchy: outer workstation frame, raised device title strip, grouped device modules, thin separators, compact uppercase labels and restrained highlight colors.
- Show the loaded sample status, sample name, waveform preview and sample-browser access in the visible top section.
- Use rotary knobs with a value arc, indicator, label and numeric readout for pitch, fine tune, level and ADSR. A slider-only layout does not meet this requirement.
- Use distinct tactile switches for loop, sample selection/load, and supported modes. State must be visually clear.
- Keep waveform/sample browser and controls readable in landscape on phone-sized displays; do not hard-code an oversized desktop width or height.
- Respect Live-inspired concepts without copying proprietary bitmaps or assets.

## Functional integrity
- Sample list entries must refer to available built-in or imported WAV files. Selecting one updates the sampler, selected filename and persisted preference.
- Loading must expose loading/ready/failure feedback. Waveform must be decoded from the selected source or clearly show an unavailable state; do not invent a waveform for external media.
- Knobs must update the same DirectWaveSampler fields used for note playback and persist their settings.
- Keyboard interactions trigger note-on/note-off only when the selected sample is loaded.
- Loop is a real sampler setting; do not visually show active mode unless the state is enabled.
- Avoid fake effect controls. Only show processing sections when actual DSP exists.

## Factory sample
- Keep an offline bundled sample available at install time and reachable from the sample browser.
- Third-party samples must have a documented licence and source attribution. Verify HTTP download, WAV/RIFF header and build inclusion in CI.
- Do not describe generated audio as a third-party download.

## Validation
- Run Android Gradle build and unit tests on the updated branch.
- Verify on an Android device: display fits without clipped knobs/buttons; rotary drag is responsive; sample list selection loads audible sound; loop, pitch, fine tune, output level and ADSR respond as expected; editor remains usable while device panel is open.
- CI success is not evidence of device visuals or audio output by itself.
