package com.nexplayback.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var addressInput: EditText
    private lateinit var audioInput: EditText
    private lateinit var playPauseButton: Button
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 17, 24)
        window.navigationBarColor = Color.rgb(11, 17, 24)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildInterface()
        connectPlaybackService()
        requestNotificationPermission()
        if (savedInstanceState == null) browser.loadUrl("https://www.google.com")
        else browser.restoreState(savedInstanceState)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 17, 24))
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Nex-PlayBack"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(makeButton("FLOAT") { openFloatingBrowser() })
        root.addView(header)

        addressInput = makeField("Cari atau masukkan alamat website")
        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addressRow.addView(addressInput, LinearLayout.LayoutParams(0, dp(44), 1f))
        addressRow.addView(makeButton("BUKA") { openAddress() })
        root.addView(addressRow)

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        shortcuts.addView(makeButton("Google") { browser.loadUrl("https://www.google.com") })
        shortcuts.addView(makeButton("Yahoo") { browser.loadUrl("https://www.yahoo.com") })
        shortcuts.addView(makeButton("YouTube") { browser.loadUrl("https://www.youtube.com") })
        root.addView(shortcuts)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        nav.addView(makeButton("‹") { if (browser.canGoBack()) browser.goBack() })
        nav.addView(makeButton("›") { if (browser.canGoForward()) browser.goForward() })
        nav.addView(makeButton("↻") { browser.reload() })
        nav.addView(makeButton("HOME") { browser.loadUrl("https://www.google.com") })
        root.addView(nav)

        browser = WebView(this).apply {
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
                    if (addressInput.text.toString() != url) {
                        addressInput.setText(url)
                        addressInput.setSelection(addressInput.text.length)
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                }
            }
        }
        root.addView(
            browser,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        )

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#14202C", 14)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        playerPanel.addView(TextView(this).apply {
            text = "AUDIO PLAYER  •  BACKGROUND"
            textSize = 11f
            setTextColor(Color.rgb(67, 217, 163))
        })
        audioInput = makeField("URL audio HTTPS langsung")
        playerPanel.addView(audioInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
            topMargin = dp(6)
        })
        val playerButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        playerButtons.addView(makeButton("PUTAR URL") { playAudioUrl() })
        playerButtons.addView(makeButton("FILE LOKAL") { chooseAudioFile() })
        playPauseButton = makeButton("PAUSE") { togglePlayback() }
        playerButtons.addView(playPauseButton)
        playerPanel.addView(playerButtons)
        root.addView(playerPanel)
        setContentView(root)
    }

    private fun connectPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get().also { mediaController ->
                    mediaController.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playPauseButton.text = if (isPlaying) "PAUSE" else "PLAY"
                        }
                    })
                    playPauseButton.text = if (mediaController.isPlaying) "PAUSE" else "PLAY"
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Player gagal disiapkan.", Toast.LENGTH_SHORT).show()
            }
        }, mainExecutor)
    }

    private fun playAudioUrl() {
        val raw = audioInput.text.toString().trim()
        if (raw.isEmpty()) {
            audioInput.error = "Masukkan URL audio HTTPS"
            return
        }
        val uri = Uri.parse(raw)
        if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank()) {
            audioInput.error = "Gunakan URL HTTPS langsung ke file atau stream audio."
            return
        }
        startMedia(uri, uri.lastPathSegment ?: "Nex-PlayBack stream")
    }

    private fun startMedia(uri: Uri, title: String) {
        val mediaController = controller
        if (mediaController == null) {
            Toast.makeText(this, "Player sedang disiapkan. Coba lagi sebentar.", Toast.LENGTH_SHORT).show()
            return
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Nex-PlayBack").build())
            .build()
        mediaController.setMediaItem(item)
        mediaController.prepare()
        mediaController.play()
        playPauseButton.text = "PAUSE"
    }

    private fun chooseAudioFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_AUDIO)
    }

    @Deprecated("Uses the platform activity result API for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUDIO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some document providers do not offer persistable grants.
            }
            startMedia(uri, uri.lastPathSegment ?: "Audio lokal")
        } else if (requestCode == REQUEST_OVERLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) openFloatingBrowser()
        }
    }

    private fun togglePlayback() {
        val mediaController = controller ?: run {
            Toast.makeText(this, "Player belum siap.", Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaController.isPlaying) mediaController.pause()
        else if (mediaController.mediaItemCount > 0) mediaController.play()
    }

    private fun openAddress() {
        val text = addressInput.text.toString().trim()
        if (text.isEmpty()) return
        val destination = when {
            text.startsWith("https://", true) || text.startsWith("http://", true) -> text
            text.contains(" ") || !text.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(text)
            else -> "https://$text"
        }
        browser.loadUrl(destination)
    }

    private fun openFloatingBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_OVERLAY)
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain untuk membuka halaman mengambang.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingBrowserService::class.java)
            .putExtra(FloatingBrowserService.EXTRA_URL, browser.url ?: "https://www.google.com")
        startService(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    @Deprecated("Uses the platform activity result API for broad Android compatibility.")
    override fun onBackPressed() {
        if (::browser.isInitialized && browser.canGoBack()) browser.goBack()
        else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (::browser.isInitialized) WebViewPowerManager.resume(browser)
    }

    override fun onStop() {
        if (::browser.isInitialized) WebViewPowerManager.pause(browser)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        controllerFuture = null
        if (::browser.isInitialized) WebViewPowerManager.destroy(browser)
        super.onDestroy()
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(31, 49, 64))
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
        }
    }

    private fun makeField(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(132, 150, 168))
            isSingleLine = true
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded("#182633", 10)
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
        private const val REQUEST_AUDIO = 1001
        private const val REQUEST_OVERLAY = 1002
        private const val REQUEST_NOTIFICATIONS = 1003
    }
}
