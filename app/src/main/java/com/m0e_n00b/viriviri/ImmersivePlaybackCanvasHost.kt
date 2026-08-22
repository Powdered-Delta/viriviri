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
  private var appliedVisibleSlots: Set<PanelSlot>? = null

  fun dispatch(event: PlaybackCanvasEvent) {
    state = PlaybackCanvasReducer.reduce(state, event)
    applyCurrentVisibleSlots()
  }

  fun applyInitialState() = applyCurrentVisibleSlots()

  fun applyCurrentState() = applyCurrentVisibleSlots()

  private fun applyCurrentVisibleSlots() {
    val visibleSlots = PlaybackCanvasReducer.visibleSlots(state, theme)
    if (visibleSlots == appliedVisibleSlots) return
    appliedVisibleSlots = visibleSlots
    applyVisibleSlots(visibleSlots)
  }
}
