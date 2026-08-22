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

data class DanmakuDepthLayer(
    val id: String,
    val surfaceId: String,
    val depthOffsetMeters: Float,
    val maxActiveItems: Int,
    val basicStyle: OverlayStyleOverride = OverlayStyleOverride(),
)

data class DanmakuSurfaceGroup(
    val id: String,
    val allocationWeight: Float = 1f,
    val sharedCoordinateSpace: DanmakuCoordinateSpace = DanmakuCoordinateSpace(),
    val laneSets: List<DanmakuLaneSetSpec>,
    val layers: List<DanmakuDepthLayer>,
    val occlusionPolicy: DanmakuOcclusionPolicy = DanmakuOcclusionPolicy.AVOID_PROJECTED_OVERLAP,
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
)

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
