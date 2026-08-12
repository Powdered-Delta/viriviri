package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.PlaybackCanvas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveBrowseSelectionPolicyTest {
  @Test
  fun firstSelectionFromBrowseReturnsToPlayback() {
    assertTrue(
        shouldReturnToPlaybackAfterBrowseSelection(
            awaitingSelection = true,
            canvas = PlaybackCanvas.BROWSE,
            baselineVideoId = null,
            selectedVideoId = "BV1first",
        )
    )
  }

  @Test
  fun sameSelectionOrNonBrowseStateDoesNotCloseBrowse() {
    assertFalse(
        shouldReturnToPlaybackAfterBrowseSelection(
            awaitingSelection = true,
            canvas = PlaybackCanvas.BROWSE,
            baselineVideoId = "BV1same",
            selectedVideoId = "BV1same",
        )
    )
    assertFalse(
        shouldReturnToPlaybackAfterBrowseSelection(
            awaitingSelection = true,
            canvas = PlaybackCanvas.PLAYBACK,
            baselineVideoId = "BV1old",
            selectedVideoId = "BV1new",
        )
    )
  }

  @Test
  fun selectingDifferentVideoFromBrowseReturnsToPlayback() {
    assertTrue(
        shouldReturnToPlaybackAfterBrowseSelection(
            awaitingSelection = true,
            canvas = PlaybackCanvas.BROWSE,
            baselineVideoId = "BV1old",
            selectedVideoId = "BV1new",
        )
    )
  }
}
