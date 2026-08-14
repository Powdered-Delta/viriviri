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
  return spatialVideoContentQuadForAspect(
      stageWidth = stageWidth,
      stageHeight = stageHeight,
      displayAspectRatio = videoWidth.toFloat() * pixelWidthHeightRatio / videoHeight,
  )
}

internal fun spatialVideoContentQuadForAspect(
    stageWidth: Float,
    stageHeight: Float,
    displayAspectRatio: Float,
): SpatialVideoContentQuad {
  val fullStage = SpatialVideoContentQuad(stageWidth / 2f, stageHeight / 2f)
  if (
      stageWidth <= 0f ||
          stageHeight <= 0f ||
          !displayAspectRatio.isFinite() ||
          displayAspectRatio <= 0f
  ) {
    return fullStage
  }
  val stageRatio = stageWidth / stageHeight
  return if (displayAspectRatio >= stageRatio) {
    SpatialVideoContentQuad(fullStage.halfWidth, stageWidth / displayAspectRatio / 2f)
  } else {
    SpatialVideoContentQuad(stageHeight * displayAspectRatio / 2f, fullStage.halfHeight)
  }
}
