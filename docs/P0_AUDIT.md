# P0 Audit — StudioWave Independent Native Android DAW

## Isolation
- This is a standalone Android-native source tree. Do not copy from or modify `jorgi80-droid/openDAW`.

## Architecture
- UI: Kotlin + Jetpack Compose, landscape workspace in `app/src/main/java/com/studiowave/app/MainActivity.kt`.
- Data: JSON project model in `data/ProjectModel.kt`.
- Audio prototype: `AudioTrack` stream through Kotlin/JNI and C++ tone generator.
- Native build: CMake / Android NDK; Oboe is not yet integrated.
- CI workflow: `.github/workflows/android-ci.yml` builds debug APK and runs unit tests on push/PR.

## Current limitations
The project is a prototype. Arrangement clips and waveforms are not yet backed by full audio/MIDI scheduling. Mixer routing, production DSP effects, recording, `.sdaw` persistence, autosave/recovery, ZIP sharing, mastering, WAV export, full Settings/Log, Oboe low-latency backend, and measured Eco/Standard profiles are not complete.

## P0 acceptance criteria
1. `assembleDebug` succeeds using JDK 17, SDK platform 36, NDK 27.2.12479018, and CMake 3.22.1.
2. `testDebugUnitTest` passes.
3. Install and launch on a real Android device; verify landscape, system bars, JNI load, play/stop lifecycle, and crash log.
4. Test project-model round trip and invalid schema behavior.
5. Keep CI/build evidence separate from device-playback evidence. Do not claim latency/underrun performance without measurements.

## Next
Push the complete independent source tree, inspect CI failures, then fix P0 before interactive timeline P1.