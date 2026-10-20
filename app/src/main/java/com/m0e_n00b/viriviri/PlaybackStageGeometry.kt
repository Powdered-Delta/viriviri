package com.m0e_n00b.viriviri

/** Decoded pixels inside the fixed Media3 FIT output, in normalized texture coordinates. */
internal data class StageVideoCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}

/** One host-owned geometry value; child layers never derive scale/ratio/curvature independently. */
internal data class PlaybackStageGeometry(
    val viewport: SpatialVideoContentQuad,
    val videoContent: SpatialVideoContentQuad,
    val videoShape: SpatialVideoContentQuad,
    val videoCrop: StageVideoCrop,
    val curvature: PlaybackStageCurvature,
) {
  // Option A: one full-stage raster and physical aspect for danmaku, independent of source aspect.
  val danmakuContent: SpatialVideoContentQuad get() = viewport
  val backdropContent: SpatialVideoContentQuad get() = viewport
}

internal fun playbackStageGeometry(
    stageWidth: Float,
    stageHeight: Float,
    sourceAspect: Float,
    targetAspect: Float?,
    curvature: PlaybackStageCurvature,
    outputAspect: Float = IMMERSIVE_VIDEO_OUTPUT_WIDTH.toFloat() / IMMERSIVE_VIDEO_OUTPUT_HEIGHT,
): PlaybackStageGeometry {
  require(stageWidth.isFinite() && stageWidth > 0f && stageHeight.isFinite() && stageHeight > 0f)
  require(outputAspect.isFinite() && outputAspect > 0f)
  val viewport = SpatialVideoContentQuad(stageWidth / 2f, stageHeight / 2f)
  val source = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: outputAspect
  val target = targetAspect?.takeIf { it.isFinite() && it > 0f } ?: source
  val fitted = spatialVideoContentQuadForAspect(stageWidth, stageHeight, target)
  val crop = if (source < outputAspect) {
    val width = source / outputAspect
    StageVideoCrop(left = (1f - width) / 2f, right = (1f + width) / 2f)
  } else {
    val height = outputAspect / source
    StageVideoCrop(top = (1f - height) / 2f, bottom = (1f + height) / 2f)
  }
  // SDK Crop shrinks BOTH the rendered layer and aperture footprint. Compensate the pre-crop
  // shape, rather than narrowing the whole FIT buffer again (which preserves its black bars).
  return PlaybackStageGeometry(
      viewport = viewport,
      videoContent = fitted,
      videoShape = SpatialVideoContentQuad(fitted.halfWidth / crop.width, fitted.halfHeight / crop.height),
      videoCrop = crop,
      curvature = curvature,
  )
}
