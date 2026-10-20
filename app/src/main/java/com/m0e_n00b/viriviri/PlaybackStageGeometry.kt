package com.m0e_n00b.viriviri

import com.meta.spatial.runtime.Crop

/** One host-owned geometry value; child layers never derive scale/ratio/curvature independently. */
internal data class PlaybackStageGeometry(
    val viewport: SpatialVideoContentQuad,
    val videoContent: SpatialVideoContentQuad,
    val curvature: PlaybackStageCurvature,
) {
  // Direct decoder Surface: sample the whole frame. Pixel allocation does not imply FIT black bars.
  val videoTextureCrop: Crop get() = Crop.NONE
  // Physical shape supplies display aspect, including source pixel aspect ratio.
  val videoShape: SpatialVideoContentQuad get() = videoContent
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
): PlaybackStageGeometry {
  require(stageWidth.isFinite() && stageWidth > 0f && stageHeight.isFinite() && stageHeight > 0f)
  val viewport = SpatialVideoContentQuad(stageWidth / 2f, stageHeight / 2f)
  val source = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: viewport.aspectRatio
  val target = targetAspect?.takeIf { it.isFinite() && it > 0f } ?: source
  val fitted = spatialVideoContentQuadForAspect(stageWidth, stageHeight, target)
  return PlaybackStageGeometry(
      viewport = viewport,
      videoContent = fitted,
      curvature = curvature,
  )
}
