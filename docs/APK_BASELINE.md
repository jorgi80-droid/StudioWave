# Uploaded APK baseline and source restoration

- Reference artifact: user-uploaded `app-debug.apk`, supplied on 2026-10-15.
- SHA-256: `3a396f0f8dd4d26669527f816e0dd8b84d38d8040c3addf5cf2ed90d310b4919`.
- Size: 23,246,583 bytes (22.17 MiB).
- Package contains six DEX files, native `libstudiowave_audio.so` and `liboboe.so` for arm64-v8a, armeabi-v7a and x86_64, plus factory WAV resources.
- DEX symbols include `DirectWaveSampler`, `DirectWaveDevicePanel`, `FloatingTimelineControl`, `TimelineTimebase` and `TimelineViewport`. The restored source tree on `main` is the closest matching repository source identified.
- APKs do not contain the original Kotlin source tree or a lossless source map. The APK is the behavioral baseline; this does not claim byte-for-byte source reconstruction.

## Timeline regression repair

- The Arrangement track headers are a separate sibling column. A 94 dp header offset was incorrectly subtracted from pane-local coordinates while pinch centroids were already local, so zoom, grid/ruler, clips and hit-testing used inconsistent coordinate spaces.
- Use one pane-local viewport for the measured Arrangement width across grid, ruler, clips, overview navigation, playhead, clip menu, hit-testing, pan, pinch and drag/drop.
- Vertical gesture hit-testing and pinch anchoring account for both the overview and ruler top inset.
- Clip drag keeps the grabbed screen position stable if zoom or scroll changes during the gesture and converts the final drop position through the current viewport.
- Preserve the measured viewport-width/max-scroll behavior already restored on `main`.
- Regression tests cover pane-local coordinates, clip duration width and anchored zoom in addition to the existing 1800-bar, scroll-bound and resize tests.

## Validation

CI must pass for the new PR head, followed by Android landscape device testing of pinch zoom, horizontal/vertical scroll, ruler seeking, clip drag, left/right resize and playback-follow behavior. This file records the baseline and intended validation; it is not a claim that device testing has already passed.
