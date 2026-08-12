package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveMediaStatusTest {
  @Test
  fun selectedVideoDisplaysTitleAndAuthor() {
    val status = immersiveMediaStatus(recommendation(title = "Video title", author = "Creator"), error = null)

    assertEquals("Video title", status.title)
    assertEquals("Creator", status.detail)
  }

  @Test
  fun playbackErrorOverridesAuthorWithoutDiscardingTitle() {
    val status = immersiveMediaStatus(recommendation(title = "Video title", author = "Creator"), error = "DASH unavailable")

    assertEquals("Video title", status.title)
    assertEquals("DASH unavailable", status.detail)
  }

  @Test
  fun emptyStateAndLongTextAreStableForFixedPanelGeometry() {
    assertEquals(ImmersiveMediaStatus("No video selected", "Browse to choose a video"), immersiveMediaStatus(null, null))
    assertEquals("ab...", immersiveMediaStatus(recommendation(title = "abcdef", author = "Creator"), null, maxTitleLength = 5).title)
    assertEquals("ab", immersiveMediaStatus(recommendation(title = "abcdef", author = "Creator"), null, maxTitleLength = 2).title)
  }

  private fun recommendation(title: String, author: String) =
      Recommendation("BV1status", title, author, null, null, null, null, "https://www.bilibili.com/video/BV1status")
}
