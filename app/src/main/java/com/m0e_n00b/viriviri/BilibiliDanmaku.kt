package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEmissionDirection
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal fun parseBilibiliDanmakuXml(xml: String): List<DanmakuEvent> {
  val factory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  }
  val comments = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).getElementsByTagName("d")
  return buildList {
    for (index in 0 until comments.length) {
      val element = comments.item(index) as? org.w3c.dom.Element ?: continue
      val parts = element.getAttribute("p").split(',')
      val startMs = parts.getOrNull(0)?.toDoubleOrNull()?.times(1_000)?.toLong() ?: continue
      val mode = parts.getOrNull(1)?.toIntOrNull() ?: continue
      val text = element.textContent.trim().takeIf(String::isNotBlank) ?: continue
      val lane = when (mode) {
        1, 2, 3 -> DanmakuLaneFamily.SCROLLING to DanmakuEmissionDirection.RIGHT_TO_LEFT
        4 -> DanmakuLaneFamily.TOP_FIXED to null
        5 -> DanmakuLaneFamily.BOTTOM_FIXED to null
        else -> continue
      }
      add(
          DanmakuEvent(
              id = "${startMs}:${index}",
              startMs = startMs,
              text = text,
              laneFamily = lane.first,
              emissionDirection = lane.second,
          )
      )
    }
  }
}
