package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveSearchTest {
  @Test
  fun videoSearchEndpoint_usesTheVerifiedWbiSearchContract() {
    assertEquals("/x/web-interface/wbi/search/type", BilibiliPlaybackProvider.videoSearchEndpointPath())
  }

  @Test
  fun mapVideoSearchResults_mapsUsableVideosAndSkipsEntriesWithoutBvid() {
    val results = BilibiliPlaybackProvider.mapVideoSearchResults(
        listOf(
            BilibiliSearchVideo("BV1xx411c7mD", "<em>VR</em> &amp; video", "creator", "https://cover", "01:02:03", "1.2万", "1720000000"),
            BilibiliSearchVideo("", "not a video", "", "", "", "", null),
        )
    )

    assertEquals(1, results.size)
    assertEquals("VR & video", results.single().title)
    assertEquals(3723, results.single().durationSeconds)
    assertEquals(12_000L, results.single().viewCount)
    assertEquals("1720000000", results.single().displayLabel)
    assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", results.single().videoUrl)
  }

  @Test
  fun mapVideoSearchResults_returnsEmptyListForEntriesWithoutBvid() {
    assertTrue(BilibiliPlaybackProvider.mapVideoSearchResults(listOf(BilibiliSearchVideo("", "", "", "", "", "", null))).isEmpty())
  }

  @Test
  fun searchRequestTracker_onlyAcceptsTheLatestRequest() {
    val tracker = SearchRequestTracker()
    val first = tracker.beginRequest()
    val second = tracker.beginRequest()

    assertFalse(tracker.isCurrent(first))
    assertTrue(tracker.isCurrent(second))
  }

  @Test
  fun textureViewScale_letterboxesAndPillarboxesWithoutCropping() {
    assertEquals(TextureViewScale(0.75f, 1f), calculateTextureViewScale(1600, 900, 1600, 1200))
    assertEquals(TextureViewScale(0.31640625f, 1f), calculateTextureViewScale(1600, 900, 900, 1600))
  }

  @Test
  fun textureViewScale_hidesOutputUntilValidVideoDimensionsAreKnown() {
    assertEquals(TextureViewScale(0f, 0f), calculateTextureViewScale(1600, 900, 0, 0))
  }

  @Test
  fun textureViewScale_respectsMedia3PixelAspectRatio() {
    val scale = calculateTextureViewScale(1600, 900, 1600, 1200, pixelWidthHeightRatio = 2f)
    assertEquals(1f, scale.x, 0.0001f)
    assertEquals(2f / 3f, scale.y, 0.0001f)
  }

  @Test
  fun textureViewScale_hidesOutputForAnInvalidPixelAspectRatio() {
    assertEquals(
        TextureViewScale(0f, 0f),
        calculateTextureViewScale(1600, 900, 1600, 900, pixelWidthHeightRatio = 0f),
    )
  }
}
