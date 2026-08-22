package com.m0e_n00b.viriviri

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CenterContentSessionTest {
  @After
  fun resetMode() {
    CenterContentSession.show(CenterContentMode.PLAYBACK)
  }

  @Test
  fun playbackModeKeepsCenterContentHiddenUntilAnExplicitRouteOpensIt() {
    assertEquals(CenterContentMode.PLAYBACK, CenterContentSession.mode.value)
  }

  @Test
  fun centerVideoListUsesTheApprovedThreeColumnGrid() {
    assertEquals(3, CENTER_VIDEO_GRID_COLUMNS)
  }

  @Test
  fun centerPanelSwitchesBetweenVideoListAndSearchWithoutPlayerState() {
    CenterContentSession.show(CenterContentMode.SEARCH)
    assertEquals(CenterContentMode.SEARCH, CenterContentSession.mode.value)

    CenterContentSession.show(CenterContentMode.VIDEO_LIST)
    assertEquals(CenterContentMode.VIDEO_LIST, CenterContentSession.mode.value)
  }
}
