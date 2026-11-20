# Nex-PlayBack

Android browser and background media player.

## Current build

- Native Android app using Kotlin.
- WebView browser with Google, Yahoo and YouTube shortcuts.
- Media3 audio player for direct HTTPS audio streams and audio files selected from device storage.
- Web playback tracking for standard HTML audio/video elements.
- MediaSession foreground service publishes web media title, artist, playback state and seek position to Android media controls.
- Notification and lock-screen controls can play, pause, stop and seek web media when the page exposes a standard HTML media element.
- Floating WebView windows with a movable launcher, multiple windows, drag, resize, maximize, minimize, hide and restore.
- Minimum Android version: Android 10 (API 29).
- Current app version: 0.1.1.

## Background behavior

- The browser WebView remains alive while a page has an active or paused HTML media item, allowing supported page playback to continue when the app UI is in the background.
- A MediaSession-backed foreground service keeps Android media controls available while web playback is active.
- The main WebView is retained and detached from the Activity if the Activity is destroyed while it still owns a media item, then reused when the app is opened again.
- Floating windows can be hidden or minimized without destroying their WebViews. They can be restored from the floating launcher.
- Browser views without a media item are paused when their owning screen is stopped.
- Global WebView timers are paused only while all managed browser windows are inactive.

## Playback limits

Web media control uses the browser page's standard HTML audio/video element. It does not extract, download, convert or re-host a site's media stream. Some sites use cross-origin embedded players, DRM, custom playback engines or their own background rules; those pages may not expose controllable media metadata or may stop playback when backgrounded.

YouTube opens through its official website. Background playback remains subject to YouTube's own account, product and playback restrictions. Nex-PlayBack does not bypass those restrictions.

The native player continues to support direct HTTPS media URLs and local audio files.

## Build

Open the repository in Android Studio, or run:

```sh
gradle assembleDebug
```

The CI APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and releases

- Pushes to `main` create a temporary CI artifact named `Nex-PlayBack-CI`.
- Pull requests targeting `main` run the Android build workflow.
- Pushing a version tag such as `v0.1.1` creates an APK asset named `Nex-PlayBack-v0.1.1.apk` and publishes it to that GitHub Release.
- The APK filename shown in tagged releases does not contain the word `debug`.

**Signing note:** Tagged APKs currently use the Android debug signing certificate so they remain installable. This is not a permanent production signing key; set up a private release keystore in GitHub Actions before wider distribution. The original v0.1.0 release remains unchanged.
