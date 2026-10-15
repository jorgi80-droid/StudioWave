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

## Fixes in this batch

- Arrangement track headers are a separate sibling column, but the old timeline viewport subtracted a 94 dp header offset from local canvas coordinates while pinch gestures used pane-local coordinates. The shared `arrangementPaneViewport` constructor now guarantees a zero header offset for the timeline pane.
- Clip dragging preserves the grabbed screen position if scroll/zoom changes during the gesture and converts the final drop position through the current viewport.
- Vertical hit-testing and pinch zoom now use the complete top inset (overview + ruler), not just the ruler height, preventing a vertical anchor offset when navigating the track rows.
- Regression tests cover pane-local coordinate mapping, clip duration width and anchored pinch zoom.

## Validation status

- Added regression cases for repeated zoom and clip/ruler/grid edge mapping at multiple scales and scroll positions.
- The latest implementation is tracked on `ui/ableton-inspired-mobile-workspace`; do not build from `main` until that branch is merged. Branch and APK version provenance must be checked for every released APK.
- CI and device-level gesture testing must pass before claiming the repair is fully validated. This commit does not claim the APK has been reproduced from original source.
