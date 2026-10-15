# Uploaded APK baseline and source restoration

- Reference artifact: `app-debug.apk`, supplied in this conversation on 2026-10-15.
- SHA-256: `3a396f0f8dd4d26669527f816e0dd8b84d38d8040c3addf5cf2ed90d310b4919`.
- Package code inspection found six DEX files, native `libstudiowave_audio.so` and `liboboe.so`, and factory WAV preset resources.
- DEX symbols include `DirectWaveSampler`, `DirectWaveDevicePanel`, `FloatingTimelineControl`, `TimelineTimebase`, and `TimelineViewport`. These match branch `ui/ableton-inspired-mobile-workspace` much more closely than the previous `main` application source.
- APKs do not contain the original Kotlin source tree or a lossless source map. This branch restores the closest matching available source from GitHub; it does not claim to reverse-engineer the original source byte-for-byte.

## Timeline regression fix in this integration

- Measure the real Arrangement viewport width and pass it to all shared geometry consumers.
- Clamp horizontal scroll to `[0, maxArrangementTick - visibleWidth/pixelsPerTick]`, including after zoom changes.
- Keep gesture coordinates pane-local and use one fractional-tick anchor through pinch zoom.
- Keep shared scroll state synchronized with the viewport's normalized range.
- Add tests for scroll limits, non-finite coordinates, clip width/resize mapping and repeated zoom.
