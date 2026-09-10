package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuCanvasRuntimeTest {
  private fun fakeSource(events: List<DanmakuEvent>): DanmakuStreamSource =
      DanmakuStreamSource(
          fetchSegment = { _, _ -> events },
          // Synchronous in tests so buckets are populated before advance() scans them.
          executor = java.util.concurrent.Executor { it.run() },
          measure = { event, scale ->
            DanmakuRenderMetrics(
                textWidthPx = event.text.length * 16f * scale,
                fontScale = scale,
                textColorArgb = 0xFFFFFFFF.toInt(),
                outlineWidthPx = 8f * scale,
            )
          },
      )

  private fun scrolling(id: String, ms: Long, text: String = "c", weight: Int = DanmakuEvent.WEIGHT_UNKNOWN) =
      DanmakuEvent(id, ms, text, weight = weight)

  @Test
  fun activeSetAddsCurrentBucketAndRetiresAfterDuration() {
    val segment =
        listOf(
            scrolling("a", 0L, "alpha"),
            scrolling("c", 9_000L, "gamma"),
        )
    val source = fakeSource(segment)
    val runtime =
        DanmakuCanvasRuntime(source, DanmakuRenderConfig(scrollingLaneCount = 12, scrollingDurationMs = 6_000L))

    runtime.advance(0L)
    assertEquals(1, runtime.active.size)

    // 9s later: a (entered 0, expires 6000) has retired; c just entered.
    runtime.advance(9_000L)
    val activeIds = runtime.active.map { it.event.id }
    assertEquals("active should be only c, got $activeIds", listOf("c"), activeIds)
  }

  @Test
  fun twoCanvasesScheduleIndependentlyOverSharedSource() {
    val segment = (0 until 20).map { scrolling("e$it", 0L, "comment $it") }
    val shared = fakeSource(segment)
    val small = DanmakuCanvasRuntime(shared, DanmakuRenderConfig(scrollingLaneCount = 2))
    val large = DanmakuCanvasRuntime(shared, DanmakuRenderConfig(scrollingLaneCount = 10))

    small.advance(0L)
    large.advance(0L)

    assertEquals(2, small.active.size)
    assertEquals(10, large.active.size)
    // Shared source buckets all events; both canvases reuse the single measurement cache.
    assertEquals(shared.eventsInBucket(0L).size, segment.size)
  }

  @Test
  fun lowWeightEventsAreFiltered() {
    val segment =
        listOf(
            scrolling("hot", 0L, "hot", weight = 9),
            scrolling("cold", 100L, "cold", weight = 1),
        )
    val source = fakeSource(segment)
    val runtime =
        DanmakuCanvasRuntime(source, DanmakuRenderConfig(scrollingLaneCount = 8, weightThreshold = 5))

    runtime.advance(0L)
    runtime.advance(100L)

    val ids = runtime.active.map { it.event.id }
    assertTrue(ids.contains("hot"))
    assertTrue(!ids.contains("cold"))
  }
}