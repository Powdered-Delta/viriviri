package com.m0e_n00b.spatialworkbench.core

/** Runtime interaction focus for the regular WATCH layout. */
enum class PlaybackCanvas {
  QUIET_WATCH,
  PLAYBACK,
  BROWSE,
  CONTEXT,
}

data class PlaybackCanvasState(
    val canvas: PlaybackCanvas = PlaybackCanvas.QUIET_WATCH,
    val isActuallyPlaying: Boolean = false,
)

sealed interface PlaybackCanvasEvent {
  data object PrimaryStageAction : PlaybackCanvasEvent

  data object OpenBrowse : PlaybackCanvasEvent

  data object OpenContext : PlaybackCanvasEvent

  data object Dismiss : PlaybackCanvasEvent

  data object IdleTimeout : PlaybackCanvasEvent

  data class PlaybackStateChanged(val isActuallyPlaying: Boolean) : PlaybackCanvasEvent
}

/**
 * Pure interaction-canvas reducer. Spatial and 2D adapters consume the resolved slots but own
 * rendering, timers, panel input, and player callbacks.
 */
object PlaybackCanvasReducer {
  fun reduce(state: PlaybackCanvasState, event: PlaybackCanvasEvent): PlaybackCanvasState =
      when (event) {
        PlaybackCanvasEvent.PrimaryStageAction ->
            state.copy(canvas = if (state.canvas == PlaybackCanvas.QUIET_WATCH) PlaybackCanvas.PLAYBACK else state.canvas)
        PlaybackCanvasEvent.OpenBrowse -> state.copy(canvas = PlaybackCanvas.BROWSE)
        PlaybackCanvasEvent.OpenContext -> state.copy(canvas = PlaybackCanvas.CONTEXT)
        PlaybackCanvasEvent.Dismiss -> state.copy(canvas = PlaybackCanvas.QUIET_WATCH)
        PlaybackCanvasEvent.IdleTimeout ->
            if (state.isActuallyPlaying && state.canvas == PlaybackCanvas.PLAYBACK) {
              state.copy(canvas = PlaybackCanvas.QUIET_WATCH)
            } else {
              state
            }
        is PlaybackCanvasEvent.PlaybackStateChanged ->
            state.copy(
                isActuallyPlaying = event.isActuallyPlaying,
                canvas =
                    if (!event.isActuallyPlaying && state.isActuallyPlaying && state.canvas == PlaybackCanvas.QUIET_WATCH) {
                      PlaybackCanvas.PLAYBACK
                    } else {
                      state.canvas
                    },
            )
      }

  fun visibleSlots(
      state: PlaybackCanvasState,
      theme: SpatialTheme,
  ): Set<PanelSlot> {
    val requested =
        when (state.canvas) {
          PlaybackCanvas.QUIET_WATCH -> setOf(PanelSlot.MEDIA_STAGE)
          PlaybackCanvas.PLAYBACK -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.TRANSPORT, PanelSlot.SYSTEM_TOOLBAR)
          PlaybackCanvas.BROWSE -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.BROWSE)
          PlaybackCanvas.CONTEXT -> setOf(PanelSlot.MEDIA_STAGE, PanelSlot.CONTEXT)
        }
    val persistent =
        theme.presentationPolicies
            .filterValues { it == PanelPresentationPolicy.PERSISTENT }
            .keys
    return requested + persistent
  }
}
