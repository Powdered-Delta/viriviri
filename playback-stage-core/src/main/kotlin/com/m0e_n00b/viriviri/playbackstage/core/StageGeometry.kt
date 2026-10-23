package com.m0e_n00b.viriviri.playbackstage.core

/** Raw controls only. Effective cylinder radius is derived once and never normalized again. */
data class StageParameters(
    val scale: Float = 1f,
    val targetAspect: Float? = null,
    val curvature: StageCurvature = StageCurvature.Flat,
) {
  fun normalized(): StageParameters = copy(
      scale = scale.takeIf { it.isFinite() }?.coerceIn(0.7f, 2f) ?: 1f,
      targetAspect = targetAspect?.takeIf { it.isFinite() && it > 0f },
      curvature = when (curvature) {
        StageCurvature.Flat -> StageCurvature.Flat
        is StageCurvature.Cylinder -> StageCurvature.fromRawRadius(curvature.radiusMeters)
      },
  )
}

sealed interface StageCurvature {
  data object Flat : StageCurvature
  data class Cylinder(val radiusMeters: Float) : StageCurvature

  companion object {
    /** Raw cylinder radii include 8 metres; only larger values resolve to Flat. */
    fun fromRawRadius(radius: Float?): StageCurvature =
        if (radius == null || !radius.isFinite() || radius <= 0f || radius > 8f) Flat
        else Cylinder(radius.coerceAtLeast(1.5f))
  }
}

data class StageVideoMetrics(
    val width: Int = 0,
    val height: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
) {
  fun normalized(): StageVideoMetrics = copy(
      pixelWidthHeightRatio = pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f,
  )
}

data class StagePresentation(
    val videoVisible: Boolean = true,
    val backdropVisible: Boolean = false,
    val danmakuVisible: Boolean = true,
)

data class StageInput(
    val parameters: StageParameters = StageParameters(),
    val video: StageVideoMetrics = StageVideoMetrics(),
    val presentation: StagePresentation = StagePresentation(),
) {
  fun normalized(): StageInput = copy(parameters = parameters.normalized(), video = video.normalized())
}

enum class StageRole { VIDEO, BACKDROP, DANMAKU }

data class StageSize(val width: Float, val height: Float) {
  init { require(width.isFinite() && width > 0f && height.isFinite() && height > 0f) }
}

/** Semantic surface centre, never a native cylinder axis or world-space pose. */
data class StageOffset(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

data class StageLayer(
    val role: StageRole,
    val size: StageSize,
    val effectiveRadiusMeters: Float?,
    val surfaceDistanceMeters: Float,
    val visible: Boolean,
)

data class StageLayout(
    val viewport: StageSize,
    val centerFromBottomAnchor: StageOffset,
    val video: StageLayer,
    val backdrop: StageLayer,
    val danmaku: StageLayer,
) {
  fun layer(role: StageRole): StageLayer = when (role) {
    StageRole.VIDEO -> video
    StageRole.BACKDROP -> backdrop
    StageRole.DANMAKU -> danmaku
  }
}

/** Complete decoded-frame sampling is a backend invariant; aspect fitting only changes size. */
fun solveStageLayout(input: StageInput): StageLayout {
  val normalized = input.normalized()
  val scale = normalized.parameters.scale
  val viewport = StageSize(1.6f * scale, 0.9f * scale)
  val metrics = normalized.video
  val source = if (metrics.width > 0 && metrics.height > 0)
    metrics.width.toFloat() * metrics.pixelWidthHeightRatio / metrics.height else 0f
  val aspect = normalized.parameters.targetAspect
      ?: source.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
  val videoSize = if (aspect >= viewport.width / viewport.height)
    StageSize(viewport.width, viewport.width / aspect)
  else StageSize(viewport.height * aspect, viewport.height)
  val radius = (normalized.parameters.curvature as? StageCurvature.Cylinder)?.radiusMeters?.times(scale)
  return StageLayout(
      viewport, StageOffset(y = 0.45f * scale + 0.06f),
      StageLayer(StageRole.VIDEO, videoSize, radius, 0f, normalized.presentation.videoVisible),
      StageLayer(StageRole.BACKDROP, viewport, radius, 0.01f * scale, normalized.presentation.backdropVisible),
      StageLayer(StageRole.DANMAKU, viewport, radius, -0.01f * scale, normalized.presentation.danmakuVisible),
  )
}
