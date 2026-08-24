package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoListFilterTest {
  @Test
  fun searchFilterMapsToBilibiliOrderAndRangeParameters() {
    val options =
        VideoListFilterState(
            sort = VideoListSort.FAVORITES,
            date = VideoListDateFilter.THIS_WEEK,
            duration = VideoListDurationFilter.MEDIUM,
        ).toBilibiliSearchOptions()

    assertEquals("stow", options.order)
    assertEquals(VideoListDateFilter.THIS_WEEK.ordinal, options.pubdate)
    assertEquals(VideoListDurationFilter.MEDIUM.ordinal, options.duration)
  }

  @Test
  fun comprehensiveFilterUsesTotalRankByDefault() {
    assertEquals("totalrank", VideoListFilterState().toBilibiliSearchOptions().order)
  }

  @Test
  fun recommendationFilterSortsAndFiltersLocally() {
    val now = 1_000_000L
    val recommendations =
        listOf(
            recommendation("old", publishedAt = now - 10 * 86_400L, danmaku = 9, favorite = 3, duration = 120),
            recommendation("latest", publishedAt = now - 60L, danmaku = 2, favorite = 20, duration = 900),
            recommendation("popular", publishedAt = now - 120L, danmaku = 30, favorite = 5, duration = 2_400),
        )

    assertEquals(
        listOf("latest", "popular", "old"),
        VideoListFilterState(sort = VideoListSort.LATEST).filterRecommendations(recommendations, now).map { it.videoId },
    )
    assertEquals(
        listOf("popular", "old", "latest"),
        VideoListFilterState(sort = VideoListSort.DANMAKU).filterRecommendations(recommendations, now).map { it.videoId },
    )
    assertEquals(
        listOf("latest"),
        VideoListFilterState(date = VideoListDateFilter.TODAY, duration = VideoListDurationFilter.MEDIUM)
            .filterRecommendations(recommendations, now)
            .map { it.videoId },
    )
  }

  private fun recommendation(
      id: String,
      publishedAt: Long,
      danmaku: Long,
      favorite: Long,
      duration: Int,
  ) = Recommendation(
      videoId = id,
      title = id,
      authorName = "author",
      coverUrl = null,
      durationSeconds = duration,
      viewCount = 0,
      displayLabel = null,
      videoUrl = "https://example.invalid/$id",
      publishedAtEpochSeconds = publishedAt,
      danmakuCount = danmaku,
      favoriteCount = favorite,
  )
}
