# StudioWave DirectWave factory samples

The Android CI workflow downloads six WAV one-shots from
[Boochi44/free-drum-samples](https://github.com/Boochi44/free-drum-samples).
The upstream project README states that these samples are dedicated to CC0 1.0.

Included files:
- `hard-kick-01.wav`
- `hard-snare-01.wav`
- `hi-hat-closed-01.wav`
- `808-bass-sub.wav`
- `bounce-kick-01.wav`
- `vintage-kick-01.wav`

Source/license reference:
https://github.com/Boochi44/free-drum-samples/blob/main/README.md

CI verifies all six files exist and have a RIFF WAV header before building the APK.
The DirectWave browser copies selected samples from Android app assets into app-private storage.
