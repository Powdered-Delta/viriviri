package com.m0e_n00b.spatialworkbench.core

enum class OverlayKind {
  DANMAKU,
  CAPTION,
}

enum class OverlayAnchorMode {
  STAGE_LOCKED,
  GAZE_LOCKED,
}

enum class DanmakuLaneFamily {
  SCROLLING,
  TOP_FIXED,
  BOTTOM_FIXED,
}

enum class DanmakuEmissionDirection {
  LEFT_TO_RIGHT,
  RIGHT_TO_LEFT,
  TOP_TO_BOTTOM,
  BOTTOM_TO_TOP,
}

enum class TextWritingMode {
  HORIZONTAL_TB,
  VERTICAL_RL,
  VERTICAL_LR,
}

enum class TextDirection {
  AUTO,
  LTR,
  RTL,
}

enum class TextOverflowPolicy {
  CLIP,
  ELLIPSIZE,
  SCALE_DOWN,
  REJECT,
}

enum class DanmakuOcclusionPolicy {
  AVOID_PROJECTED_OVERLAP,
  ALLOW_BRIEF_OVERLAP,
  FRONT_LAYER_WINS,
}

enum class CaptionDisplayMode {
  ORIGINAL_ONLY,
  TRANSLATED_ONLY,
  BILINGUAL,
}

/** Whether the lane occupancy state is one group-level state or one state per surface (R7.5). */
enum class LaneStateSharing {
  SHARED,
  ISOLATED,
}

data class OverlayTextLayoutStyle(
    val writingMode: TextWritingMode = TextWritingMode.HORIZONTAL_TB,
    val direction: TextDirection = TextDirection.AUTO,
    val maxLines: Int = 1,
    val lineSpacingRatio: Float = 1f,
    val overflow: TextOverflowPolicy = TextOverflowPolicy.CLIP,
)

data class OverlayBasicStyle(
    val fontScale: Float = 1f,
    /** Packed ARGB stored as a Long so core remains platform-neutral. */
    val textColorArgb: Long = 0xFFFFFFFFL,
    val opacity: Float = 1f,
    val speedScale: Float = 1f,
    val outlineWidthDp: Float = 1f,
    val textLayout: OverlayTextLayoutStyle = OverlayTextLayoutStyle(),
)

data class OverlayStyleOverride(
    val fontScale: Float? = null,
    val textColorArgb: Long? = null,
    val opacity: Float? = null,
    val speedScale: Float? = null,
    val outlineWidthDp: Float? = null,
    val textLayout: OverlayTextLayoutStyle? = null,
)

data class OverlaySurfaceSpec(
    val id: String,
    val enabled: Boolean,
    val supportedKinds: Set<OverlayKind>,
    val anchorMode: OverlayAnchorMode,
    val capacity: Int,
    val basicStyle: OverlayBasicStyle = OverlayBasicStyle(),
)

data class DanmakuCoordinateSpace(
    val widthUnits: Float = 1f,
    val heightUnits: Float = 1f,
)

data class DanmakuLaneSetSpec(
    val id: String,
    val laneFamily: DanmakuLaneFamily,
    val emissionDirection: DanmakuEmissionDirection? = null,
    val laneCount: Int,
)

/**
 * A depth layer of a danmaku group.
 *
 * [surfaceIds] is an **allocation domain, not a duplication domain** (design §4.4, decision D4=A):
 * one event is assigned to exactly one of these surfaces by a single deterministic surface-selection
 * step. If a product really needs the same event drawn on several surfaces, that must be declared
 * explicitly through [duplicateTo] instead of being implied by [surfaceIds].
 *
 * When the owning group uses [LaneStateSharing.ISOLATED] and this layer binds more than one surface,
 * each surface keeps its own lane state, so a per-surface quota must be declared in [surfaceQuotas];
 * otherwise the capacity is ambiguous (`AmbiguousLaneSharing`).
 */
data class DanmakuDepthLayer(
    val id: String,
    val surfaceIds: Set<String>,
    val depthOffsetMeters: Float,
    val maxActiveItems: Int,
    val basicStyle: OverlayStyleOverride = OverlayStyleOverride(),
    /** Explicit copy semantics: surfaces that must additionally receive a duplicate of the event. */
    val duplicateTo: Set<String> = emptySet(),
    /** Per-surface active-item quota, required for ISOLATED lane state with multiple surfaces. */
    val surfaceQuotas: Map<String, Int> = emptyMap(),
) {
  /** Every surface this layer may touch (allocation targets plus explicit duplicates). */
  fun allSurfaceIds(): Set<String> = surfaceIds + duplicateTo

  /** Effective capacity for [surfaceId]: layer quota, per-surface quota, and surface capacity bound. */
  fun effectiveCapacity(surfaceId: String, surfaceCapacity: Int): Int {
    val quota = surfaceQuotas[surfaceId] ?: maxActiveItems
    return minOf(surfaceCapacity, maxActiveItems, quota)
  }
}

data class DanmakuSurfaceGroup(
    val id: String,
    val allocationWeight: Float = 1f,
    val sharedCoordinateSpace: DanmakuCoordinateSpace = DanmakuCoordinateSpace(),
    val laneSets: List<DanmakuLaneSetSpec>,
    val layers: List<DanmakuDepthLayer>,
    val occlusionPolicy: DanmakuOcclusionPolicy = DanmakuOcclusionPolicy.AVOID_PROJECTED_OVERLAP,
    /** One shared lane-occupancy state for the whole group, or one state per surface (R7.5). */
    val laneStateSharing: LaneStateSharing = LaneStateSharing.SHARED,
    /** Group-wide active-item ceiling across all layers and surfaces; null = only surface capacity. */
    val maxActiveItems: Int? = null,
    /** Viewpoint-occlusion domain this group belongs to, when it is checked across groups. */
    val occlusionDomainId: String? = null,
)

data class DanmakuOcclusionDomain(
    val id: String,
    val groupIds: Set<String>,
    val requireCrossGroupChecks: Boolean = true,
)

data class DanmakuEvent(
    val id: String,
    val startMs: Long,
    val text: String,
    val laneFamily: DanmakuLaneFamily = DanmakuLaneFamily.SCROLLING,
    val emissionDirection: DanmakuEmissionDirection? = DanmakuEmissionDirection.RIGHT_TO_LEFT,
    val styleOverride: OverlayStyleOverride? = null,
    /**
     * Provider density/weight signal (e.g. bilibili weight). Higher = denser/hotter barrage;
     * admission may drop low-weight events first. [WEIGHT_UNKNOWN] means the source has no
     * weight and the event must never be dropped on weight grounds.
     */
    val weight: Int = WEIGHT_UNKNOWN,
) {
    companion object {
        const val WEIGHT_UNKNOWN: Int = -1
    }
}

data class CaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val sourceLanguage: String? = null,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
)

data class TranslationSettings(
    val enabled: Boolean = false,
    val providerId: String? = null,
    val modelId: String? = null,
    val targetLanguage: String? = null,
    val maxConcurrentRequests: Int = 1,
    val timeoutMs: Long = 5_000L,
    val prefetchWindowMs: Long = 0L,
) {
  fun cacheKey(cue: CaptionCue): String? {
    if (!enabled) return null
    val provider = providerId?.takeIf(String::isNotBlank) ?: return null
    val model = modelId?.takeIf(String::isNotBlank) ?: return null
    val language = targetLanguage?.takeIf(String::isNotBlank) ?: return null
    return "${cue.id}:${cue.originalText.hashCode()}:$language:$provider:$model"
  }
}

interface OverlayTransformPlugin {
  val id: String
  val version: String
  val order: Int
  val idempotent: Boolean
}

interface DanmakuTransformPlugin : OverlayTransformPlugin {
  fun transform(events: List<DanmakuEvent>): List<DanmakuEvent>
}

interface CaptionTransformPlugin : OverlayTransformPlugin {
  fun transform(cues: List<CaptionCue>): List<CaptionCue>
}

data class OverlayMeasuredBounds(
    val widthUnits: Float,
    val heightUnits: Float,
)

data class ResolvedDanmakuStyle(
    val surfaceStyle: OverlayBasicStyle,
    val layerStyle: OverlayStyleOverride,
    val eventStyle: OverlayStyleOverride?,
    val resolvedStyle: OverlayBasicStyle,
)

interface OverlayMetricsResolver {
  fun measure(
      event: DanmakuEvent,
      style: ResolvedDanmakuStyle,
      surface: OverlaySurfaceSpec,
  ): OverlayMeasuredBounds
}

data class ViewerPose(val id: String)

data class ProjectedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

interface ViewerProjectionResolver {
  fun projectedBounds(
      assignment: ScheduledDanmakuAssignment,
      viewerPose: ViewerPose,
      atPlaybackMs: Long,
  ): ProjectedBounds
}

interface CrossLayerOcclusionResolver {
  fun conflicts(
      candidate: ScheduledDanmakuAssignment,
      scheduled: List<ScheduledDanmakuAssignment>,
      viewerPose: ViewerPose,
      sampleTimesMs: LongArray,
  ): Boolean
}

data class DanmakuAllocationRequest(
    val event: DanmakuEvent,
    val groups: List<DanmakuSurfaceGroup>,
    val surfaces: Map<String, OverlaySurfaceSpec>,
    val activeItemsBySurface: Map<String, Int>,
    val activeItemsByGroup: Map<String, Int>,
    val unavailableLaneIds: Set<String> = emptySet(),
)

data class ScheduledDanmakuAssignment(
    val eventId: String,
    val groupId: String,
    val laneSetId: String,
    val laneIndex: Int,
    val layerId: String,
    val surfaceId: String,
    val emissionDirection: DanmakuEmissionDirection?,
    val styleSnapshot: ResolvedDanmakuStyle,
)

sealed interface DanmakuAllocationResult {
  data class Assigned(val assignment: ScheduledDanmakuAssignment) : DanmakuAllocationResult

  data class Dropped(val reason: DanmakuDropReason) : DanmakuAllocationResult
}

enum class DanmakuDropReason {
  INVALID_TOPOLOGY,
  NO_ELIGIBLE_SURFACE,
  NO_ELIGIBLE_LANE,
}
