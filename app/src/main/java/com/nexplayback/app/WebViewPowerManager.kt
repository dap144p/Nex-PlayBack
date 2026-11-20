package com.nexplayback.app

import android.view.View
import android.webkit.WebView
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Coordinates WebView lifecycle across the main browser and floating browser.
 * WebView.pauseTimers()/resumeTimers() affect every WebView in this app process,
 * so global timers are paused only while every managed browser window is inactive.
 */
object WebViewPowerManager {
    private val activeWebViews = Collections.newSetFromMap(
        IdentityHashMap<WebView, Boolean>()
    )
    private var timersPaused = false

    @Synchronized
    fun resume(webView: WebView) {
        val resumeGlobalTimers = activeWebViews.isEmpty() && timersPaused
        activeWebViews.add(webView)
        webView.visibility = View.VISIBLE
        webView.onResume()
        if (resumeGlobalTimers) {
            webView.resumeTimers()
            timersPaused = false
        }
    }

    @Synchronized
    fun pause(webView: WebView) {
        if (activeWebViews.remove(webView)) {
            webView.onPause()
        }
        webView.visibility = View.GONE
        if (activeWebViews.isEmpty() && !timersPaused) {
            webView.pauseTimers()
            timersPaused = true
        }
    }

    @Synchronized
    fun destroy(webView: WebView) {
        pause(webView)
        webView.stopLoading()
        webView.destroy()
    }
}
