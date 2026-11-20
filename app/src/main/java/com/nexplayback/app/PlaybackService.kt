package com.nexplayback.app

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var webPlayer: WebMediaSessionPlayer
    private var mediaSession: MediaSession? = null
    private var usingWebPlayer = false

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this).build()
        webPlayer = WebMediaSessionPlayer(
            Looper.getMainLooper(),
            onPlayWhenReadyChange = { WebPlaybackCoordinator.setPlayback(it) },
            onStopPlayback = { WebPlaybackCoordinator.stopPlayback() },
            onSeekTo = { WebPlaybackCoordinator.seekTo(it) }
        )
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        WebPlaybackCoordinator.setPlaybackListener(::onWebMediaChanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY_MEDIA) {
            val uri: Uri? = intent.data
            if (uri != null) {
                mediaSession?.setPlayer(exoPlayer)
                usingWebPlayer = false
                WebPlaybackCoordinator.stopPlayback()
                val title = intent.getStringExtra(EXTRA_TITLE)?.ifBlank { null }
                    ?: uri.lastPathSegment
                    ?: "Nex-PlayBack audio"
                val item = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist("Nex-PlayBack")
                            .build()
                    )
                    .build()
                exoPlayer.setMediaItem(item)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun onWebMediaChanged(info: WebMediaInfo?) {
        if (info == null || !info.hasMedia) {
            if (usingWebPlayer) webPlayer.clear()
            return
        }
        if (!usingWebPlayer) {
            exoPlayer.pause()
            mediaSession?.setPlayer(webPlayer)
            usingWebPlayer = true
        }
        webPlayer.update(
            title = info.title,
            artist = info.artist,
            playing = info.isPlaying,
            duration = info.durationMs,
            position = info.positionMs,
            seekable = info.isSeekable
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        WebPlaybackCoordinator.setPlaybackListener(null)
        mediaSession?.release()
        mediaSession = null
        if (::webPlayer.isInitialized) webPlayer.release()
        if (::exoPlayer.isInitialized) exoPlayer.release()
        usingWebPlayer = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_MEDIA = "com.nexplayback.app.action.PLAY_MEDIA"
        const val EXTRA_TITLE = "com.nexplayback.app.extra.MEDIA_TITLE"
    }
}
