package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import java.io.ByteArrayOutputStream

/**
 * Minimal, dependency-free protobuf wire reader for Bilibili's segmented danmaku response
 * (GET /x/v2/dm/web/seg.so, message DmSegMobileReply). Only the fields we render are decoded;
 * unknown fields are skipped generically so schema additions do not break parsing.
 *
 * Relevant schema (community/service/dm/v1):
 *   DmSegMobileReply { repeated DanmakuElem elems = 1; }
 *   DanmakuElem { int64 id=1; int32 progress(ms)=2; int32 mode=3; int32 fontsize=4;
 *                 uint32 color=5; string content=7; int32 weight=9; string idStr=12; }
 *
 * Segments are 6 minutes (360000 ms) and 1-based, i.e. segment_index = floor(progressMs / 360000)+1.
 */
internal object BilibiliDanmakuProto {
  const val SEGMENT_DURATION_MS = 6L * 60L * 1000L

  fun segmentIndexFor(progressMs: Long): Int =
      ((progressMs.coerceAtLeast(0L) / SEGMENT_DURATION_MS) + 1L).toInt()

  fun parse(bytes: ByteArray): List<DanmakuEvent> {
    val top = Reader(bytes)
    val events = ArrayList<DanmakuEvent>()
    var ordinal = 0
    while (!top.atEnd()) {
      val tag = top.readVarint()
      val field = (tag shr 3).toInt()
      when ((tag and 0x7L).toInt()) {
        2 -> {
          val payload = top.readLengthDelimited()
          if (field == 1) {
            val elem = parseElem(payload, ordinal++)
            if (elem != null) events.add(elem)
          }
          // other length-delimited top fields: ignore
        }
        0 -> top.readVarint() // skip scalar
        else -> return events // unsupported wire type; stop gracefully
      }
    }
    return events
  }

  private fun parseElem(bytes: ByteArray, ordinal: Int): DanmakuEvent? {
    val r = Reader(bytes)
    var id = 0L
    var idStr: String? = null
    var progress = 0
    var mode = 1
    var fontsize = DEFAULT_BILIBILI_FONT_SIZE.toInt()
    var color = 0xFFFFFFL
    var content: String? = null
    var weight = com.m0e_n00b.spatialworkbench.core.DanmakuEvent.WEIGHT_UNKNOWN
    while (!r.atEnd()) {
      val tag = r.readVarint()
      val field = (tag shr 3).toInt()
      when ((tag and 0x7L).toInt()) {
        0 -> {
          val v = r.readVarint()
          when (field) {
            1 -> id = v
            2 -> progress = v.toInt()
            3 -> mode = v.toInt()
            4 -> fontsize = v.toInt()
            5 -> color = v
            9 -> weight = v.toInt()
          }
        }
        2 -> {
          val payload = r.readLengthDelimited()
          if (field == 7) content = payload.toStringUtf8()
          if (field == 12) idStr = payload.toStringUtf8()
        }
        else -> return null
      }
    }
    val text = content?.trim().orEmpty()
    if (text.isEmpty()) return null
    val eventId = idStr?.takeIf(String::isNotBlank) ?: (if (id != 0L) id.toString() else "$progress:$ordinal")
    return buildBilibiliDanmakuEvent(
        id = eventId,
        startMs = progress.toLong(),
        mode = mode,
        fontSize = fontsize.toFloat(),
        colorArgb = color,
        content = text,
        weight = weight,
    )
  }
}

/** Tiny protobuf wire reader over a byte array. */
private class Reader(private val bytes: ByteArray) {
  private var pos = 0

  fun atEnd(): Boolean = pos >= bytes.size

  fun readVarint(): Long {
    var result = 0L
    var shift = 0
    while (true) {
      if (pos >= bytes.size) return result
      val b = bytes[pos++].toInt() and 0xFF
      result = result or ((b and 0x7F).toLong() shl shift)
      if (b and 0x80 == 0) return result
      shift += 7
      if (shift > 63) return result
    }
  }

  fun readLengthDelimited(): ByteArray {
    val len = readVarint().toInt()
    val start = pos
    val end = (start + len).coerceAtMost(bytes.size)
    val out = bytes.copyOfRange(start, end)
    pos = end
    return out
  }
}

private fun ByteArray.toStringUtf8(): String = toString(Charsets.UTF_8)

/** Builds a varint-tagged field for tests / fixtures. */
internal fun protoVarint(field: Int, value: Long): ByteArray {
  val tag = (field shl 3) or 0
  val out = ByteArrayOutputStream()
  out.write(byteArrayOf(tag.toByte()))
  var v = value
  while (true) {
    val b = (v and 0x7F).toInt()
    v = v ushr 7
    if (v == 0L) {
      out.write(b)
      break
    } else {
      out.write(b or 0x80)
    }
  }
  return out.toByteArray()
}

/** Builds a length-delimited (string/bytes/message) field for tests / fixtures. */
internal fun protoBytes(field: Int, payload: ByteArray): ByteArray {
  val tag = (field shl 3) or 2
  val out = ByteArrayOutputStream()
  out.write(byteArrayOf(tag.toByte()))
  var len = payload.size
  while (true) {
    val b = len and 0x7F
    len = len ushr 7
    if (len == 0) {
      out.write(b)
      break
    } else {
      out.write(b or 0x80)
    }
  }
  out.write(payload)
  return out.toByteArray()
}

internal fun protoString(field: Int, value: String): ByteArray = protoBytes(field, value.toByteArray(Charsets.UTF_8))
