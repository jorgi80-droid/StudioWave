# DirectWave Factory One-Shots

The Android CI workflow downloads six WAV assets into this directory before packaging the APK. They are sourced from [Boochi44/free-drum-samples](https://github.com/Boochi44/free-drum-samples); the upstream README states the one-shot samples are Creative Commons Zero (CC0 1.0).

Included files: hard-kick-01.wav, hard-snare-01.wav, hi-hat-closed-01.wav, 808-bass-sub.wav, bounce-kick-01.wav, and vintage-kick-01.wav.

CI verifies the six WAVs and their RIFF headers before the build. License/source: https://github.com/Boochi44/free-drum-samples/blob/main/README.md

The current arrangement engine does not yet play these WAVs as sampler voices; that requires audio-engine integration.
