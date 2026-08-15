package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEmissionDirection
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import com.m0e_n00b.spatialworkbench.core.OverlayStyleOverride
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal fun parseBilibiliDanmakuXml(xml: String): List<DanmakuEvent> {
  // Android's Harmony parser does not implement the usual JAXP disallow-doctype feature.
  // Reject DTD-bearing documents before parsing instead of relying on that unsupported feature.
  if (DOCTYPE_PATTERN.containsMatchIn(xml)) return emptyList()
  val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
  val comments = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).getElementsByTagName("d")
  return buildList {
    for (index in 0 until comments.length) {
      val element = comments.item(index) as? org.w3c.dom.Element ?: continue
      val parts = element.getAttribute("p").split(',')
      val startMs = parts.getOrNull(0)?.toDoubleOrNull()?.times(1_000)?.toLong() ?: continue
      val mode = parts.getOrNull(1)?.toIntOrNull() ?: continue
      val text = element.textContent.trim().takeIf(String::isNotBlank) ?: continue
      val fontScale = parts.getOrNull(2)?.toFloatOrNull()?.div(DEFAULT_BILIBILI_FONT_SIZE)
      val colorArgb = parts.getOrNull(3)?.toLongOrNull()?.and(0x00FFFFFFL)?.or(0xFF000000L)
      val lane = when (mode) {
        1, 2, 3 -> DanmakuLaneFamily.SCROLLING to DanmakuEmissionDirection.RIGHT_TO_LEFT
        4 -> DanmakuLaneFamily.BOTTOM_FIXED to null
        5 -> DanmakuLaneFamily.TOP_FIXED to null
        6 -> DanmakuLaneFamily.SCROLLING to DanmakuEmissionDirection.LEFT_TO_RIGHT
        else -> continue
      }
      add(
          DanmakuEvent(
              id = "${startMs}:${index}",
              startMs = startMs,
              text = text,
              laneFamily = lane.first,
              emissionDirection = lane.second,
              styleOverride = OverlayStyleOverride(fontScale = fontScale, textColorArgb = colorArgb),
          )
      )
    }
  }
}

private const val DEFAULT_BILIBILI_FONT_SIZE = 25f
private val DOCTYPE_PATTERN = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)
