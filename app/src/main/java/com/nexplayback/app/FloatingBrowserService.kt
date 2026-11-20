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
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBrowserService : Service() {
    private var windowManager: WindowManager? = null
    private var windowView: LinearLayout? = null
    private var webView: WebView? = null
    private var addressInput: EditText? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var minimized = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL) ?: "https://www.google.com"
        if (windowView != null) {
            webView?.loadUrl(url)
            return START_NOT_STICKY
        }
        showWindow(url)
        return START_NOT_STICKY
    }

    private fun showWindow(initialUrl: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#101923", 16)
            clipToOutline = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            setBackgroundColor(Color.rgb(24, 38, 51))
        }
        val dragLabel = TextView(this).apply {
            text = "Nex-PlayBack  •  FLOAT"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(dragLabel, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(makeButton("—") { toggleMinimize() })
        header.addView(makeButton("×") { stopSelf() })
        panel.addView(header)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        addressInput = EditText(this).apply {
            hint = "Alamat website"
            textSize = 13f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded("#203040", 8)
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(40), 1f))
        addressRow.addView(makeButton("GO") { loadAddress() })
        panel.addView(addressRow)

        webView = WebView(this).apply {
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
                    addressInput?.setText(url)
                }
            }
        }
        panel.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        header.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = windowParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - downX).toInt()
                        params.y = startY + (event.rawY - downY).toInt()
                        windowView?.let { windowManager?.updateViewLayout(it, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })

        val params = WindowManager.LayoutParams(
            dp(350),
            dp(500),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(90)
        }

        windowParams = params
        windowView = panel
        try {
            windowManager?.addView(panel, params)
            webView?.loadUrl(initialUrl)
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
        }
    }

    private fun loadAddress() {
        val value = addressInput?.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return
        val url = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains(" ") || !value.contains(".") -> "https://www.google.com/search?q=" + android.net.Uri.encode(value)
            else -> "https://$value"
        }
        webView?.loadUrl(url)
    }

    private fun toggleMinimize() {
        val panel = windowView ?: return
        val web = webView ?: return
        val params = windowParams ?: return
        val addressRow = panel.getChildAt(1)
        minimized = !minimized
        addressRow.visibility = if (minimized) View.GONE else View.VISIBLE
        web.visibility = if (minimized) View.GONE else View.VISIBLE
        params.height = if (minimized) dp(48) else dp(500)
        windowManager?.updateViewLayout(panel, params)
    }

    override fun onDestroy() {
        windowView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The overlay was already detached by the system.
            }
        }
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        windowView = null
        windowParams = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(37, 57, 73))
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener { action() }
        }
    }

    private fun rounded(hex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "com.nexplayback.app.extra.URL"
    }
}
