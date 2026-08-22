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
}
