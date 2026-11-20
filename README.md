# Nex-PlayBack

Android browser and background audio player.

## Current build

- Native Android app using Kotlin.
- WebView browser with Google, Yahoo and YouTube shortcuts.
- Media3 audio player for direct HTTPS audio streams and audio files selected from device storage.
- MediaSession foreground service for lock-screen, notification and Bluetooth media controls.
- Floating WebView window. Android's draw-over-other-apps permission is requested only when the user taps **FLOAT**.
- Minimum Android version: Android 10 (API 29).
- Current app version: 0.1.1.

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

The CI APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and releases

- Pushes to `main` create a temporary CI artifact named `Nex-PlayBack-CI`.
- Pushing a version tag such as `v0.1.1` creates an APK asset named `Nex-PlayBack-v0.1.1.apk` and publishes it to that GitHub Release.
- The APK filename shown in tagged releases does not contain the word `debug`.

**Signing note:** Tagged APKs currently use the Android debug signing certificate so they remain installable. This is not a permanent production signing key; set up a private release keystore in GitHub Actions before wider distribution. The original v0.1.0 release remains unchanged.
