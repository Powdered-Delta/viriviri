package com.m0e_n00b.viriviri

import android.graphics.Paint
import android.util.Log
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Lead time before a segment boundary at which to prefetch the next segment. */
private const val PREFETCH_LEAD_MS = 60_000L

/** Background daemon thread for segment network loads (shared; draw loop only submits tasks). */
private val DEFAULT_EXECUTOR: java.util.concurrent.Executor =
    java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "DanmakuSeg").apply { isDaemon = true }
    }
/** Position jumps larger than this are treated as seeks (active set is rebuilt). */
private const val SEEK_RESET_THRESHOLD_MS = 2_000L
/** On the first frame after a seek, admit events starting this far back (nearby visible comments). */
private const val LOOKBACK_MS = 1_500L

/**
 * One scrolling/pinned danmaku currently on a canvas. Pooled: instances are recycled via
 * [DanmakuCanvasRuntime] so high-density playback never pays per-frame allocation/GC.
 */
internal class ActiveDanmaku {
  lateinit var event: DanmakuEvent
  var metrics: DanmakuRenderMetrics = DanmakuRenderMetrics(0f, 1f, 0, 0f)
  var lane: Int = 0
  var family: DanmakuLaneFamily = DanmakuLaneFamily.SCROLLING
  /** Absolute tick after which the item is fully off-canvas and retires. */
  var expiresAtMs: Long = 0L
  /** Absolute tick at which the item entered the canvas (drives scroll progress). */
  var startTickMs: Long = 0L
}

/**
 * Shared, streaming danmaku data layer for ONE video. Lazily fetches 6-minute protobuf segments
 * by playback position, buckets events by 0.1s, and caches measured [DanmakuRenderMetrics].
 *
 * Multi-canvas: this source is shared across every danmaku canvas (single network/cache, one
 * measurement pass). Each canvas owns a [DanmakuCanvasRuntime] that schedules its own lanes over
 * this shared source.
 */
internal class DanmakuStreamSource(
    /** Resolves the danmaku cid; invoked lazily on the background fetch thread (it does network I/O). */
    private val resolveContentId: () -> Long = { -1L },
    private val fetchSegment: (contentId: Long, segmentIndex: Int) -> List<DanmakuEvent> = { _, _ -> emptyList() },
    private val measure: (DanmakuEvent, Float) -> DanmakuRenderMetrics = defaultMeasure,
    /** Segment loads run off the UI thread in production; tests inject a synchronous executor. */
    private val executor: java.util.concurrent.Executor = DEFAULT_EXECUTOR,
) {
  @Volatile private var contentId: Long = 0L // 0 = unresolved; -1 = failed
  private val segments = ConcurrentHashMap<Int, List<DanmakuEvent>>()
  private val inFlight = ConcurrentHashMap.newKeySet<Int>()
  /** Buckets keyed by startMs/100; populated lazily (off the UI thread) as segments load. */
  private val buckets = ConcurrentHashMap<Long, MutableList<DanmakuEvent>>()
  private val metricsCache = ConcurrentHashMap<String, DanmakuRenderMetrics>()
  private val canvases = mutableListOf<WeakReference<DanmakuCanvasRuntime>>()

  // Debug/telemetry counters (cheap; updated only when a segment finishes loading).
  @Volatile private var totalEvents = 0
  @Volatile private var lastSegment = 0

  fun registerCanvas(runtime: DanmakuCanvasRuntime) {
    canvases.removeAll { it.get() == null || it.get() === runtime }
    canvases.add(WeakReference(runtime))
  }

  private fun activeCanvasCount(): Int {
    var alive = 0
    val iter = canvases.iterator()
    while (iter.hasNext()) if (iter.next().get() != null) alive++
    return alive
  }

  /** Live one-line status for the debug panel / logcat. */
  fun debugSummary(): String =
      "seg " + segments.size + "|last " + lastSegment +
          " | danmaku " + totalEvents +
          " | canvas " + activeCanvasCount() +
          when (contentId) {
            0L -> " | cid resolving"
            -1L -> " | cid n/a"
            else -> " | cid " + contentId
          }

  fun metricsFor(event: DanmakuEvent): DanmakuRenderMetrics =
      metricsCache.getOrPut(event.id) {
        val scale = event.styleOverride?.fontScale?.coerceIn(0.6f, 2.5f) ?: 1f
        measure(event, scale)
      }

  /** Events scheduled at [bucketIndex] (startMs/100); empty when the segment is not loaded yet. */
  fun eventsInBucket(bucketIndex: Long): List<DanmakuEvent> = buckets[bucketIndex].orEmpty()

  /** Loads the segment containing [progressMs] (plus a prefetch hint); safe to call every tick. */
  fun ensureSegmentLoaded(progressMs: Long) {
    val index = BilibiliDanmakuProto.segmentIndexFor(progressMs)
    loadSegment(index)
    // Near a segment boundary, prefetch the next one so playback never waits on a fetch.
    if (progressMs.rem(BilibiliDanmakuProto.SEGMENT_DURATION_MS) >
        BilibiliDanmakuProto.SEGMENT_DURATION_MS - PREFETCH_LEAD_MS) {
      loadSegment(index + 1)
    }
  }

  private fun loadSegment(index: Int) {
    if (index <= 0 || segments.containsKey(index) || !inFlight.add(index)) return
    executor.execute {
      try {
        if (contentId == 0L) {
          contentId = runCatching { resolveContentId() }.getOrElse { -1L }
        }
        val events = fetchSegment(contentId, index)
        for (event in events) {
          buckets.getOrPut(event.startMs / 100L) { java.util.Collections.synchronizedList(ArrayList()) }.add(event)
        }
        segments[index] = events
        totalEvents += events.size
        lastSegment = index
        danmakuLog("seg.so cid=" + contentId + " seg=" + index + " elems=" + events.size + " total=" + totalEvents)
      } catch (error: Throwable) {
        danmakuLog("seg.so seg=" + index + " failed: " + error.message)
      } finally {
        inFlight.remove(index)
      }
    }
  }

  companion object {
    // Lazily created (and never touched in JVM tests that inject their own measure) so simply
    // loading the class does not require android.graphics.Paint.
    @Volatile private var paint: Paint? = null
    val defaultMeasure: (DanmakuEvent, Float) -> DanmakuRenderMetrics = { event, scale ->
      val p = paint ?: Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = DANMAKU_TEXT_SIZE_PX }.also { paint = it }
      DanmakuRenderMetrics(
          textWidthPx = p.measureText(event.text) * scale,
          fontScale = scale,
          textColorArgb = (event.styleOverride?.textColorArgb ?: 0xFFFFFFFFL).toInt(),
          outlineWidthPx = DANMAKU_OUTLINE_WIDTH_PX * scale,
      )
    }
  }
}

/**
 * Per-canvas active-set scheduler. Each canvas maintains its own lanes and a recycled object pool,
 * advancing [active] per playback tick and pulling new events from the shared [DanmakuStreamSource].
 * Lane admission bounds on-screen count and prevents overlap (drops surplus/hot barrage).
 */
internal class DanmakuCanvasRuntime(
    private val source: DanmakuStreamSource,
    private val config: DanmakuRenderConfig = DanmakuRenderConfig.DEFAULT,
) {
  val active = ArrayList<ActiveDanmaku>()
  val activeCount: Int get() = active.size

  init {
    source.registerCanvas(this)
  }
  private val pool = ArrayDeque<ActiveDanmaku>()
  private val scrollingFreeAt = LongArray(config.scrollingLaneCount)
  private val topFreeAt = LongArray(config.fixedLaneCount)
  private val bottomFreeAt = LongArray(config.fixedLaneCount)
  private val seenIds = HashSet<String>()
  private var lastPositionMs = Long.MIN_VALUE

  fun advance(positionMs: Long) {
    source.ensureSegmentLoaded(positionMs)

    // Seek detection: a backward jump (or large forward jump) means the active set is stale.
    val firstFrame = lastPositionMs == Long.MIN_VALUE
    val bigJump = !firstFrame && Math.abs(positionMs - lastPositionMs) > SEEK_RESET_THRESHOLD_MS
    if (!firstFrame && (positionMs < lastPositionMs || bigJump)) {
      resetActive()
    }
    // On the first frame (or after a seek) admit from slightly before now so nearby comments show
    // immediately; on playback ticks admit only the new events in (lastPosition, position].
    val windowStart =
        if (firstFrame || bigJump) (positionMs - LOOKBACK_MS).coerceAtLeast(0L) else lastPositionMs
    lastPositionMs = positionMs

    retireExpired(positionMs)

    // Admit events whose start falls in [windowStart, positionMs], deduped, so a segment that
    // arrives slightly late still shows (the draw loop is the UI thread; the fetch is background).
    val firstBucket = windowStart / 100L
    val lastBucketIndex = positionMs / 100L
    var bucket = firstBucket
    while (bucket <= lastBucketIndex) {
      for (event in source.eventsInBucket(bucket)) {
        if (event.startMs in windowStart..positionMs && seenIds.add(event.id)) {
          admit(event, positionMs)
        }
      }
      bucket++
    }
  }

  private fun resetActive() {
    pool.addAll(active)
    active.clear()
    seenIds.clear()
    scrollingFreeAt.fill(0L)
    topFreeAt.fill(0L)
    bottomFreeAt.fill(0L)
  }

  private fun admit(event: DanmakuEvent, nowMs: Long) {
    if (config.weightThreshold > 0 &&
        event.weight != DanmakuEvent.WEIGHT_UNKNOWN &&
        event.weight < config.weightThreshold) {
      return
    }
    val duration =
        if (event.laneFamily == DanmakuLaneFamily.SCROLLING) config.scrollingDurationMs else config.fixedDurationMs
    val metrics = source.metricsFor(event)
    val chosen = pickLane(event, nowMs, duration) ?: return
    // Lane stays occupied while the item crosses plus an entry gap so the next comment cannot spawn
    // on top of it (keeps scrolling lanes non-overlapping). Fixed lanes use their dwell duration.
    val entryGap = if (event.laneFamily == DanmakuLaneFamily.SCROLLING) config.scrollingDurationMs / 4L else 0L
    // Lane is blocked (for the next item) until the occupant clears the entry edge; the item
    // itself retires from the active set after just its crossing/dwell window.
    chosen.first[chosen.second] = nowMs + duration + entryGap
    val item = pool.removeLastOrNull() ?: ActiveDanmaku()
    item.event = event
    item.metrics = metrics
    item.lane = chosen.second
    item.family = event.laneFamily
    item.expiresAtMs = nowMs + duration
    item.startTickMs = nowMs
    active.add(item)
  }

  /** Returns the lane array + chosen lane, or null when admission rejects (no free lane). */
  private fun pickLane(
      event: DanmakuEvent,
      nowMs: Long,
      @Suppress("UNUSED_PARAMETER") durationMs: Long,
  ): Pair<LongArray, Int>? {
    when (event.laneFamily) {
      DanmakuLaneFamily.SCROLLING -> {
        // A lane is free when its occupant has cleared the entry edge (empty lanes hold 0 and are
        // always eligible). The occupant sets freeAt with an entry gap already baked in.
        val free = scrollingFreeAt.indexOfFirstOrNull { it <= nowMs }
        if (free != null) return scrollingFreeAt to free
        return if (config.rejectWhenNoFreeLane) null
        else leastBusy(scrollingFreeAt).takeIf { it >= 0 }?.let { scrollingFreeAt to it }
      }
      DanmakuLaneFamily.TOP_FIXED -> {
        val free = topFreeAt.indexOfFirstOrNull { it <= nowMs }
        if (free != null) return topFreeAt to free
        return if (config.rejectWhenNoFreeLane) null
        else leastBusy(topFreeAt).takeIf { it >= 0 }?.let { topFreeAt to it }
      }
      DanmakuLaneFamily.BOTTOM_FIXED -> {
        val free = bottomFreeAt.indexOfFirstOrNull { it <= nowMs }
        if (free != null) return bottomFreeAt to free
        return if (config.rejectWhenNoFreeLane) null
        else leastBusy(bottomFreeAt).takeIf { it >= 0 }?.let { bottomFreeAt to it }
      }
    }
  }

  private fun leastBusy(freeAt: LongArray): Int = freeAt.indices.minByOrNull { freeAt[it] } ?: -1

  private fun retireExpired(positionMs: Long) {
    var write = 0
    for (read in active.indices) {
      val item = active[read]
      if (positionMs <= item.expiresAtMs) {
        if (write != read) active[write] = item
        write++
      } else {
        pool.addLast(item)
        seenIds.remove(item.event.id)
      }
    }
    while (active.size > write) active.removeAt(active.size - 1)
  }
}

private inline fun LongArray.indexOfFirstOrNull(predicate: (Long) -> Boolean): Int? {
  for (index in indices) if (predicate(this[index])) return index
  return null
}

/** Logs on-device; harmlessly no-ops under JVM unit tests where android.util.Log is unmocked. */
private fun danmakuLog(message: String) {
  runCatching { Log.i("ViriViriDanmaku", message) }
}
