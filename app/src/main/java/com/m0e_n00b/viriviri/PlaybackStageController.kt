package com.m0e_n00b.viriviri

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3

internal enum class PlaybackStageLayer(val baseLocalZ: Float) {
  VIDEO(0f), BACKDROP(0.01f), DANMAKU(-0.01f)
}

internal data class PlaybackStageParameters(
    val scale: Float = PlaybackCanvasSize.STANDARD.scale,
    val targetAspect: Float? = null,
    val curvature: PlaybackStageCurvature = PlaybackStageCurvature.Flat,
)

internal data class PlaybackStageLayerGeometry(
    val role: PlaybackStageLayer,
    val content: SpatialVideoContentQuad,
    val curvature: PlaybackStageCurvature,
    /** Surface separation after stage scaling; curvature already contains the effective radius. */
    val baseLocalZ: Float,
) {
  // All render carriers are aligned to the stage. The axis-origin offset is local, before rotation.
  fun localPose(): Pose = Pose(Vector3(0f, 0f, curvature.entityLocalZForSurface(baseLocalZ)))

  fun worldPose(stageWorld: Pose): Pose = stageWorld * localPose()
}

internal data class PlaybackStageLayout(
    val geometry: PlaybackStageGeometry,
    val danmakuBaseLocalZ: Float,
    val scale: Float,
) {
  val layers: List<PlaybackStageLayerGeometry> = listOf(
      PlaybackStageLayerGeometry(PlaybackStageLayer.VIDEO, geometry.videoShape, geometry.curvature, PlaybackStageLayer.VIDEO.baseLocalZ * scale),
      PlaybackStageLayerGeometry(PlaybackStageLayer.BACKDROP, geometry.backdropContent, geometry.curvature, PlaybackStageLayer.BACKDROP.baseLocalZ * scale),
      PlaybackStageLayerGeometry(PlaybackStageLayer.DANMAKU, geometry.danmakuContent, geometry.curvature, danmakuBaseLocalZ * scale),
  )
}

/**
 * Single geometry owner. Input callbacks submit values; the frame callback alone commits them.
 * A complete panel set is required before a revision is acknowledged. Identity checks also replay
 * the latest layout when the SDK replaces a panel, even if no user setting changed.
 * Input submission and frame execution are serialized: Android and SDK callback ordering must not
 * expose a mixture of a new radius and an old size. No native operation runs in an input callback.
 */
internal class PlaybackStageController<T : Any>(
    private val widthMeters: Float,
    private val heightMeters: Float,
    private val resolve: (PlaybackStageLayer) -> T?,
    private val commit: (PlaybackStageLayout, Map<PlaybackStageLayer, T>) -> Boolean,
) {
  @Volatile var parameters = PlaybackStageParameters()
    private set
  private var videoWidth = 0
  private var videoHeight = 0
  private var pixelRatio = 1f
  private var danmakuBaseLocalZ = PlaybackStageLayer.DANMAKU.baseLocalZ
  private var revision = 0L
  private var appliedRevision = -1L
  private var appliedPanels: Map<PlaybackStageLayer, T> = emptyMap()
  private var closed = false

  @Volatile var layout: PlaybackStageLayout = buildLayout()
    private set

  val diagnostic: SpatialVideoAspectDiagnostic
    get() = synchronized(this) { spatialVideoAspectDiagnostic(
        widthMeters * parameters.scale, heightMeters * parameters.scale,
        videoWidth, videoHeight, pixelRatio,
    ) }

  @Synchronized fun setParameters(value: PlaybackStageParameters) {
    val normalized = value.copy(scale = PlaybackCanvasSize.clampStageScale(value.scale))
    if (closed || normalized == parameters) return
    parameters = normalized
    rebuild()
  }

  @Synchronized fun setVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float) {
    val ratio = pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    if (closed || (videoWidth == width && videoHeight == height && pixelRatio == ratio)) return
    videoWidth = width
    videoHeight = height
    pixelRatio = ratio
    rebuild()
  }

  @Synchronized fun setDanmakuDepth(value: Float) {
    require(value.isFinite())
    if (closed || danmakuBaseLocalZ == value) return
    danmakuBaseLocalZ = value
    rebuild()
  }

  /** Render-policy changes use the same commit path, without manufacturing geometry changes. */
  @Synchronized fun invalidate() {
    if (!closed) revision++
  }

  @Synchronized fun flush() {
    if (closed) return
    val panels = linkedMapOf<PlaybackStageLayer, T>()
    for (role in PlaybackStageLayer.entries) panels[role] = resolve(role) ?: return
    if (appliedRevision == revision && panels.all { (role, panel) -> appliedPanels[role] === panel }) return
    val pendingRevision = revision
    val pendingLayout = layout
    if (commit(pendingLayout, panels)) {
      appliedRevision = pendingRevision
      appliedPanels = panels
    }
  }

  @Synchronized fun close() {
    closed = true
    appliedPanels = emptyMap()
  }

  private fun rebuild() {
    layout = buildLayout()
    revision++
  }

  private fun buildLayout(): PlaybackStageLayout = PlaybackStageLayout(
      playbackStageGeometry(
          widthMeters * parameters.scale, heightMeters * parameters.scale,
          diagnostic.displayAspectRatio, parameters.targetAspect,
          parameters.curvature.scaledBy(parameters.scale),
      ),
      danmakuBaseLocalZ,
      parameters.scale,
  )
}
