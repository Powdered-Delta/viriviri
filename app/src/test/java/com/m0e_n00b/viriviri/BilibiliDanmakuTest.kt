package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEmissionDirection
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliDanmakuTest {
  @Test
  fun parserMapsSupportedModesAndEscapedText() {
    val events =
        parseBilibiliDanmakuXml(
            """
            <i>
              <d p="1.5,1,25,16777215,0,0,0,0">scroll &amp; text</d>
              <d p="2,4,25,16777215,0,0,0,0">bottom</d>
              <d p="3,5,25,16777215,0,0,0,0">top</d>
              <d p="4,6,25,16777215,0,0,0,0">reverse</d>
            </i>
            """.trimIndent()
        )

    assertEquals(4, events.size)
    assertEquals(1_500L, events[0].startMs)
    assertEquals("scroll & text", events[0].text)
    assertEquals(DanmakuLaneFamily.SCROLLING, events[0].laneFamily)
    assertEquals(DanmakuEmissionDirection.RIGHT_TO_LEFT, events[0].emissionDirection)
    assertEquals(1f, events[0].styleOverride?.fontScale)
    assertEquals(0xFFFFFFFFL, events[0].styleOverride?.textColorArgb)
    assertEquals(DanmakuLaneFamily.BOTTOM_FIXED, events[1].laneFamily)
    assertEquals(DanmakuLaneFamily.TOP_FIXED, events[2].laneFamily)
    assertEquals(DanmakuEmissionDirection.LEFT_TO_RIGHT, events[3].emissionDirection)
  }

  @Test
  fun parserRejectsDtdBeforeInvokingAndroidXmlParser() {
    assertEquals(
        emptyList<Any>(),
        parseBilibiliDanmakuXml("<!DOCTYPE i [<!ENTITY x 'unsafe'>]><i><d p=\"1,1\">&x;</d></i>"),
    )
  }

  @Test
  fun parserSkipsMalformedOrEmptyEntries() {
    val events =
        parseBilibiliDanmakuXml(
            """
            <i>
              <d p="bad,1">invalid-time</d>
              <d p="1,bad">invalid-mode</d>
              <d p="1,1">   </d>
              <d p="2,1">valid</d>
            </i>
            """.trimIndent()
        )

    assertEquals(listOf("valid"), events.map { it.text })
  }
}
