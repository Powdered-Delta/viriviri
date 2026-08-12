package com.m0e_n00b.viriviri

internal data class ImmersivePlaybackControlState(
    val showPauseIcon: Boolean,
    val isActuallyPlaying: Boolean,
)

internal fun immersivePlaybackControlState(
    playWhenReady: Boolean,
    isPlaying: Boolean,
): ImmersivePlaybackControlState =
    ImmersivePlaybackControlState(
        showPauseIcon = playWhenReady,
        isActuallyPlaying = isPlaying,
    )

/** Preserves the user's playback intent across a temporary seek-drag pause. */
internal class SeekDragPlaybackPolicy {
  private var playWhenReadyBeforeDrag: Boolean? = null

  fun start(playWhenReady: Boolean): Boolean {
    playWhenReadyBeforeDrag = playWhenReady
    return playWhenReady
  }

  fun finish(): Boolean? = playWhenReadyBeforeDrag.also { playWhenReadyBeforeDrag = null }
}
