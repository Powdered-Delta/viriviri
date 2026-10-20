package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCanvasContractsTest {
  @Test
  fun primaryStageActionMovesQuietWatchBrowseAndContextToPlayback() {
    val quiet = PlaybackCanvasState()
    val playback = PlaybackCanvasReducer.reduce(quiet, PlaybackCanvasEvent.PrimaryStageAction)
    val browse = PlaybackCanvasReducer.reduce(PlaybackCanvasState(PlaybackCanvas.BROWSE), PlaybackCanvasEvent.PrimaryStageAction)
    val context = PlaybackCanvasReducer.reduce(PlaybackCanvasState(PlaybackCanvas.CONTEXT), PlaybackCanvasEvent.PrimaryStageAction)

    assertEquals(PlaybackCanvas.PLAYBACK, playback.canvas)
    assertEquals(PlaybackCanvas.PLAYBACK, browse.canvas)
    assertEquals(PlaybackCanvas.PLAYBACK, context.canvas)
  }

  /**
   * The stage is the Workbench toggle, so repeated actions must walk
   * QUIET_WATCH -> PLAYBACK -> QUIET_WATCH rather than staying in PLAYBACK.
   *
   * A level-triggered call site would flip the Workbench every frame the trigger is held; the
   * edge-triggered firing lives in the app layer (AnalogMediaStageTuningSystem), and this test pins
   * only the pure transition it drives.
   */
  @Test
  fun primaryStageActionTogglesQuietWatchAndPlayback() {
    val opened = PlaybackCanvasReducer.reduce(PlaybackCanvasState(), PlaybackCanvasEvent.PrimaryStageAction)
    val closed = PlaybackCanvasReducer.reduce(opened, PlaybackCanvasEvent.PrimaryStageAction)
    val reopened = PlaybackCanvasReducer.reduce(closed, PlaybackCanvasEvent.PrimaryStageAction)

    assertEquals(PlaybackCanvas.PLAYBACK, opened.canvas)
    assertEquals(PlaybackCanvas.QUIET_WATCH, closed.canvas)
    assertEquals(PlaybackCanvas.PLAYBACK, reopened.canvas)
  }

  /**
   * The toggle is scoped to QUIET_WATCH <-> PLAYBACK. Browse and Context keep returning to Playback,
   * which is the explicit non-goal recorded in the PRD.
   */
  @Test
  fun primaryStageActionReturnsBrowseAndContextToPlaybackWithoutClosing() {
    val browse = PlaybackCanvasState(PlaybackCanvas.BROWSE)
    val context = PlaybackCanvasState(PlaybackCanvas.CONTEXT)

    val fromBrowse = PlaybackCanvasReducer.reduce(browse, PlaybackCanvasEvent.PrimaryStageAction)
    val fromContext = PlaybackCanvasReducer.reduce(context, PlaybackCanvasEvent.PrimaryStageAction)

    assertEquals(PlaybackCanvas.PLAYBACK, fromBrowse.canvas)
    assertEquals(PlaybackCanvas.PLAYBACK, fromContext.canvas)
  }

  @Test
  fun browseAndContextAreMutuallyExclusiveAndDismissReturnsQuietWatch() {
    val browse = PlaybackCanvasReducer.reduce(PlaybackCanvasState(), PlaybackCanvasEvent.OpenBrowse)
    val quietFromBrowse = PlaybackCanvasReducer.reduce(browse, PlaybackCanvasEvent.Dismiss)
    val context = PlaybackCanvasReducer.reduce(browse, PlaybackCanvasEvent.OpenContext)
    val quietFromContext = PlaybackCanvasReducer.reduce(context, PlaybackCanvasEvent.Dismiss)

    assertEquals(PlaybackCanvas.BROWSE, browse.canvas)
    assertEquals(PlaybackCanvas.QUIET_WATCH, quietFromBrowse.canvas)
    assertEquals(PlaybackCanvas.CONTEXT, context.canvas)
    assertEquals(PlaybackCanvas.QUIET_WATCH, quietFromContext.canvas)
  }

  @Test
  fun explicitPlaybackOutcomeClosesBrowseWithoutUsingPrimaryStageAction() {
    val browse = PlaybackCanvasReducer.reduce(PlaybackCanvasState(), PlaybackCanvasEvent.OpenBrowse)
    val playback = PlaybackCanvasReducer.reduce(browse, PlaybackCanvasEvent.OpenPlayback)

    assertEquals(PlaybackCanvas.BROWSE, browse.canvas)
    assertEquals(PlaybackCanvas.PLAYBACK, playback.canvas)
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
