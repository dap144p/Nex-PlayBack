# Nex-PlayBack

Android browser and background audio player.

## Current build

- Native Android app using Kotlin.
- WebView browser with Google, Yahoo and YouTube shortcuts.
- Media3 audio player for direct HTTPS audio streams and audio files selected from device storage.
- MediaSession foreground service for lock-screen, notification and Bluetooth media controls.
- Floating WebView window. Android's draw-over-other-apps permission is requested only when the user taps **FLOAT**.
- Minimum Android version: Android 10 (API 29).
- CI workflow builds a debug APK on pushes to `main` and `feature/android-player-foundation`.

## Important playback limits

The native player accepts direct HTTPS media URLs and local audio files. It does not extract, download or convert audio from YouTube or other sites. YouTube and other websites keep their own playback, account and background-playback rules. WebView page audio may stop when the app is backgrounded; the native Media3 player is the reliable background playback path.

## Build

Open the repository in Android Studio, or run:

```sh
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions publishes the same debug APK as a workflow artifact.
