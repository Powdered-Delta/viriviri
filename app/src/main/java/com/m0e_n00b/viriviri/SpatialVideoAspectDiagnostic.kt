package com.m0e_n00b.viriviri

internal data class SpatialVideoAspectDiagnostic(
    val videoWidth: Int,
    val videoHeight: Int,
    val pixelWidthHeightRatio: Float,
    val displayAspectRatio: Float,
    val contentHalfWidth: Float,
    val contentHalfHeight: Float,
)

internal fun spatialVideoAspectDiagnostic(
    stageWidth: Float,
    stageHeight: Float,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float,
): SpatialVideoAspectDiagnostic {
  val content =
      spatialVideoContentQuad(
          stageWidth = stageWidth,
          stageHeight = stageHeight,
          videoWidth = videoWidth,
          videoHeight = videoHeight,
          pixelWidthHeightRatio = pixelWidthHeightRatio,
      )
  val displayAspectRatio =
      if (videoWidth > 0 && videoHeight > 0 && pixelWidthHeightRatio.isFinite()) {
        videoWidth.toFloat() * pixelWidthHeightRatio / videoHeight
      } else {
        0f
      }
  return SpatialVideoAspectDiagnostic(
      videoWidth = videoWidth,
      videoHeight = videoHeight,
      pixelWidthHeightRatio = pixelWidthHeightRatio,
      displayAspectRatio = displayAspectRatio,
      contentHalfWidth = content.halfWidth,
      contentHalfHeight = content.halfHeight,
  )
}
