package com.m0e_n00b.viriviri

enum class VideoListSort {
  COMPREHENSIVE,
  LATEST,
  DANMAKU,
  FAVORITES,
}

enum class VideoListDateFilter {
  ANY,
  TODAY,
  THIS_WEEK,
  THIS_MONTH,
}

enum class VideoListDurationFilter {
  ANY,
  SHORT,
  MEDIUM,
  LONG,
}

data class VideoListFilterState(
    val sort: VideoListSort = VideoListSort.COMPREHENSIVE,
    val date: VideoListDateFilter = VideoListDateFilter.ANY,
    val duration: VideoListDurationFilter = VideoListDurationFilter.ANY,
) {
  fun toBilibiliSearchOptions(): BilibiliSearchOptions =
      BilibiliSearchOptions(
          order =
              when (sort) {
                VideoListSort.COMPREHENSIVE -> "totalrank"
                VideoListSort.LATEST -> "pubdate"
                VideoListSort.DANMAKU -> "dm"
                VideoListSort.FAVORITES -> "stow"
              },
          pubdate = date.takeIf { it != VideoListDateFilter.ANY }?.ordinal,
          duration = duration.takeIf { it != VideoListDurationFilter.ANY }?.ordinal,
      )

  fun filterRecommendations(
      recommendations: List<Recommendation>,
      nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
  ): List<Recommendation> {
    val dateCutoff =
        when (date) {
          VideoListDateFilter.ANY -> Long.MIN_VALUE
          VideoListDateFilter.TODAY -> nowEpochSeconds - DAY_SECONDS
          VideoListDateFilter.THIS_WEEK -> nowEpochSeconds - 7 * DAY_SECONDS
          VideoListDateFilter.THIS_MONTH -> nowEpochSeconds - 30 * DAY_SECONDS
        }
    val durationMatches: (Recommendation) -> Boolean = { recommendation ->
      when (duration) {
        VideoListDurationFilter.ANY -> true
        VideoListDurationFilter.SHORT -> (recommendation.durationSeconds ?: Int.MAX_VALUE) < SHORT_DURATION_SECONDS
        VideoListDurationFilter.MEDIUM -> (recommendation.durationSeconds ?: -1) in SHORT_DURATION_SECONDS until LONG_DURATION_SECONDS
        VideoListDurationFilter.LONG -> (recommendation.durationSeconds ?: -1) >= LONG_DURATION_SECONDS
      }
    }
    val filtered =
        recommendations.filter { recommendation ->
          durationMatches(recommendation) &&
              (date == VideoListDateFilter.ANY || (recommendation.publishedAtEpochSeconds ?: Long.MIN_VALUE) >= dateCutoff)
        }
    return when (sort) {
      VideoListSort.COMPREHENSIVE -> filtered
      VideoListSort.LATEST -> filtered.sortedByDescending { it.publishedAtEpochSeconds ?: Long.MIN_VALUE }
      VideoListSort.DANMAKU -> filtered.sortedByDescending { it.danmakuCount ?: Long.MIN_VALUE }
      VideoListSort.FAVORITES -> filtered.sortedByDescending { it.favoriteCount ?: Long.MIN_VALUE }
    }
  }

  companion object {
    private const val DAY_SECONDS = 86_400L
    private const val SHORT_DURATION_SECONDS = 10 * 60
    private const val LONG_DURATION_SECONDS = 30 * 60
  }
}
