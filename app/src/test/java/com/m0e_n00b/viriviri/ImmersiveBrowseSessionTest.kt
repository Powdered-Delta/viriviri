package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveBrowseSessionTest {
  @Test
  fun openCapturesTheCurrentSelectionAsTheBaseline() {
    assertEquals(
        ImmersiveBrowseSession(baselineVideoId = "BV1current", isActive = true),
        ImmersiveBrowseSessionReducer.open("BV1current"),
    )
  }

  @Test
  fun cancelReturnsToPlaybackExactlyOnce() {
    val active = ImmersiveBrowseSessionReducer.open("BV1current")
    val cancelled = ImmersiveBrowseSessionReducer.cancel(active)
    val repeatedCancel = ImmersiveBrowseSessionReducer.cancel(cancelled.session)

    assertTrue(cancelled.returnToPlayback)
    assertEquals(ImmersiveBrowseSession(), cancelled.session)
    assertFalse(repeatedCancel.returnToPlayback)
  }

  @Test
  fun selectingAVideoFromBrowseReturnsToPlayback() {
    val active = ImmersiveBrowseSessionReducer.open("BV1current")
    val sameVideo =
        ImmersiveBrowseSessionReducer.onAppState(
            session = active,
            canvas = PlaybackCanvas.BROWSE,
            destination = ViriViriDestination.VIEWER,
            previousDestination = ViriViriDestination.RECOMMENDATIONS,
        )
    val differentVideo =
        ImmersiveBrowseSessionReducer.onAppState(
            session = active.copy(baselineVideoId = "BV1other"),
            canvas = PlaybackCanvas.BROWSE,
            destination = ViriViriDestination.VIEWER,
            previousDestination = ViriViriDestination.RECOMMENDATIONS,
        )

    assertTrue(sameVideo.returnToPlayback)
    assertTrue(differentVideo.returnToPlayback)
    assertEquals(ImmersiveBrowseSession(), sameVideo.session)
    assertEquals(ImmersiveBrowseSession(), differentVideo.session)
  }

  @Test
  fun openingBrowseOrSearchOverAPlayingVideoDoesNotCloseItself() {
    // Regression: destination is already VIEWER before Browse/Search opens, so the
    // state emission that opens the workspace must not be mistaken for a selection.
    val active = ImmersiveBrowseSessionReducer.open("BV1current")
    val openedOverPlayback =
        ImmersiveBrowseSessionReducer.onAppState(
            session = active,
            canvas = PlaybackCanvas.BROWSE,
            destination = ViriViriDestination.VIEWER,
            previousDestination = ViriViriDestination.VIEWER,
        )
    val repeatedEmission =
        ImmersiveBrowseSessionReducer.onAppState(
            session = openedOverPlayback.session,
            canvas = PlaybackCanvas.BROWSE,
            destination = ViriViriDestination.VIEWER,
            previousDestination = ViriViriDestination.VIEWER,
        )

    assertFalse(openedOverPlayback.returnToPlayback)
    assertEquals(active, openedOverPlayback.session)
    assertFalse(repeatedEmission.returnToPlayback)
    assertEquals(active, repeatedEmission.session)
  }

  @Test
  fun recommendationRefreshOrNonBrowseCanvasDoesNotCloseTheSession() {
    val active = ImmersiveBrowseSessionReducer.open("BV1current")
    val refreshed =
        ImmersiveBrowseSessionReducer.onAppState(
            session = active,
            canvas = PlaybackCanvas.BROWSE,
            destination = ViriViriDestination.RECOMMENDATIONS,
            previousDestination = ViriViriDestination.RECOMMENDATIONS,
        )
    val hiddenBrowse =
        ImmersiveBrowseSessionReducer.onAppState(
            session = active,
            canvas = PlaybackCanvas.PLAYBACK,
            destination = ViriViriDestination.VIEWER,
            previousDestination = ViriViriDestination.RECOMMENDATIONS,
        )

    assertFalse(refreshed.returnToPlayback)
    assertEquals(active, refreshed.session)
    assertFalse(hiddenBrowse.returnToPlayback)
    assertEquals(active, hiddenBrowse.session)
  }
}
