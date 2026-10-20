package com.m0e_n00b.viriviri

internal data class SpatialVideoContentQuad(
    val halfWidth: Float,
    val halfHeight: Float,
) {
  val widthMeters: Float
    get() = halfWidth * 2f

  val heightMeters: Float
    get() = halfHeight * 2f

  val aspectRatio: Float
    get() = widthMeters / heightMeters
}

internal data class SpatialPanelDisplaySize(val widthDp: Float, val heightDp: Float)

/** UI texture dimensions with the same aspect ratio as the physical panel shape. */
internal fun spatialPanelDisplaySizeForAspect(
    aspectRatio: Float,
    longEdgeDp: Float = 1280f,
): SpatialPanelDisplaySize {
  val safeLongEdgeDp = longEdgeDp.takeIf { it.isFinite() && it > 0f } ?: 1280f
  if (!aspectRatio.isFinite() || aspectRatio <= 0f) {
    return SpatialPanelDisplaySize(safeLongEdgeDp, safeLongEdgeDp * 9f / 16f)
  }
  return if (aspectRatio >= 1f) {
    SpatialPanelDisplaySize(safeLongEdgeDp, safeLongEdgeDp / aspectRatio)
  } else {
    SpatialPanelDisplaySize(safeLongEdgeDp * aspectRatio, safeLongEdgeDp)
  }
}

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
