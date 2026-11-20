package com.nexplayback.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBrowserService : Service() {
    private lateinit var windowManager: WindowManager
    private val browserWindows = linkedMapOf<Int, BrowserWindow>()
    private var nextWindowId = 1
    private var launcherView: TextView? = null
    private var launcherParams: WindowManager.LayoutParams? = null
    private var menuView: LinearLayout? = null

    private inner class BrowserWindow(
        val id: Int,
        val root: LinearLayout,
        val params: WindowManager.LayoutParams,
        val content: LinearLayout,
        val addressRow: LinearLayout,
        val webView: WebView,
        val addressInput: EditText,
        val resizeBar: TextView
    ) {
        var minimized = false
        var maximized = false
        var attached = true
        var previousWidth = params.width
        var previousHeight = params.height
        var previousX = params.x
        var previousY = params.y
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureLauncher()
        if (intent?.action == ACTION_SHOW_MENU) {
            showLauncherMenu()
        } else {
            val url = intent?.getStringExtra(EXTRA_URL) ?: "https://www.google.com"
            createBrowserWindow(url)
        }
        return START_NOT_STICKY
    }

    private fun ensureLauncher() {
        if (launcherView != null) return
        val bubble = TextView(this).apply {
            text = "N"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(27, 151, 119))
                setStroke(dp(1), Color.rgb(100, 230, 190))
            }
            elevation = dp(8).toFloat()
            contentDescription = "Nex-PlayBack floating menu"
        }
        val params = WindowManager.LayoutParams(
            dp(52), dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    params.x = (startX + dx).coerceIn(0, screenWidth() - dp(52))
                    params.y = (startY + dy).coerceIn(0, screenHeight() - dp(52))
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showLauncherMenu()
                    true
                }
                else -> true
            }
        }
        try {
            windowManager.addView(bubble, params)
            launcherView = bubble
            launcherParams = params
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        }
    }

    private fun showLauncherMenu() {
        removeMenu()
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded("#101923", 14)
            elevation = dp(12).toFloat()
        }
        menu.addView(TextView(this).apply {
            text = "NEX-PLAYBACK • FLOAT"
            textSize = 12f
            setTextColor(Color.rgb(100, 230, 190))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(4), dp(6), dp(8))
        })
        menu.addView(makeMenuButton("+ Browser baru") {
            removeMenu()
            createBrowserWindow("https://www.google.com")
        })
        browserWindows.values.forEach { browser ->
            val label = if (browser.attached) {
                if (browser.minimized) "Pulihkan Browser #${browser.id}" else "Browser #${browser.id}"
            } else {
                "Buka kembali Browser #${browser.id}"
            }
            menu.addView(makeMenuButton(label) {
                removeMenu()
                restoreBrowserWindow(browser.id)
            })
        }
        menu.addView(makeMenuButton("Tutup semua jendela") {
            removeMenu()
            closeAllBrowserWindows()
        })
        menu.addView(makeMenuButton("Tutup menu") { removeMenu() })

        val params = WindowManager.LayoutParams(
            dp(220), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bubbleParams = launcherParams
            x = (bubbleParams?.x ?: dp(12)).coerceAtMost(screenWidth() - dp(230))
            y = ((bubbleParams?.y ?: dp(180)) + dp(60)).coerceAtMost(screenHeight() - dp(260))
        }
        try {
            windowManager.addView(menu, params)
            menuView = menu
        } catch (_: SecurityException) {
            menuView = null
        } catch (_: WindowManager.BadTokenException) {
            menuView = null
        }
    }

    private fun createBrowserWindow(initialUrl: String) {
        val id = nextWindowId++
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#101923", 14)
            clipToOutline = true
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(4), 0)
            setBackgroundColor(Color.rgb(24, 38, 51))
        }
        val title = TextView(this).apply {
            text = "Nex Browser #$id"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(makeButton("—") { minimizeBrowserWindow(id) })
        header.addView(makeButton("□") { toggleMaximize(id) })
        header.addView(makeButton("×") { hideBrowserWindow(id) })
        root.addView(header)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val addressInput = EditText(this).apply {
            hint = "Cari atau masukkan alamat"
            textSize = 13f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded("#203040", 8)
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(40), 1f))
        addressRow.addView(makeButton("GO") { loadAddress(id) })
        root.addView(addressRow)

        val web = WebView(this).apply {
            setBackgroundColor(Color.rgb(17, 25, 35))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.safeBrowsingEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageFinished(view: WebView, url: String) {
                    addressInput.setText(url)
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                }
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(addressRow)
        content.addView(web, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val resizeBar = TextView(this).apply {
            text = "━━━━  ↘"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(160, 180, 195))
            setBackgroundColor(Color.rgb(24, 38, 51))
        }
        root.addView(resizeBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(26)
        ))

        val offset = browserWindows.size * dp(18)
        val params = WindowManager.LayoutParams(
            minOf(dp(340), screenWidth() - dp(20)),
            minOf(dp(470), screenHeight() - dp(100)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dp(12) + offset).coerceAtMost(screenWidth() - width)
            y = (dp(90) + offset).coerceAtMost(screenHeight() - height)
        }
        val browser = BrowserWindow(id, root, params, content, addressRow, web, addressInput, resizeBar)
        browserWindows[id] = browser

        attachDragHandler(title, browser)
        attachResizeHandler(browser)
        try {
            windowManager.addView(root, params)
            WebViewPowerManager.resume(web)
            web.loadUrl(initialUrl)
        } catch (_: SecurityException) {
            browserWindows.remove(id)
            WebViewPowerManager.destroy(web)
        } catch (_: WindowManager.BadTokenException) {
            browserWindows.remove(id)
            WebViewPowerManager.destroy(web)
        }
    }

    private fun attachDragHandler(view: View, browser: BrowserWindow) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = browser.params.x
                    startY = browser.params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!browser.maximized) {
                        browser.params.x = (startX + (event.rawX - downX).toInt())
                            .coerceIn(0, screenWidth() - browser.params.width)
                        browser.params.y = (startY + (event.rawY - downY).toInt())
                            .coerceIn(0, screenHeight() - dp(44))
                        updateWindow(browser)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
    }

    private fun attachResizeHandler(browser: BrowserWindow) {
        var downX = 0f
        var downY = 0f
        var startWidth = 0
        var startHeight = 0
        browser.resizeBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = browser.params.width
                    startHeight = browser.params.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!browser.maximized && !browser.minimized) {
                        val maxWidth = (screenWidth() - browser.params.x).coerceAtLeast(dp(250))
                        val maxHeight = (screenHeight() - browser.params.y).coerceAtLeast(dp(220))
                        browser.params.width = (startWidth + (event.rawX - downX).toInt())
                            .coerceIn(dp(250), maxWidth)
                        browser.params.height = (startHeight + (event.rawY - downY).toInt())
                            .coerceIn(dp(220), maxHeight)
                        updateWindow(browser)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
    }

    private fun loadAddress(id: Int) {
        val browser = browserWindows[id] ?: return
        val value = browser.addressInput.text.toString().trim()
        if (value.isEmpty()) return
        val url = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains(" ") || !value.contains(".") ->
                "https://www.google.com/search?q=" + Uri.encode(value)
            else -> "https://$value"
        }
        browser.webView.loadUrl(url)
    }

    private fun minimizeBrowserWindow(id: Int) {
        val browser = browserWindows[id] ?: return
        if (browser.minimized) return
        browser.previousHeight = browser.params.height
        browser.minimized = true
        browser.content.visibility = View.GONE
        browser.resizeBar.visibility = View.GONE
        browser.params.height = dp(44)
        updateWindow(browser)
    }

    private fun toggleMaximize(id: Int) {
        val browser = browserWindows[id] ?: return
        if (browser.minimized) {
            restoreBrowserWindow(id)
            return
        }
        if (browser.maximized) {
            browser.params.width = browser.previousWidth
            browser.params.height = browser.previousHeight
            browser.params.x = browser.previousX
            browser.params.y = browser.previousY
            browser.maximized = false
        } else {
            browser.previousWidth = browser.params.width
            browser.previousHeight = browser.params.height
            browser.previousX = browser.params.x
            browser.previousY = browser.params.y
            browser.params.width = screenWidth()
            browser.params.height = screenHeight()
            browser.params.x = 0
            browser.params.y = 0
            browser.maximized = true
        }
        updateWindow(browser)
    }

    private fun restoreBrowserWindow(id: Int) {
        val browser = browserWindows[id] ?: return
        if (!browser.attached) {
            try {
                windowManager.addView(browser.root, browser.params)
                browser.attached = true
            } catch (_: Exception) {
                return
            }
        }
        if (browser.minimized) {
            browser.minimized = false
            browser.content.visibility = View.VISIBLE
            browser.resizeBar.visibility = View.VISIBLE
            browser.params.height = browser.previousHeight.coerceAtLeast(dp(220))
        }
        updateWindow(browser)
    }

    private fun hideBrowserWindow(id: Int) {
        val browser = browserWindows[id] ?: return
        if (!browser.attached) return
        try {
            windowManager.removeView(browser.root)
            browser.attached = false
        } catch (_: IllegalArgumentException) {
            browser.attached = false
        }
    }

    private fun closeAllBrowserWindows() {
        browserWindows.values.toList().forEach { browser ->
            browserWindows.remove(browser.id)
            try {
                if (browser.attached) windowManager.removeView(browser.root)
            } catch (_: IllegalArgumentException) {
                // The window was already detached.
            }
            WebViewPowerManager.destroy(browser.webView)
        }
    }

    private fun updateWindow(browser: BrowserWindow) {
        if (browser.attached) {
            try {
                windowManager.updateViewLayout(browser.root, browser.params)
            } catch (_: IllegalArgumentException) {
                browser.attached = false
            }
        }
    }

    private fun removeMenu() {
        menuView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // The menu was already detached.
            }
        }
        menuView = null
    }

    override fun onDestroy() {
        removeMenu()
        browserWindows.values.toList().forEach { browser ->
            try {
                if (browser.attached) windowManager.removeView(browser.root)
            } catch (_: IllegalArgumentException) {
                // The window was already detached.
            }
            WebViewPowerManager.destroy(browser.webView)
        }
        browserWindows.clear()
        launcherView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // The launcher was already detached.
            }
        }
        launcherView = null
        launcherParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 57, 73))
            minHeight = dp(36)
            minimumHeight = dp(36)
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { action() }
        }
    }

    private fun makeMenuButton(label: String, action: () -> Unit): Button {
        return makeButton(label, action).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    private fun rounded(hex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = resources.displayMetrics.heightPixels
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "com.nexplayback.app.extra.URL"
        const val ACTION_SHOW_MENU = "com.nexplayback.app.action.SHOW_FLOAT_MENU"
    }
}
