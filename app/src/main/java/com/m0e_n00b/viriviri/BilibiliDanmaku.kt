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
      val fontSize = parts.getOrNull(2)?.toFloatOrNull() ?: DEFAULT_BILIBILI_FONT_SIZE
      val colorArgb = parts.getOrNull(3)?.toLongOrNull()?.and(0x00FFFFFFL)?.or(0xFF000000L)
      add(
          buildBilibiliDanmakuEvent(
              id = "${startMs}:${index}",
              startMs = startMs,
              mode = mode,
              fontSize = fontSize,
              colorArgb = colorArgb ?: 0xFFFFFFFFL,
              content = text,
          )
          ?: continue
      )
    }
  }
}

internal const val DEFAULT_BILIBILI_FONT_SIZE = 25f
private val DOCTYPE_PATTERN = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)

/**
 * Shared [DanmakuEvent] construction for the XML (list.so) and protobuf (seg.so) sources:
 * maps bilibili mode/color/font-size onto the core overlay model. Returns null for modes we
 * do not render (e.g. code/special danmaku).
 */
internal fun buildBilibiliDanmakuEvent(
    id: String,
    startMs: Long,
    mode: Int,
    fontSize: Float,
    colorArgb: Long,
    content: String,
    weight: Int = DanmakuEvent.WEIGHT_UNKNOWN,
): DanmakuEvent? {
  val lane =
      when (mode) {
        1, 2, 3 -> DanmakuLaneFamily.SCROLLING to DanmakuEmissionDirection.RIGHT_TO_LEFT
        4 -> DanmakuLaneFamily.BOTTOM_FIXED to null
        5 -> DanmakuLaneFamily.TOP_FIXED to null
        6 -> DanmakuLaneFamily.SCROLLING to DanmakuEmissionDirection.LEFT_TO_RIGHT
        else -> return null
      }
  val fontScale = fontSize.takeIf { it > 0f }?.div(DEFAULT_BILIBILI_FONT_SIZE)
  val packedColor = colorArgb.and(0x00FFFFFFL).or(0xFF000000L)
  return DanmakuEvent(
      id = id,
      startMs = startMs,
      text = content,
      laneFamily = lane.first,
      emissionDirection = lane.second,
      styleOverride = OverlayStyleOverride(fontScale = fontScale, textColorArgb = packedColor),
      weight = weight,
  )
}
