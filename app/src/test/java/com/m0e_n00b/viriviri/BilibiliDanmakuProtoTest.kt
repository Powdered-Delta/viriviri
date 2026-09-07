package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliDanmakuProtoTest {
  @Test
  fun segmentIndexIsSixMinutesAndOneBased() {
    assertEquals(1, BilibiliDanmakuProto.segmentIndexFor(0))
    assertEquals(1, BilibiliDanmakuProto.segmentIndexFor(359_999))
    assertEquals(2, BilibiliDanmakuProto.segmentIndexFor(360_000))
    assertEquals(3, BilibiliDanmakuProto.segmentIndexFor(720_000 + 1))
  }

  @Test
  fun decodesScrollingCommentWithColorAndWeight() {
    val elem =
        concat(
            protoVarint(1, 42L), // id
            protoVarint(2, 12_300L), // progress ms
            protoVarint(3, 1L), // mode = scroll right-to-left
            protoVarint(4, 25L), // fontsize
            protoVarint(5, 0xFF0000L), // color red
            protoString(7, "哈哈哈哈"), // content
            protoVarint(9, 8L), // weight
        )
    val reply = protoBytes(1, elem) // repeated elems

    val events = BilibiliDanmakuProto.parse(reply)
    assertEquals(1, events.size)
    val e = events.first()
    assertEquals("42", e.id)
    assertEquals(12_300L, e.startMs)
    assertEquals(DanmakuLaneFamily.SCROLLING, e.laneFamily)
    assertEquals("哈哈哈哈", e.text)
    assertEquals(8, e.weight)
  }

  @Test
  fun skipsUnsupportedModesAndEmptyContent() {
    val mode7 =
        concat(
            protoVarint(1, 1L),
            protoVarint(2, 100L),
            protoVarint(3, 7L), // code/special danmaku -> not rendered
            protoString(7, "special"),
        )
    val blank =
        concat(
            protoVarint(1, 2L),
            protoVarint(2, 200L),
            protoVarint(3, 1L),
            protoString(7, "   "),
        )
    val normal =
        concat(
            protoVarint(1, 3L),
            protoVarint(2, 300L),
            protoVarint(3, 4L), // bottom fixed
            protoString(7, "bottom"),
        )
    val reply = concat(protoBytes(1, mode7), protoBytes(1, blank), protoBytes(1, normal))

    val events = BilibiliDanmakuProto.parse(reply)
    assertEquals(1, events.size)
    assertEquals("bottom", events.first().text)
    assertEquals(DanmakuLaneFamily.BOTTOM_FIXED, events.first().laneFamily)
  }

  @Test
  fun prefersIdStrWhenPresentAndDefaultsWeightToUnknown() {
    val elem =
        concat(
            protoVarint(1, 99L),
            protoString(12, "abc123"),
            protoVarint(2, 500L),
            protoVarint(3, 1L),
            protoString(7, "x"),
        )
    val events = BilibiliDanmakuProto.parse(protoBytes(1, elem))
    val e = events.single()
    assertEquals("abc123", e.id)
    assertEquals(DanmakuEvent.WEIGHT_UNKNOWN, e.weight)
  }

  @Test
  fun ignoresUnknownTopFields() {
    val elem =
        concat(protoVarint(1, 7L), protoVarint(2, 1L), protoVarint(3, 1L), protoString(7, "hi"))
    // Unknown top-level field 99 with a varint and bytes payload must be skipped gracefully.
    val reply =
        concat(
            protoBytes(1, elem),
            protoVarint(99, 123L),
            protoBytes(98, byteArrayOf(1, 2, 3, 4)),
        )
    val events = BilibiliDanmakuProto.parse(reply)
    assertEquals(1, events.size)
    assertEquals("hi", events.first().text)
  }

  private fun concat(vararg parts: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    parts.forEach { out.write(it) }
    return out.toByteArray()
  }
}
