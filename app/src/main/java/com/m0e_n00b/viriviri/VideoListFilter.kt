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
}
