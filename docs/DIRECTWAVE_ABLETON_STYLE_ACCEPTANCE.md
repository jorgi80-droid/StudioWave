# DirectWave — Live-inspired control surface acceptance

This checklist is the acceptance target for DirectWave and does not claim device validation has already happened.

## Visual language
- Build a convincing Live-inspired instrument device: narrow title strip, enable/status lamp, device name and loaded sample; deliberate raised panels, separators, labels, and restrained aqua/orange state accents.
- Put sample identity, a real source waveform, and sample-browser actions in the visible instrument surface.
- Use rotary controls with arc/ring, pointer, label and numeric readout for Pitch, Fine Tune, Level, Attack, Decay, Sustain and Release. A set of generic sliders does not satisfy the request.
- Use tactile, stateful switches/buttons for Loop, Browse/Load and supported modes. Clearly distinguish active, inactive, loading and failed states.
- Fit the actual available landscape viewport on a phone. Avoid fixed desktop-style widths that force the controls off-screen; compact and full-screen layouts should both remain operable.
- Follow Ableton's visual concepts but draw original UI rather than copy proprietary bitmaps/assets.

## Functional integrity
- The selected factory or imported sample must be loaded by the sampler, with its displayed name and state updated and persisted.
- A waveform must be decoded from the selected source or show an honest empty/error state; do not display a placeholder as a real waveform.
- Knobs must update the same DirectWaveSampler values used during note playback and persist their settings.
- Keyboard touch triggers note-on/note-off only when the selected sample is ready.
- Loop must change actual sampler playback behavior. Do not display active state when it is not enabled.
- Do not present fake effect inserts as working audio processors. Only show an effect UI when processing is implemented.

## Factory library and licensing
- Keep an offline bundled sample accessible on a fresh install, without a network connection.
- Any downloaded third-party WAV needs a recorded upstream URL and licence; validate its RIFF header and inclusion in the packaged APK during CI.
- Do not describe generated audio as a third-party download or confuse “One Shoot.wav” with an unverified external filename.

## Validation
- Run Android Gradle build and unit tests against latest main after integration.
- On an Android device verify fit/clipping, rotary drag response, sample selection and audible output, loop, pitch/fine tune, level, ADSR and keyboard interaction while the device view is open.
- CI success cannot substitute for device checks of touch latency, visual layout, or sound output.
