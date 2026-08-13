package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import com.m0e_n00b.spatialworkbench.core.ContentAccess
import com.m0e_n00b.spatialworkbench.core.badgeText

class RecommendationPaginationTest {
  @Test
  fun chargingExclusiveAccessRequiresTheDedicatedSeasonFlag() {
    assertEquals(
        ContentAccess.CHARGING_EXCLUSIVE,
        recommendationAccess(isChargeableSeason = true),
    )
    assertEquals(
        ContentAccess.STANDARD,
        recommendationAccess(isChargeableSeason = false),
    )
  }

  @Test
  fun recommendationFeedMappingUsesOnlyExplicitChargingExclusiveField() {
    val charging =
        BilibiliPlaybackProvider.mapRecommendationItem(
            org.json.JSONObject("""{"bvid":"BV1charge","is_chargeable_season":true}""")
        )
    val ordinary = BilibiliPlaybackProvider.mapRecommendationItem(org.json.JSONObject("""{"bvid":"BV1ordinary"}"""))

    assertEquals(ContentAccess.CHARGING_EXCLUSIVE, charging?.access)
    assertEquals(ContentAccess.STANDARD, ordinary?.access)
  }

  @Test
  fun recommendationMappingCarriesChargingBadgeOnlyForExplicitFlag() {
    val response =
        org.json.JSONObject(
            """
            {"data":{"result":[
              {"bvid":"BV1charge","title":"Charge","author":"Creator","pic":"","duration":"1:00","play":"1","is_chargeable_season":true},
              {"bvid":"BV1ordinary","title":"Ordinary","author":"Creator","pic":"","duration":"1:00","play":"1","rights":{"elec":1}}
            ]}}
            """.trimIndent()
        )

    val mapped = BilibiliPlaybackProvider.mapVideoSearchResults(response)

    assertEquals(ContentAccess.CHARGING_EXCLUSIVE, mapped[0].access)
    assertEquals(ContentAccess.STANDARD, mapped[1].access)
  }

  @Test
  fun contentAccessBadgeTextUsesSharedContract() {
    assertEquals("充电", ContentAccess.CHARGING_EXCLUSIVE.badgeText())
    assertNull(ContentAccess.STANDARD.badgeText())
  }

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
