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
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.HandoffRouteState
import com.viriviri.core.state.HandoffExperimentMode
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
    var experimentMode: HandoffExperimentMode = HandoffExperimentMode.DIRECT_RECOVERY
        private set

    fun setExperimentMode(mode: HandoffExperimentMode) {
        experimentMode = mode
        metrics = metrics.copy(experimentMode = mode)
        Log.i(TAG, "handoff_experiment_mode=${mode.name}")
    }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    logPlayerEvent("playback_state=${playbackStateName(playbackState)}")
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    logPlayerEvent("play_when_ready=$playWhenReady reason=$reason")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    logPlayerEvent("is_playing=$isPlaying")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "player_error code=${error.errorCodeName} ${playerContext()}", error)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    logPlayerEvent(
                        "video_size=${videoSize.width}x${videoSize.height} " +
                            "ratio=${videoSize.pixelWidthHeightRatio}",
                    )
                }

                override fun onRenderedFirstFrame() {
                    logPlayerEvent("rendered_first_frame")
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    logPlayerEvent("audio_session_changed=$audioSessionId")
                }
            },
        )
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

    fun loadTestMedia(playWhenReady: Boolean = true) {
        ownershipState.loadOnce {
            player.playWhenReady = playWhenReady
            player.setMediaItem(MediaItem.fromUri(TEST_VIDEO_URI))
            metrics = metrics.copy(prepareCalls = metrics.prepareCalls + 1)
            player.prepare()
            Log.i(TAG, "Prepared bundled media; player=${identity(player)} manager=${identity(this)}")
        }
    }

    fun capturePlaybackSnapshot(): PlaybackSnapshot =
        PlaybackSnapshot(
            mediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: TEST_VIDEO_URI,
            positionMs = player.currentPosition,
            playWhenReady = player.playWhenReady,
            wasPlaying = player.isPlaying,
            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
        ).also { snapshot ->
            Log.i(
                TAG,
                "playback_snapshot_captured media=${snapshot.mediaUri} position=${snapshot.positionMs}ms " +
                    "playWhenReady=${snapshot.playWhenReady} ${playerContext()}",
            )
        }

    fun restorePlaybackSnapshot(snapshot: PlaybackSnapshot, transitionId: Long?, host: HandoffTarget) {
        val currentMediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentMediaUri != snapshot.mediaUri) {
            player.setMediaItem(MediaItem.fromUri(snapshot.mediaUri), snapshot.positionMs)
            metrics = metrics.copy(prepareCalls = metrics.prepareCalls + 1)
            player.prepare()
        } else if (player.currentPosition != snapshot.positionMs) {
            player.seekTo(snapshot.positionMs)
        }
        player.playWhenReady = false
        metrics = metrics.copy(playbackPositionMs = player.currentPosition)
        Log.i(
            TAG,
            "playback_snapshot_restored transition=$transitionId host=${host.name} " +
                "media=$currentMediaUri position=${player.currentPosition}ms " +
                "playWhenReady=${player.playWhenReady} resumeAfterFirstFrame=${snapshot.playWhenReady} " +
                "capturedAt=${snapshot.capturedAtElapsedMs} player=${identity(player)} manager=${identity(this)}",
        )
    }

    fun pauseForHandoff(transitionId: Long?) {
        player.playWhenReady = false
        player.pause()
        Log.i(
            TAG,
            "route_pause_requested transition=$transitionId position=${player.currentPosition}ms " +
                "player=${identity(player)} manager=${identity(this)}",
        )
    }

    fun resumeAfterFirstFrame(snapshot: PlaybackSnapshot, transitionId: Long?, host: HandoffTarget) {
        if (snapshot.positionMs != player.currentPosition) player.seekTo(snapshot.positionMs)
        player.playWhenReady = snapshot.playWhenReady
        if (snapshot.playWhenReady) player.play()
        Log.i(
            TAG,
            "route_playback_resumed transition=$transitionId host=${host.name} " +
                "position=${player.currentPosition}ms playWhenReady=${player.playWhenReady} " +
                "wasPlaying=${snapshot.wasPlaying} player=${identity(player)} manager=${identity(this)}",
        )
    }

    fun resumeAfterRouteFailure(snapshot: PlaybackSnapshot, transitionId: Long?) {
        if (snapshot.positionMs != player.currentPosition) player.seekTo(snapshot.positionMs)
        player.playWhenReady = snapshot.playWhenReady
        if (snapshot.playWhenReady) player.play()
        Log.i(
            TAG,
            "route_playback_restored_after_failure transition=$transitionId position=${player.currentPosition}ms " +
                "playWhenReady=${player.playWhenReady} player=${identity(player)} manager=${identity(this)}",
        )
    }

    /** Returns true only when [surface] is confirmed as the current player output. */
    fun attachSurface(
        surface: Surface,
        target: HandoffTarget,
        transitionId: Long?,
    ): Boolean {
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
        val protectedDestinationReplacement = false
        val attached = ownershipState.ensureOutput(surface, target) { previous, next ->
            val replacementMode = surfaceReplacementMode(
                currentTarget = previous?.target,
                destinationTarget = target,
                protectedHandoff = protectedDestinationReplacement,
                experimentMode = metrics.experimentMode,
            )
            Log.i(
                TAG,
                "Surface replacement mode=${replacementMode.logName} transition=$transitionId " +
                    "oldTarget=${previous?.target?.name} oldSurface=${previous?.value?.let(::identity)} " +
                    "newTarget=${next.target?.name} newSurface=${identity(next.value)}",
            )
            if (replacementMode == SurfaceReplacementMode.CLEAR_THEN_SET) {
                try {
                    previous?.value?.let(player::clearVideoSurface)
                } catch (error: RuntimeException) {
                    Log.e(
                        TAG,
                        "old_surface_clear_failed mode=${metrics.experimentMode.name} " +
                            "transition=$transitionId oldSurface=${previous?.value?.let(::identity)}",
                        error,
                    )
                }
            }
            player.setVideoSurface(next.value)
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
            if (shouldRequestPlaybackAfterAttach(replacedOutput = !alreadyCurrent)) {
                player.play()
                Log.i(
                    TAG,
                    "Surface replacement requested playback transition=$transitionId target=${target.name} " +
                        "position=${player.currentPosition}ms ${playerContext()}",
                )
            }
        }
        val attachDurationMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.i(
            TAG,
            "Surface attachment verified transition=$transitionId target=${target.name} " +
                "replacement=${!alreadyCurrent} replacementMode=${
                    surfaceReplacementMode(
                        currentTarget = HandoffTarget.IMMERSIVE,
                        destinationTarget = target,
                        protectedHandoff = protectedDestinationReplacement,
                        experimentMode = metrics.experimentMode,
                    ).logName
                } " +
                "surface=${identity(surface)} " +
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
        ownershipState.clearOutput()
        Log.i(
            TAG,
            "player_release_begin player=${identity(player)} manager=${identity(this)} " +
                "prepare=${metrics.prepareCalls} decoder=${metrics.videoDecoderInitializations} " +
                "handoffRecovery=${metrics.handoffDecoderRecoveries}",
        )
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
            experimentMode = experimentMode,
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
            routeState = HandoffRouteState.WAITING_FOR_SOURCE_DESTROY,
            sourceDestroyedAfterMs = null,
            transitionFailureReason = null,
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

    fun recordTransitionFailed(transitionId: Long, reason: String) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            routeState = HandoffRouteState.FAILED,
            transitionFailureReason = reason,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordSourceDestroyed(transitionId: Long, elapsedMs: Long) {
        if (metrics.transitionId != transitionId) return
        metrics = metrics.copy(
            sourceDestroyedAfterMs = elapsedMs,
            playbackPositionMs = player.currentPosition,
        )
    }

    fun recordRouteState(
        transitionId: Long,
        routeState: HandoffRouteState,
        failureReason: String? = null,
    ) {
        if (metrics.transitionId != transitionId) return
        if (metrics.routeState == HandoffRouteState.FAILED && routeState != HandoffRouteState.FAILED) return
        metrics = metrics.copy(
            routeState = routeState,
            transitionFailureReason = failureReason,
            playbackPositionMs = player.currentPosition,
        )
    }

    private fun logPlayerEvent(event: String) {
        Log.i(TAG, "player_event=$event ${playerContext()}")
    }

    private fun playerContext(): String =
        "transition=${metrics.transitionId} target=${metrics.currentTarget?.name} " +
            "position=${player.currentPosition}ms state=${playbackStateName(player.playbackState)} " +
            "playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} " +
            "audioSession=${player.audioSessionId}"

    companion object {
        private const val TAG = "ViriviriPlayerPoC"
        private const val TEST_VIDEO_URI = "asset:///poc/rick.mp4"

        private fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))

        private fun playbackStateName(playbackState: Int): String = when (playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($playbackState)"
        }

        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager = instance ?: synchronized(this) {
            instance ?: PlayerManager(context.applicationContext).also { instance = it }
        }

        fun releaseInstanceIfPresent() {
            instance?.release()
        }

        private fun clearInstance(manager: PlayerManager) = synchronized(this) {
            if (instance === manager) instance = null
        }
    }
}
