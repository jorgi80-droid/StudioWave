# DirectWave Factory One-Shots

The Android CI workflow downloads six real WAV samples into this directory before packaging the APK. They are sourced from [Boochi44/free-drum-samples](https://github.com/Boochi44/free-drum-samples); the upstream README states the one-shot samples are licensed Creative Commons Zero (CC0 1.0).

Included: hard-kick-01.wav, hard-snare-01.wav, hi-hat-closed-01.wav, 808-bass-sub.wav, bounce-kick-01.wav, vintage-kick-01.wav.

CI checks that exactly six files exist and that each begins with the RIFF header before running the build. Source/license: https://github.com/Boochi44/free-drum-samples/blob/main/README.md

Current limitation: the audio engine does not yet instantiate sampler voices from these WAV files. DirectWave playback and filter/envelope/FX DSP still require engine integration.
