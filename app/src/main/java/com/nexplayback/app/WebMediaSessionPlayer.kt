package com.nexplayback.app

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class WebMediaSessionPlayer(
    looper: Looper,
    private val onPlayWhenReadyChange: (Boolean) -> Unit,
    private val onStopPlayback: () -> Unit,
    private val onSeekTo: (Long) -> Unit
) : SimpleBasePlayer(looper) {
    private var mediaItem: MediaItem? = null
    private var isPlaying = false
    private var isSeekable = false
    private var durationMs = C.TIME_UNSET
    private var positionMs = 0L

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_TIMELINE)
            .apply {
                if (isSeekable) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            }
            .build()
        val item = mediaItem
        val playlist = if (item == null) {
            emptyList()
        } else {
            listOf(
                SimpleBasePlayer.MediaItemData.Builder(item.mediaId)
                    .setMediaItem(item)
                    .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1_000L)
                    .setIsSeekable(isSeekable)
                    .build()
            )
        }
        val state = State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(if (item == null) Player.STATE_IDLE else Player.STATE_READY)
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(playlist)
        if (item != null) {
            state.setCurrentMediaItemIndex(0)
                .setContentPositionMs(positionMs)
                .setContentBufferedPositionMs(positionMs)
        }
        return state.build()
    }

    fun update(
        title: String,
        artist: String,
        playing: Boolean,
        duration: Long,
        position: Long,
        seekable: Boolean
    ) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "Web playback" })
            .setArtist(artist.ifBlank { "Nex-PlayBack" })
            .build()
        mediaItem = MediaItem.Builder()
            .setMediaId("web-playback")
            .setMediaMetadata(metadata)
            .build()
        isPlaying = playing
        durationMs = duration.takeIf { it >= 0L } ?: C.TIME_UNSET
        positionMs = position.coerceAtLeast(0L)
        isSeekable = seekable && durationMs != C.TIME_UNSET
        invalidateState()
    }

    fun clear() {
        mediaItem = null
        isPlaying = false
        isSeekable = false
        durationMs = C.TIME_UNSET
        positionMs = 0L
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isPlaying = playWhenReady
        onPlayWhenReadyChange(playWhenReady)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onStopPlayback()
        clear()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        val target = positionMs.coerceAtLeast(0L)
        this.positionMs = target
        onSeekTo(target)
        invalidateState()
        return Futures.immediateVoidFuture()
    }
}
