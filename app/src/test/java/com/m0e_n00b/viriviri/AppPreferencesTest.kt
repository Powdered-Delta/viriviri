package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {
  @Test
  fun historyCodecPreservesOrderAndUnicodeWhileRemovingDuplicates() {
    val encoded = AppPreferenceCodec.encodeHistory(listOf("动画", "VR ASMR", "动画", "  音乐  ", ""))

    assertEquals(listOf("动画", "VR ASMR", "音乐"), AppPreferenceCodec.decodeHistory(encoded))
  }

  @Test
  fun historyCodecIgnoresMalformedEntries() {
    assertEquals(emptyList<String>(), AppPreferenceCodec.decodeHistory("!invalid"))
  }

  @Test
  fun stageScaleCodecDefaultsAndClampsToSupportedRange() {
    assertEquals(PlaybackCanvasSize.STANDARD.scale, AppPreferenceCodec.decodeStageScale(null), 0f)
    assertEquals(PlaybackCanvasSize.MIN_STAGE_SCALE, AppPreferenceCodec.decodeStageScale("0.1"), 0f)
    assertEquals(PlaybackCanvasSize.MAX_STAGE_SCALE, AppPreferenceCodec.decodeStageScale("9.0"), 0f)
    assertEquals(1.23f, AppPreferenceCodec.decodeStageScale("1.23"), 0f)
    assertTrue(PlaybackCanvasSize.clampStageScale(Float.NaN).isFinite())
  }
}
