package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStageGeometryTest {
  private fun geometry(source: Float, scale: Float = 1f, target: Float? = null) =
      playbackStageGeometry(1.6f * scale, 0.9f * scale, source, target, PlaybackStageCurvature.Flat)

  @Test
  fun portraitRemovesFitBufferBarsWithoutCroppingVideoOrDoubleNarrowing() {
    val g = geometry(9f / 16f)
    assertEquals(0.341796875f, g.videoCrop.left, 0.000001f)
    assertEquals(0.658203125f, g.videoCrop.right, 0.000001f)
    assertEquals(0f, g.videoCrop.top, 0f)
    assertEquals(1f, g.videoCrop.bottom, 0f)
    assertEquals(0.50625f, g.videoShape.widthMeters, 0.0001f)
    assertEquals(0.9f, g.videoShape.heightMeters, 0.0001f)
    assertEquals(g.videoContent, g.videoShape)
  }

  @Test
  fun ultrawideRemovesOnlyTopAndBottomBufferBars() {
    val g = geometry(21f / 9f)
    assertEquals(0f, g.videoCrop.left, 0f)
    assertEquals(1f, g.videoCrop.right, 0f)
    assertEquals(0.1190476f, g.videoCrop.top, 0.0001f)
    assertEquals(1.6f, g.videoShape.widthMeters, 0.0001f)
    assertEquals(1.6f * 9f / 21f, g.videoShape.heightMeters, 0.0001f)
  }

  @Test
  fun danmakuAndBackdropKeepFullViewportAcrossSourceAndTargetRatios() {
    for (source in listOf(9f / 16f, 1f, 16f / 9f, 21f / 9f)) {
      val g = geometry(source, target = 9f / 16f)
      assertEquals(SpatialVideoContentQuad(0.8f, 0.45f), g.danmakuContent)
      assertEquals(g.viewport, g.backdropContent)
      assertEquals(16f / 9f, g.danmakuContent.aspectRatio, 0.0001f)
      // UV trimming cannot pre-divide the physical layer shape or scale the aperture separately.
      assertEquals(0.50625f, g.videoShape.widthMeters, 0.0001f)
      assertEquals(0.9f, g.videoShape.heightMeters, 0.0001f)
    }
  }

  @Test
  fun scalingChangesAllPhysicalFootprintsOnceAndKeepsTextureCrop() {
    val original = geometry(9f / 16f)
    val scaled = geometry(9f / 16f, scale = 1.5f)
    assertEquals(original.videoCrop, scaled.videoCrop)
    assertEquals(original.videoContent.widthMeters * 1.5f, scaled.videoContent.widthMeters, 0.0001f)
    assertEquals(original.videoShape.widthMeters * 1.5f, scaled.videoShape.widthMeters, 0.0001f)
    assertEquals(2.4f, scaled.danmakuContent.widthMeters, 0.0001f)
    assertEquals(1.35f, scaled.backdropContent.heightMeters, 0.0001f)
  }

  @Test
  fun invalidSourceDoesNotCreateInfiniteGeometryOrCrop() {
    for (source in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
      val g = geometry(source)
      assertEquals(StageVideoCrop(), g.videoCrop)
      assertEquals(g.viewport, g.videoContent)
      assertEquals(g.viewport, g.videoShape)
    }
  }

  @Test
  fun radiusDoesNotChangeVideoExtentOrTextureClip() {
    val flat = geometry(9f / 16f)
    for (radius in listOf(1.5f, 2.7f, 7.9f)) {
      val curved = playbackStageGeometry(1.6f, 0.9f, 9f / 16f, null, PlaybackStageCurvature.Cylinder(radius))
      assertEquals(flat.videoShape, curved.videoShape)
      assertEquals(flat.videoCrop, curved.videoCrop)
      assertEquals(flat.danmakuContent, curved.danmakuContent)
    }
  }
}
