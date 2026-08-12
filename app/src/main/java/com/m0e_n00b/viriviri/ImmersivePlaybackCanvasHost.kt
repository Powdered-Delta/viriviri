package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.CinemaTheme
import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasEvent
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasReducer
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasState
import com.m0e_n00b.spatialworkbench.core.SpatialTheme

/** App adapter from semantic canvas events to existing Spatial panel-slot visibility requests. */
internal class ImmersivePlaybackCanvasHost(
    private val theme: SpatialTheme = CinemaTheme.create(),
    private val applyVisibleSlots: (Set<PanelSlot>) -> Unit,
) {
  var state: PlaybackCanvasState = PlaybackCanvasState()
    private set

  fun dispatch(event: PlaybackCanvasEvent) {
    state = PlaybackCanvasReducer.reduce(state, event)
    applyVisibleSlots(PlaybackCanvasReducer.visibleSlots(state, theme))
  }

  fun applyInitialState() = applyCurrentState()

  fun applyCurrentState() {
    applyVisibleSlots(PlaybackCanvasReducer.visibleSlots(state, theme))
  }
}
