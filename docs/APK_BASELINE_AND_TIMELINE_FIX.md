# Uploaded APK baseline and Arrangement coordinate repair

- Baseline artifact: user-uploaded `app-debug.apk`
- SHA-256: `3a396f0f8dd4d26669527f816e0dd8b84d38d8040c3addf5cf2ed90d310b4919`
- APK size: 23,246,583 bytes (22.17 MiB)
- Package includes `liboboe.so` and `libstudiowave_audio.so` for arm64-v8a, armeabi-v7a and x86_64.
- The APK is the visual/behavioral baseline for continued development. An APK is compiled DEX, not the original Kotlin source; the source branch below is the closest repository source candidate identified by the app's UI class/signature matches. This record does not claim a byte-for-byte source reconstruction.

## Source baseline

- Repository: `jorgi80-droid/StudioWave`
- Source branch: `ui/ableton-inspired-mobile-workspace`
- Baseline commit before this repair: `2073e81841364543bd4d70ff0d9f3ac747ff99ff`

## Fix in this commit

The Arrangement track headers are a separate sibling column. TimelineGrid, the ruler, clip positions, the overview navigator, playhead and gesture centroids are all local to the timeline pane. The previous code subtracted a 94 dp track-header offset from these local coordinates, while pinch calculations used pane-local coordinates without that offset. This mixed coordinate space caused zoom anchoring and clip placement to drift.

A single `arrangementPaneViewport` constructor now explicitly uses a zero header offset for pane-local mapping. Clip dragging keeps the grabbed screen position stable if the viewport changes during a drag and converts the final drop position through the current viewport. Unit tests cover local mapping, clip duration width and anchored pinch zoom.

## Validation status

CI and device-level gesture testing must pass before claiming the repair is fully validated. This commit does not claim the APK has been reproduced from original source.
