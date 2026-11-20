package com.nexplayback.app

import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.os.Handler
import android.os.Looper
import java.util.IdentityHashMap

data class WebMediaInfo(
    val title: String,
    val artist: String,
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val isSeekable: Boolean
)

object WebPlaybackCoordinator {
    private data class Entry(
        var info: WebMediaInfo? = null,
        var touchedAt: Long = 0L,
        val isMain: Boolean
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val entries = IdentityHashMap<WebView, Entry>()
    private var mainWebView: WebView? = null
    private var activeWebView: WebView? = null
    private var suppressedPausedView: WebView? = null
    private var lastTouched = 0L
    private var playbackListener: ((WebMediaInfo?) -> Unit)? = null

    fun obtainMainWebView(context: Context): WebView {
        mainWebView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return createWebView(context, isMain = true)
    }

    fun createFloatingWebView(context: Context): WebView = createWebView(context, isMain = false)

    private fun createWebView(context: Context, isMain: Boolean): WebView {
        val webView = WebView(context.applicationContext)
        configure(webView)
        entries[webView] = Entry(isMain = isMain)
        if (isMain) mainWebView = webView
        return webView
    }

    fun configure(webView: WebView) {
        if (!entries.containsKey(webView)) entries[webView] = Entry(isMain = false)
        webView.addJavascriptInterface(MediaBridge(webView), JS_BRIDGE_NAME)
    }

    fun onPageFinished(webView: WebView) {
        if (entries.containsKey(webView)) {
            webView.evaluateJavascript(MEDIA_TRACKING_SCRIPT, null)
        }
    }

    fun setPlaybackListener(listener: ((WebMediaInfo?) -> Unit)?) {
        playbackListener = listener
        listener?.invoke(currentInfo())
    }

    fun isPlaying(webView: WebView): Boolean = entries[webView]?.info?.isPlaying == true

    fun shouldRetain(webView: WebView): Boolean = entries[webView]?.info?.hasMedia == true

    fun retainMainWebView(webView: WebView) {
        if (!shouldRetain(webView)) {
            destroyWebView(webView)
            return
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        mainWebView = webView
    }

    fun destroyWebView(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        entries.remove(webView)
        if (mainWebView === webView) mainWebView = null
        if (activeWebView === webView) {
            activeWebView = null
            selectActiveWebView()
        }
        WebViewPowerManager.destroy(webView)
        playbackListener?.invoke(currentInfo())
    }

    fun setPlayback(playing: Boolean) {
        mainHandler.post {
            val webView = activeWebView ?: return@post
            val action = if (playing) "play" else "pause"
            webView.evaluateJavascript(
                "(function(){var m=window.__nexPlaybackLastMedia;if(!m)return;try{var p=m.$action();if(p&&p.catch)p.catch(function(){});}catch(e){}})();",
                null
            )
        }
    }

    fun stopPlayback() {
        mainHandler.post {
            val webView = activeWebView ?: return@post
            suppressedPausedView = webView
            webView.evaluateJavascript(
                "(function(){var m=window.__nexPlaybackLastMedia;if(!m)return;try{m.pause();m.currentTime=0;window.__nexPlaybackLastMedia=null;}catch(e){}})();",
                null
            )
            entries[webView]?.info = null
            activeWebView = null
            selectActiveWebView()
            playbackListener?.invoke(currentInfo())
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            val webView = activeWebView ?: return@post
            val seconds = (positionMs.coerceAtLeast(0L) / 1000.0).toString()
            webView.evaluateJavascript(
                "(function(){var m=window.__nexPlaybackLastMedia;if(!m)return;try{m.currentTime=$seconds;}catch(e){}})();",
                null
            )
        }
    }

    private fun receiveMediaState(
        webView: WebView,
        title: String,
        artist: String,
        playing: Boolean,
        hasMedia: Boolean,
        durationSeconds: Double,
        positionSeconds: Double,
        seekable: Boolean
    ) {
        if (!entries.containsKey(webView)) return
        if (suppressedPausedView === webView && !playing && hasMedia) return
        if (suppressedPausedView === webView && !playing && !hasMedia) suppressedPausedView = null
        if (playing) suppressedPausedView = null

        val info = if (hasMedia) {
            WebMediaInfo(
                title = title.take(180).ifBlank { "Web playback" },
                artist = artist.take(120).ifBlank { "Nex-PlayBack" },
                hasMedia = true,
                isPlaying = playing,
                durationMs = if (durationSeconds.isFinite() && durationSeconds >= 0.0) (durationSeconds * 1000.0).toLong() else -1L,
                positionMs = if (positionSeconds.isFinite() && positionSeconds >= 0.0) (positionSeconds * 1000.0).toLong() else 0L,
                isSeekable = seekable
            )
        } else {
            null
        }

        val entry = entries[webView] ?: return
        entry.info = info
        entry.touchedAt = ++lastTouched
        if (playing || (activeWebView === webView && hasMedia)) {
            activeWebView = webView
        } else if (activeWebView === webView && !hasMedia) {
            activeWebView = null
            selectActiveWebView()
        }
        playbackListener?.invoke(currentInfo())
    }

    private fun selectActiveWebView() {
        val candidate = entries.entries
            .filter { it.value.info?.hasMedia == true }
            .maxWithOrNull(compareBy<Map.Entry<WebView, Entry>> { if (it.value.info?.isPlaying == true) 1 else 0 }.thenBy { it.value.touchedAt })
        activeWebView = candidate?.key
    }

    private fun currentInfo(): WebMediaInfo? {
        val webView = activeWebView ?: return null
        return entries[webView]?.info
    }

    private class MediaBridge(private val webView: WebView) {
        @JavascriptInterface
        fun report(
            title: String,
            artist: String,
            playing: Boolean,
            hasMedia: Boolean,
            durationSeconds: Double,
            positionSeconds: Double,
            seekable: Boolean
        ) {
            WebPlaybackCoordinator.mainHandler.post {
                WebPlaybackCoordinator.receiveMediaState(
                    webView,
                    title,
                    artist,
                    playing,
                    hasMedia,
                    durationSeconds,
                    positionSeconds,
                    seekable
                )
            }
        }
    }

    private const val JS_BRIDGE_NAME = "NexPlaybackBridge"

    private val MEDIA_TRACKING_SCRIPT = """
        (function() {
          if (window.__nexPlaybackTrackingInstalled) return;
          window.__nexPlaybackTrackingInstalled = true;
          window.__nexPlaybackLastMedia = null;

          function report() {
            try {
              var media = window.__nexPlaybackLastMedia;
              if (media && !document.documentElement.contains(media)) {
                window.__nexPlaybackLastMedia = null;
                media = null;
              }
              if (!media) {
                NexPlaybackBridge.report("", "", false, false, -1, 0, false);
                return;
              }
              var metadata = navigator.mediaSession ? navigator.mediaSession.metadata : null;
              var title = (metadata && metadata.title) || media.getAttribute("aria-label") || document.title || "Web playback";
              var artist = (metadata && metadata.artist) || location.hostname || "Nex-PlayBack";
              var duration = Number.isFinite(media.duration) ? media.duration : -1;
              var position = Number.isFinite(media.currentTime) ? media.currentTime : 0;
              var seekable = false;
              try { seekable = media.seekable && media.seekable.length > 0 && duration >= 0; } catch (e) {}
              NexPlaybackBridge.report(
                String(title).slice(0, 180),
                String(artist).slice(0, 120),
                !media.paused && !media.ended,
                true,
                duration,
                position,
                !!seekable
              );
            } catch (e) {}
          }

          document.addEventListener("play", function(event) {
            if (event.target instanceof HTMLMediaElement) {
              window.__nexPlaybackLastMedia = event.target;
              report();
            }
          }, true);
          document.addEventListener("pause", report, true);
          document.addEventListener("ended", function(event) {
            if (event.target === window.__nexPlaybackLastMedia) {
              window.__nexPlaybackLastMedia = null;
            }
            report();
          }, true);
          document.addEventListener("loadedmetadata", report, true);
          document.addEventListener("durationchange", report, true);
          document.addEventListener("seeked", report, true);
          window.setInterval(report, 1800);
          report();
        })();
    """.trimIndent()
}
