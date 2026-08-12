package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCanvasContractsTest {
  @Test
  fun primaryStageActionOpensPlaybackOnlyFromQuietWatch() {
    val quiet = PlaybackCanvasState()
    val playback = PlaybackCanvasReducer.reduce(quiet, PlaybackCanvasEvent.PrimaryStageAction)
    val browse = PlaybackCanvasReducer.reduce(PlaybackCanvasState(PlaybackCanvas.BROWSE), PlaybackCanvasEvent.PrimaryStageAction)

    assertEquals(PlaybackCanvas.PLAYBACK, playback.canvas)
    assertEquals(PlaybackCanvas.BROWSE, browse.canvas)
  }

  @Test
  fun browseAndContextAreMutuallyExclusiveAndDismissReturnsQuietWatch() {
    val browse = PlaybackCanvasReducer.reduce(PlaybackCanvasState(), PlaybackCanvasEvent.OpenBrowse)
    val context = PlaybackCanvasReducer.reduce(browse, PlaybackCanvasEvent.OpenContext)
    val quiet = PlaybackCanvasReducer.reduce(context, PlaybackCanvasEvent.Dismiss)

    assertEquals(PlaybackCanvas.BROWSE, browse.canvas)
    assertEquals(PlaybackCanvas.CONTEXT, context.canvas)
    assertEquals(PlaybackCanvas.QUIET_WATCH, quiet.canvas)
  }

  @Test
  fun idleTimeoutClosesOnlyPlaybackWhileActuallyPlaying() {
    val playingPlayback = PlaybackCanvasState(PlaybackCanvas.PLAYBACK, isActuallyPlaying = true)
    val pausedPlayback = PlaybackCanvasState(PlaybackCanvas.PLAYBACK, isActuallyPlaying = false)
    val playingBrowse = PlaybackCanvasState(PlaybackCanvas.BROWSE, isActuallyPlaying = true)

    assertEquals(
        PlaybackCanvas.QUIET_WATCH,
        PlaybackCanvasReducer.reduce(playingPlayback, PlaybackCanvasEvent.IdleTimeout).canvas,
    )
    assertEquals(
        PlaybackCanvas.PLAYBACK,
        PlaybackCanvasReducer.reduce(pausedPlayback, PlaybackCanvasEvent.IdleTimeout).canvas,
    )
    assertEquals(
        PlaybackCanvas.BROWSE,
        PlaybackCanvasReducer.reduce(playingBrowse, PlaybackCanvasEvent.IdleTimeout).canvas,
    )
  }

  @Test
  fun pauseFromQuietWatchOpensPlaybackOnlyAfterActualPlaybackAndResumePreservesCanvas() {
    val initialStopped =
        PlaybackCanvasReducer.reduce(
            PlaybackCanvasState(),
            PlaybackCanvasEvent.PlaybackStateChanged(isActuallyPlaying = false),
        )
    val paused =
        PlaybackCanvasReducer.reduce(
            PlaybackCanvasState(isActuallyPlaying = true),
            PlaybackCanvasEvent.PlaybackStateChanged(isActuallyPlaying = false),
        )
    val resumed =
        PlaybackCanvasReducer.reduce(
            paused.copy(canvas = PlaybackCanvas.CONTEXT),
            PlaybackCanvasEvent.PlaybackStateChanged(isActuallyPlaying = true),
        )

    assertEquals(PlaybackCanvas.QUIET_WATCH, initialStopped.canvas)
    assertFalse(paused.isActuallyPlaying)
    assertEquals(PlaybackCanvas.PLAYBACK, paused.canvas)
    assertTrue(resumed.isActuallyPlaying)
    assertEquals(PlaybackCanvas.CONTEXT, resumed.canvas)
  }

  @Test
  fun cinemaThemeRecipesKeepOnDemandRailsOutOfQuietAndPlaybackCanvases() {
    val canvases = CinemaTheme.create().canvases.associateBy(WorkbenchCanvas::id)

    val quiet = canvases.getValue("cinema-watch-quiet").visibleSlots
    val playback = canvases.getValue("cinema-watch-controls").visibleSlots

    assertFalse(PanelSlot.BROWSE in quiet)
    assertFalse(PanelSlot.CONTEXT in quiet)
    assertFalse(PanelSlot.BROWSE in playback)
    assertFalse(PanelSlot.CONTEXT in playback)
    assertTrue(PanelSlot.TRANSPORT in playback)
  }

  @Test
  fun visibleSlotsIncludePersistentButNotOnDemandSlotsInQuietWatch() {
    val theme = CinemaTheme.create()
    val quiet = PlaybackCanvasReducer.visibleSlots(PlaybackCanvasState(), theme)
    val playback = PlaybackCanvasReducer.visibleSlots(PlaybackCanvasState(PlaybackCanvas.PLAYBACK), theme)
    val browse = PlaybackCanvasReducer.visibleSlots(PlaybackCanvasState(PlaybackCanvas.BROWSE), theme)

    assertTrue(PanelSlot.MEDIA_STAGE in quiet)
    assertTrue(PanelSlot.SYSTEM_TOOLBAR in quiet)
    assertFalse(PanelSlot.TRANSPORT in quiet)
    assertFalse(PanelSlot.BROWSE in quiet)
    assertFalse(PanelSlot.CONTEXT in quiet)

    assertTrue(PanelSlot.TRANSPORT in playback)
    assertTrue(PanelSlot.BROWSE in browse)
    assertFalse(PanelSlot.CONTEXT in browse)
  }
}
