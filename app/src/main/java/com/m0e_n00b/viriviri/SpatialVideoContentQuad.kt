package com.m0e_n00b.viriviri

internal data class SpatialVideoContentQuad(
    val halfWidth: Float,
    val halfHeight: Float,
)

internal fun spatialVideoContentQuad(
    stageWidth: Float,
    stageHeight: Float,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float = 1f,
): SpatialVideoContentQuad {
  val fullStage = SpatialVideoContentQuad(stageWidth / 2f, stageHeight / 2f)
  if (
      stageWidth <= 0f ||
          stageHeight <= 0f ||
          videoWidth <= 0 ||
          videoHeight <= 0 ||
          !pixelWidthHeightRatio.isFinite() ||
          pixelWidthHeightRatio <= 0f
  ) {
    return fullStage
  }
  val stageRatio = stageWidth / stageHeight
  val videoRatio = videoWidth.toFloat() * pixelWidthHeightRatio / videoHeight
  return if (videoRatio >= stageRatio) {
    SpatialVideoContentQuad(fullStage.halfWidth, stageWidth / videoRatio / 2f)
  } else {
    SpatialVideoContentQuad(stageHeight * videoRatio / 2f, fullStage.halfHeight)
  }
}
