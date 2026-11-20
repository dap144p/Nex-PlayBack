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

## Background resource handling

- The main WebView is paused and removed from rendering when the activity is fully stopped, then resumed when the app returns.
- The floating WebView is paused when its window is minimized and resumed when restored.
- Global WebView layout/JavaScript timers are paused only while all Nex-PlayBack browser windows are inactive. This avoids freezing a floating browser that is still visible.
- The native Media3 playback service is independent from WebView lifecycle handling, so native audio continues while browser rendering is paused.
- Website media may follow the website/WebView background rules; use the native audio player for background audio that must continue.

## Important playback limits

The native player accepts direct HTTPS media URLs and local audio files. It does not extract, download or convert audio from YouTube or other sites. YouTube and other websites keep their own playback, account and background-playback rules. WebView page audio may stop when the app is backgrounded; the native Media3 player is the reliable background playback path.

## Build

Open the repository in Android Studio, or run:

```sh
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions publishes the same debug APK as a workflow artifact.
