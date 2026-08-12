package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PanelSlot
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas
import com.m0e_n00b.spatialworkbench.core.PlaybackCanvasEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePlaybackCanvasHostTest {
  @Test
  fun initialQuietWatchHidesOnDemandAndTransportSlots() {
    val applied = mutableListOf<Set<PanelSlot>>()
    val host = ImmersivePlaybackCanvasHost(applyVisibleSlots = applied::add)

    host.applyInitialState()

    val slots = applied.single()
    assertTrue(PanelSlot.MEDIA_STAGE in slots)
    assertTrue(PanelSlot.SYSTEM_TOOLBAR in slots)
    assertFalse(PanelSlot.TRANSPORT in slots)
    assertFalse(PanelSlot.BROWSE in slots)
    assertFalse(PanelSlot.CONTEXT in slots)
  }

  @Test
  fun primaryStageActionAndIdleTimeoutApplyPlaybackThenQuietSlots() {
    val applied = mutableListOf<Set<PanelSlot>>()
    val host = ImmersivePlaybackCanvasHost(applyVisibleSlots = applied::add)

    host.dispatch(PlaybackCanvasEvent.PrimaryStageAction)
    host.dispatch(PlaybackCanvasEvent.PlaybackStateChanged(isActuallyPlaying = true))
    host.dispatch(PlaybackCanvasEvent.IdleTimeout)

    assertEquals(PlaybackCanvas.QUIET_WATCH, host.state.canvas)
    assertTrue(PanelSlot.TRANSPORT in applied[0])
    assertFalse(PanelSlot.TRANSPORT in applied.last())
  }

  @Test
  fun browseAndContextApplyOnlyTheirOwnOnDemandRail() {
    val applied = mutableListOf<Set<PanelSlot>>()
    val host = ImmersivePlaybackCanvasHost(applyVisibleSlots = applied::add)

    host.dispatch(PlaybackCanvasEvent.OpenBrowse)
    host.dispatch(PlaybackCanvasEvent.OpenContext)

    assertTrue(PanelSlot.BROWSE in applied[0])
    assertFalse(PanelSlot.CONTEXT in applied[0])
    assertTrue(PanelSlot.CONTEXT in applied[1])
    assertFalse(PanelSlot.BROWSE in applied[1])
  }
}
