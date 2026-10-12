# StudioWave — Native Android DAW

Independent Android-native DAW starter project. This repository does not derive its source tree from `openDAW`.

## Current stage

**P0 baseline / early prototype.** The app includes a Compose-based landscape workspace, a JSON project model, and a JNI/C++ tone generator streamed through Android `AudioTrack`. This is not yet a complete DAW.

## Build

Requirements: JDK 17, Android SDK platform 36, Android NDK 27.2.12479018, and CMake 3.22.1.

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

The debug APK should be at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

See `docs/P0_AUDIT.md` for architecture, acceptance criteria, test evidence requirements and known gaps.