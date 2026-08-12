package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPaginationTest {
  @Test
  fun mergeAppendsOnlyNewVideoIdsAndKeepsFirstOccurrence() {
    val first = recommendation("BV1first")
    val duplicate = recommendation("BV1first", title = "Duplicate")
    val second = recommendation("BV1second")

    val result = mergeRecommendationPage(listOf(first), listOf(duplicate, second), pageSize = 2)

    assertEquals(listOf(first, second), result.recommendations)
    assertEquals(1, result.addedCount)
    assertTrue(result.canLoadMore)
  }

  @Test
  fun shortOrFullyDuplicatePageStopsPagination() {
    val first = recommendation("BV1first")

    assertFalse(mergeRecommendationPage(listOf(first), emptyList()).canLoadMore)
    assertFalse(mergeRecommendationPage(listOf(first), listOf(first), pageSize = 1).canLoadMore)
  }

  @Test
  fun thumbnailUrlsUseHttpsAndRejectMissingOrUnsupportedValues() {
    assertEquals("https://i0.hdslb.com/cover.jpg", normalizedThumbnailUrl("//i0.hdslb.com/cover.jpg"))
    assertEquals("https://i0.hdslb.com/cover.jpg", normalizedThumbnailUrl("http://i0.hdslb.com/cover.jpg"))
    assertEquals("https://i0.hdslb.com/cover.jpg", normalizedThumbnailUrl("https://i0.hdslb.com/cover.jpg"))
    assertNull(normalizedThumbnailUrl("file:///tmp/cover.jpg"))
    assertNull(normalizedThumbnailUrl("  "))
  }

  private fun recommendation(videoId: String, title: String = videoId) =
      Recommendation(
          videoId = videoId,
          title = title,
          authorName = "author",
          coverUrl = null,
          durationSeconds = null,
          viewCount = null,
          displayLabel = null,
          videoUrl = "https://www.bilibili.com/video/$videoId",
      )
}
