package com.m0e_n00b.viriviri.playbackstage.host

import com.m0e_n00b.viriviri.*
import com.m0e_n00b.viriviri.playbackstage.core.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackStageHostTest {
  @Test fun existingControlsAndPixelMetricsMatchLegacyPhysicalGeometry() {
    for (scale in listOf(0.7f, 1f, 2f, Float.NaN)) {
      for (ratio in PlaybackDisplayRatio.entries) {
        for (curvature in listOf(PlaybackStageCurvature.Flat, PlaybackStageCurvature.Cylinder(1.5f), PlaybackStageCurvature.Cylinder(7.9f))) {
          for (pixelRatio in listOf(1f, 16f / 15f, Float.NaN, 0f)) {
            val state = ViriViriUiState(playbackStageScale = scale, playbackDisplayRatio = ratio, playbackCurvature = curvature)
            val mapped = PlaybackStageHost.mapInput(state, StageVideoMetrics(1440, 1080, pixelRatio), StagePresentation())
            val actual = solveStageLayout(mapped)
            val legacy = PlaybackStageController<Any>(1.6f, 0.9f, { null }, { _, _ -> false })
            legacy.setParameters(PlaybackStageParameters(scale, ratio.displayAspectRatio, curvature))
            legacy.setVideoSize(1440, 1080, pixelRatio)
            assertEquals(legacy.layout.geometry.videoContent.halfWidth * 2f, actual.video.size.width, 0.0001f)
            assertEquals(legacy.layout.geometry.videoContent.halfHeight * 2f, actual.video.size.height, 0.0001f)
            assertEquals(legacy.layout.geometry.viewport.halfWidth * 2f, actual.danmaku.size.width, 0.0001f)
            assertEquals(legacy.layout.geometry.curvature.radiusOrNull(), actual.video.effectiveRadiusMeters)
            assertEquals(0f, actual.centerFromBottomAnchor.z)
          }
        }
      }
    }
  }

  @Test fun hostSubmitsWholeSnapshotThroughRealCoordinatorWithoutOwningRendering() {
    var submitted: StageFrame? = null
    val core = StageCoordinator(object : StageRenderer {
      override fun apply(frame: StageFrame): StageApplyResult {
        submitted = frame
        return StageApplyResult(frame.generations.mapValues { StageRoleResult.Applied(it.value) })
      }
      override fun hide(roles: Set<StageRole>) = Unit
      override fun close() = Unit
    })
    val host = PlaybackStageHost(core)
    val state = ViriViriUiState(playbackStageScale = 2f, playbackDisplayRatio = PlaybackDisplayRatio.PORTRAIT_9_16, playbackCurvature = PlaybackStageCurvature.Cylinder(7.9f))
    val presentation = StagePresentation(videoVisible = true, backdropVisible = true, danmakuVisible = false)
    assertTrue(host.submit(state, StageVideoMetrics(1920, 1080), presentation))
    assertNull(submitted)
    core.renderFrame()
    val frame = submitted!!
    assertEquals(15.8f, frame.layout.video.effectiveRadiusMeters!!, 0.0001f)
    assertTrue(frame.layout.backdrop.visible)
    assertFalse(frame.layout.danmaku.visible)
    assertEquals(0.96f, frame.layout.centerFromBottomAnchor.y, 0.0001f)
    assertEquals(1.0125f, frame.layout.video.size.width, 0.0001f)
    assertFalse(host.submit(state, StageVideoMetrics(1920, 1080), presentation))
  }
}
