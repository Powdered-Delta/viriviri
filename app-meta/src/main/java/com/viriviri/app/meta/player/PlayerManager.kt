package com.viriviri.app.meta.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.viriviri.core.state.SurfaceHandoffMetrics

class PlayerManager private constructor(context: Context) {
    val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
    }

    var metrics by mutableStateOf(SurfaceHandoffMetrics())
        private set

    private var currentSurface: Surface? = null
    private var hasLoadedMedia = false

    init {
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    metrics = metrics.copy(
                        videoDecoderInitializations = metrics.videoDecoderInitializations + 1,
                    )
                    Log.i(TAG, "Video decoder initialized: $decoderName")
                }
            },
        )
    }

    fun loadTestMedia() {
        if (hasLoadedMedia) return

        hasLoadedMedia = true
        player.setMediaItem(MediaItem.fromUri(TEST_VIDEO_URL))
        metrics = metrics.copy(prepareCalls = metrics.prepareCalls + 1)
        player.prepare()
    }

    fun attachSurface(surface: Surface) {
        if (!surface.isValid || currentSurface === surface) return

        val startedAtMs = SystemClock.elapsedRealtime()
        currentSurface?.let(player::clearVideoSurface)
        player.setVideoSurface(surface)
        currentSurface = surface
        metrics = metrics.copy(
            surfaceHandoffs = metrics.surfaceHandoffs + 1,
            playbackPositionMs = player.currentPosition,
            lastHandoffDurationMs = SystemClock.elapsedRealtime() - startedAtMs,
        )
        Log.i(TAG, "Attached video Surface; position=${player.currentPosition}ms")
    }

    fun detachSurface(surface: Surface) {
        if (currentSurface !== surface) return

        player.clearVideoSurface(surface)
        currentSurface = null
        metrics = metrics.copy(playbackPositionMs = player.currentPosition)
        Log.i(TAG, "Detached current video Surface; position=${player.currentPosition}ms")
    }

    fun refreshPlaybackPosition() {
        metrics = metrics.copy(playbackPositionMs = player.currentPosition)
    }

    fun release() {
        currentSurface?.let(player::clearVideoSurface)
        currentSurface = null
        player.release()
        instance = null
    }

    companion object {
        private const val TAG = "ViriviriPlayerPoC"
        private const val TEST_VIDEO_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager = instance ?: synchronized(this) {
            instance ?: PlayerManager(context.applicationContext).also { instance = it }
        }
    }
}
