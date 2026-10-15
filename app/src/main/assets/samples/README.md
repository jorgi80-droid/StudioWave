# DirectWave Factory One-Shots

The Android CI workflow downloads six real WAV files into this directory before packaging the APK. They come from [Boochi44/free-drum-samples](https://github.com/Boochi44/free-drum-samples); the upstream README states the one-shot samples are Creative Commons Zero (CC0 1.0).

Included: hard-kick-01.wav, hard-snare-01.wav, hi-hat-closed-01.wav, 808-bass-sub.wav, bounce-kick-01.wav, vintage-kick-01.wav.

The CI step verifies six WAV files and their RIFF headers before the build. Source/license: https://github.com/Boochi44/free-drum-samples/blob/main/README.md

The current audio engine does not yet instantiate sampler voices from these WAVs; playback and sampler/filter/ADSR DSP remain future integration work.
