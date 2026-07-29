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
import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.SourceFinishDisposition
import com.viriviri.core.state.SurfaceHandoffMetrics
import com.viriviri.core.state.TransitionPlaybackPolicy

class PlayerManager private constructor(context: Context) {
    val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
    }

    var metrics by mutableStateOf(SurfaceHandoffMetrics())
        private set

    private val ownershipState = PlaybackOwnershipState<Surface>()
    private var released = false

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
        ownershipState.loadOnce {
            player.setMediaItem(MediaItem.fromUri(TEST_VIDEO_URI))
            metrics = metrics.copy(prepareCalls = metrics.prepareCalls + 1)
            player.prepare()
            Log.i(TAG, "Prepared bundled media; player=${identity(player)} manager=${identity(this)}")
        }
    }

    /** Returns true only when [surface] is confirmed as the current player output. */
    fun attachSurface(surface: Surface, target: HandoffTarget, transitionId: Long?): Boolean {
        if (!surface.isValid) {
            Log.w(
                TAG,
                "Surface attach rejected transition=$transitionId target=${target.name} " +
                    "surface=${identity(surface)} reason=invalid",
            )
            return false
        }

        val startedAtMs = SystemClock.elapsedRealtime()
        val alreadyCurrent = ownershipState.isCurrentOutput(surface)
        val attached = ownershipState.ensureOutput(surface) { previous, next ->
            previous?.let(player::clearVideoSurface)
            player.setVideoSurface(next)
        }
        if (!attached) {
            Log.w(
                TAG,
                "Surface attach rejected transition=$transitionId target=${target.name} " +
                    "surface=${identity(surface)} reason=not_current",
            )
            return false
        }

        if (!alreadyCurrent) {
            metrics = metrics.copy(
                surfaceHandoffs = metrics.surfaceHandoffs + 1,
                playbackPositionMs = player.currentPosition,
                currentTarget = target,
            )
        }
        val attachDurationMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.i(
            TAG,
            "Surface attachment verified transition=$transitionId target=${target.name} " +
                "replacement=${!alreadyCurrent} surface=${identity(surface)} " +
                "player=${identity(player)} manager=${identity(this)} position=${player.currentPosition}ms " +
                "attachDuration=${attachDurationMs}ms prepare=${metrics.prepareCalls} " +
                "decoder=${metrics.videoDecoderInitializations}",
        )
        return true
    }

    fun detachSurface(surface: Surface, target: HandoffTarget, transitionId: Long?) {
        if (!ownershipState.removeOutput(surface, player::clearVideoSurface)) {
            Log.i(
                TAG,
                "Surface detach ignored transition=$transitionId target=${target.name} " +
                    "surface=${identity(surface)} reason=not_current",
            )
            return
        }
        metrics = metrics.copy(playbackPositionMs = player.currentPosition)
        Log.i(
            TAG,
            "Surface detached transition=$transitionId target=${target.name} surface=${identity(surface)} " +
                "position=${player.currentPosition}ms",
        )
    }

    fun isCurrentSurface(surface: Surface): Boolean = ownershipState.isCurrentOutput(surface)

    fun refreshPlaybackPosition() {
        metrics = metrics.copy(playbackPositionMs = player.currentPosition)
    }

    @Synchronized
    fun release() {
        if (released) return

        released = true
        player.release()
        clearInstance(this)
        Log.i(TAG, "Released player manager=${identity(this)}")
    }

    fun recordRouteRequested(
        transitionId: Long,
        source: HandoffTarget,
        destination: HandoffTarget,
        policy: TransitionPlaybackPolicy,
    ) {
        metrics = metrics.copy(
            activePolicy = policy,
            transitionId = transitionId,
            sourceTarget = source,
            destinationTarget = destination,
            destinationSurfaceReady = false,
            destinationFirstFrameReady = false,
            destinationSurfaceAttachedAfterMs = null,
            destinationFirstFrameAfterMs = null,
            playingWithoutVisibleDestinationMs = null,
            sourceFinishedAfterMs = null,
            sourceFinishDisposition = SourceFinishDisposition.NOT_REQUESTED,
            transitionTimedOut = false,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordDestinationSurfaceAttached(transitionId: Long, elapsedMs: Long) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            destinationSurfaceReady = true,
            destinationSurfaceAttachedAfterMs = elapsedMs,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordDestinationFirstFrame(transitionId: Long, elapsedMs: Long) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            destinationFirstFrameReady = true,
            destinationFirstFrameAfterMs = elapsedMs,
            playingWithoutVisibleDestinationMs = elapsedMs,
            lastHandoffDurationMs = elapsedMs,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordSourceFinished(
        transitionId: Long,
        elapsedMs: Long,
        disposition: SourceFinishDisposition,
    ) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            sourceFinishedAfterMs = elapsedMs,
            sourceFinishDisposition = disposition,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordTransitionTimedOut(transitionId: Long, elapsedMs: Long) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            transitionTimedOut = true,
            playingWithoutVisibleDestinationMs = elapsedMs,
            playbackPositionMs = player.currentPosition,
        )
    }

    companion object {
        private const val TAG = "ViriviriPlayerPoC"
        private const val TEST_VIDEO_URI = "asset:///poc/rick.mp4"

        private fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))

        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager = instance ?: synchronized(this) {
            instance ?: PlayerManager(context.applicationContext).also { instance = it }
        }

        private fun clearInstance(manager: PlayerManager) = synchronized(this) {
            if (instance === manager) instance = null
        }
    }
}
