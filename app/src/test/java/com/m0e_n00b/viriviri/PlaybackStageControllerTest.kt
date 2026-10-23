package com.m0e_n00b.viriviri

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.*
import org.junit.Test

class PlaybackStageControllerTest {
  private class Panel
  private class Host {
    val ready = mutableMapOf<PlaybackStageLayer, Panel>()
    val commits = mutableListOf<Pair<PlaybackStageLayout, Map<PlaybackStageLayer, Panel>>>()
    var canCommit = true
    val controller = PlaybackStageController<Panel>(1.6f, 0.9f, { ready[it] }) { layout, panels ->
      if (canCommit) commits += layout to panels
      canCommit
    }
    fun readyAll() { PlaybackStageLayer.entries.forEach { ready[it] = Panel() } }
  }

  @Test fun waitsForCompleteGroupThenCommitsLatestSettingsAndSourceTogether() {
    val host = Host()
    host.ready[PlaybackStageLayer.VIDEO] = Panel()
    host.controller.setVideoSize(1920, 1080, 1f)
    host.controller.flush()
    host.controller.setParameters(PlaybackStageParameters(1.8f, null, PlaybackStageCurvature.Cylinder(2.7f)))
    host.controller.setVideoSize(1080, 1920, 1f)
    host.ready[PlaybackStageLayer.DANMAKU] = Panel()
    host.controller.flush()
    assertTrue(host.commits.isEmpty())
    host.ready[PlaybackStageLayer.BACKDROP] = Panel()
    host.controller.flush()
    val layout = host.commits.single().first
    assertEquals(2.88f, layout.geometry.viewport.widthMeters, 0.0001f)
    assertEquals(1.62f, layout.geometry.viewport.heightMeters, 0.0001f)
    assertEquals(0.91125f, layout.geometry.videoShape.widthMeters, 0.0001f)
    assertEquals(1.62f, layout.geometry.videoShape.heightMeters, 0.0001f)
    assertEquals(4.86f, layout.geometry.curvature.radiusOrNull()!!, 0.0001f)
    assertEquals(PlaybackStageCurvature.Cylinder(2.7f), host.controller.parameters.curvature)
    assertEquals(layout.geometry.viewport, layout.geometry.danmakuContent)
    assertEquals(layout.geometry.videoContent, layout.geometry.videoShape)
    assertEquals(3, host.commits.single().second.size)
  }

  @Test fun sameInputsAndIdleFramesDoNotReshapePanels() {
    val host = Host(); host.readyAll(); host.controller.flush()
    repeat(5) {
      host.controller.setParameters(PlaybackStageParameters())
      host.controller.setVideoSize(0, 0, 1f)
      host.controller.flush()
    }
    assertEquals(1, host.commits.size)
  }

  @Test fun panelReplacementReplaysEntireCurrentGroupWithoutNewInput() {
    val host = Host(); host.readyAll(); host.controller.flush()
    val replacement = Panel()
    host.ready.remove(PlaybackStageLayer.DANMAKU)
    host.controller.flush()
    assertEquals(1, host.commits.size)
    host.ready[PlaybackStageLayer.DANMAKU] = replacement
    host.controller.flush()
    assertEquals(2, host.commits.size)
    assertSame(replacement, host.commits.last().second[PlaybackStageLayer.DANMAKU])
  }

  @Test fun rejectedCommitStaysPendingAndCoalescesToNewerRadius() {
    val host = Host(); host.readyAll(); host.canCommit = false
    host.controller.setParameters(PlaybackStageParameters(curvature = PlaybackStageCurvature.Cylinder(5f)))
    host.controller.flush()
    host.controller.setParameters(PlaybackStageParameters(curvature = PlaybackStageCurvature.Cylinder(2f)))
    host.canCommit = true; host.controller.flush()
    assertEquals(PlaybackStageCurvature.Cylinder(2f), host.commits.single().first.geometry.curvature)
  }

  @Test fun curveOnlyChangeIsNotSuppressedByUnchangedAspectDiagnostic() {
    val host = Host(); host.readyAll(); host.controller.setVideoSize(1920, 1080, 1f)
    host.controller.flush()
    val diagnostic = host.controller.diagnostic
    host.controller.setParameters(PlaybackStageParameters(curvature = PlaybackStageCurvature.Cylinder(3f)))
    assertEquals(diagnostic, host.controller.diagnostic)
    host.controller.flush()
    host.controller.setParameters(PlaybackStageParameters())
    host.controller.flush()
    assertEquals(3, host.commits.size)
    host.commits.last().first.layers.forEach { assertEquals(it.baseLocalZ, it.localPose().t.z, 0f) }
  }

  @Test fun debugDepthAndRenderPolicyChangesUseSameBatch() {
    val host = Host(); host.readyAll(); host.controller.flush()
    host.controller.setDanmakuDepth(-0.2f); host.controller.invalidate(); host.controller.flush()
    assertEquals(2, host.commits.size)
    val layers = host.commits.last().first.layers.associateBy { it.role }
    assertEquals(-0.2f, layers.getValue(PlaybackStageLayer.DANMAKU).baseLocalZ, 0f)
    assertEquals(0f, layers.getValue(PlaybackStageLayer.VIDEO).baseLocalZ, 0f)
  }

  @Test fun closedControllerDoesNotResolveOrWriteNativePanels() {
    val host = Host(); host.readyAll(); host.controller.flush(); host.controller.close()
    host.controller.invalidate(); host.controller.setVideoSize(1080, 1920, 1f)
    host.controller.setParameters(PlaybackStageParameters(scale = 2f)); host.controller.flush()
    assertEquals(1, host.commits.size)
    assertEquals(1f, host.controller.parameters.scale, 0f)
  }

  @Test fun layoutPinsAllCentralPointsThroughRotatedParentAndRadiusTransitions() {
    val host = Host()
    val stage = Pose(Vector3(0.7f, 1.2f, 2.1f), Quaternion(10f, 35f, 0f))
    for (curvature in listOf(PlaybackStageCurvature.Cylinder(1.5f), PlaybackStageCurvature.Cylinder(7.9f), PlaybackStageCurvature.Flat)) {
      host.controller.setParameters(PlaybackStageParameters(1.7f, null, curvature))
      for (layer in host.controller.layout.layers) {
        val expected = stage * Vector3(0f, 0f, layer.baseLocalZ)
        val actual = layer.worldPose(stage) * Vector3(0f, 0f, layer.curvature.radiusOrNull() ?: 0f)
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
      }
    }
  }

  @Test fun sourcePixelRatioAndTargetOverrideDoNotSqueezeDanmaku() {
    val host = Host()
    host.controller.setParameters(PlaybackStageParameters(targetAspect = 9f / 16f))
    host.controller.setVideoSize(720, 576, 16f / 15f)
    val geometry = host.controller.layout.geometry
    assertEquals(0.50625f, geometry.videoContent.widthMeters, 0.0001f)
    assertEquals(1.6f, geometry.danmakuContent.widthMeters, 0.0001f)
    assertEquals(0.9f, geometry.danmakuContent.heightMeters, 0.0001f)
    // Pixel aspect belongs in physical display aspect, never an inferred source UV crop.
    assertEquals(0f, geometry.videoTextureCrop.left, 0f)
    assertEquals(1f, geometry.videoTextureCrop.right, 0f)
  }

  @Test fun wholeGroupScalePreservesShapeAndScalesAllSurfaceSeparationsExactlyOnce() {
    val host = Host()
    host.controller.setVideoSize(1440, 1080, 1f)
    host.controller.setDanmakuDepth(-0.2f)
    for (radius in listOf(1.5f, 2.7f, 7.9f)) {
      for (scale in listOf(0.7f, 1f, 1.5f, 2f)) {
        host.controller.setParameters(PlaybackStageParameters(scale, null, PlaybackStageCurvature.Cylinder(radius)))
        val geometry = host.controller.layout.geometry
        assertEquals(1.6f * scale, geometry.viewport.widthMeters, 0.0001f)
        assertEquals(0.9f * scale, geometry.viewport.heightMeters, 0.0001f)
        assertEquals(1.2f * scale, geometry.videoShape.widthMeters, 0.0001f)
        assertEquals(radius * scale, geometry.curvature.radiusOrNull()!!, 0.0001f)
        assertEquals(1.6f / radius, geometry.viewport.widthMeters / geometry.curvature.radiusOrNull()!!, 0.0001f)
        for (layer in host.controller.layout.layers) {
          val authoredZ = if (layer.role == PlaybackStageLayer.DANMAKU) -0.2f else layer.role.baseLocalZ
          assertEquals(geometry.curvature, layer.curvature)
          assertEquals(authoredZ * scale, layer.baseLocalZ, 0.0001f)
          assertEquals((authoredZ - radius) * scale, layer.localPose().t.z, 0.0001f)
        }
        assertEquals(0f, geometry.videoTextureCrop.left, 0f)
        assertEquals(1f, geometry.videoTextureCrop.right, 0f)
      }
    }
  }

  @Test fun radiusAndScaleTransitionsResetFromAuthoredValuesAndRestoreRawPreferences() {
    val host = Host(); host.readyAll()
    val restored = PlaybackStageParameters(2f, null, PlaybackStageCurvature.fromRadius(7.9f))
    host.controller.setParameters(restored); host.controller.flush()
    assertEquals(15.8f, host.commits.last().first.geometry.curvature.radiusOrNull()!!, 0.0001f)
    repeat(3) {
      for ((radius, expectedVideoZ) in listOf(2.7f to -5.4f, 1.5f to -3f)) {
        host.controller.setParameters(restored.copy(curvature = PlaybackStageCurvature.fromRadius(radius)))
        host.controller.flush()
        assertEquals(expectedVideoZ, host.commits.last().first.layers.first().localPose().t.z, 0.0001f)
      }
      host.controller.setParameters(restored.copy(curvature = PlaybackStageCurvature.Flat))
      host.controller.flush()
      host.commits.last().first.layers.forEach {
        assertEquals(it.role.baseLocalZ * 2f, it.localPose().t.z, 0.0001f)
      }
      host.controller.setParameters(restored.copy(scale = 0.7f)); host.controller.flush()
      host.controller.setParameters(restored); host.controller.flush()
      assertEquals(restored, host.controller.parameters)
      assertEquals(-15.8f, host.commits.last().first.layers.first().localPose().t.z, 0.0001f)
    }
  }

  @Test fun scaledCurvedEdgesFollowSimilarityTransformUnderRotatedParent() {
    val host = Host()
    val stage = Pose(Vector3(0.7f, 1.2f, 2.1f), Quaternion(10f, 35f, 0f))
    val radius = 2.7f
    for (scale in listOf(0.7f, 1.5f, 2f)) {
      host.controller.setParameters(PlaybackStageParameters(scale, null, PlaybackStageCurvature.Cylinder(radius)))
      for (layer in host.controller.layout.layers) {
        val angle = (layer.content.halfWidth / scale) / radius
        val expected = stage * Vector3(
            radius * kotlin.math.sin(angle) * scale,
            layer.content.halfHeight,
            (layer.role.baseLocalZ + radius * (kotlin.math.cos(angle) - 1f)) * scale,
        )
        val effectiveRadius = layer.curvature.radiusOrNull()!!
        val actual = layer.worldPose(stage) * Vector3(
            effectiveRadius * kotlin.math.sin(layer.content.halfWidth / effectiveRadius),
            layer.content.halfHeight,
            effectiveRadius * kotlin.math.cos(layer.content.halfWidth / effectiveRadius),
        )
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
        assertEquals(expected.z, actual.z, 0.0001f)
      }
    }
  }

  @Test fun inputSubmittedDuringCommitIsNotAcknowledgedAsAlreadyApplied() {
    val layouts = mutableListOf<PlaybackStageLayout>()
    val panel = Panel()
    lateinit var controller: PlaybackStageController<Panel>
    controller = PlaybackStageController(1.6f, 0.9f, { panel }) { layout, _ ->
      layouts += layout
      if (layouts.size == 1) controller.setParameters(PlaybackStageParameters(scale = 2f))
      true
    }
    controller.flush()
    controller.flush()
    assertEquals(2, layouts.size)
    assertEquals(1.6f, layouts.first().geometry.viewport.widthMeters, 0.0001f)
    assertEquals(3.2f, layouts.last().geometry.viewport.widthMeters, 0.0001f)
    controller.flush()
    assertEquals(2, layouts.size)
  }

  @Test fun videoReplacedDuringSuccessfulCommitDoesNotReplayRetainedUi() {
    val panels = PlaybackStageLayer.entries.associateWith { Panel() }.toMutableMap()
    val retainedDanmaku = panels.getValue(PlaybackStageLayer.DANMAKU)
    var commits = 0
    val controller = PlaybackStageController(1.6f, 0.9f, { panels[it] }) { _, _ ->
      commits++
      panels[PlaybackStageLayer.VIDEO] = Panel()
      true
    }
    assertNull(controller.appliedLayout)
    controller.flush()
    assertEquals(controller.layout, controller.appliedLayout)
    repeat(8) { controller.flush() }
    assertEquals(1, commits)
    assertSame(retainedDanmaku, panels[PlaybackStageLayer.DANMAKU])
    controller.setParameters(PlaybackStageParameters(scale = 1.5f))
    controller.setParameters(PlaybackStageParameters(scale = 2f))
    controller.flush()
    controller.flush()
    assertEquals(2, commits)
    assertEquals(3.2f, controller.appliedLayout!!.geometry.viewport.widthMeters, 0.0001f)
  }

  @Test fun failedReplacementHasNoInputTargetAndRetriesTheLatestCompleteLayout() {
    val panels = PlaybackStageLayer.entries.associateWith { Panel() }.toMutableMap()
    var fail = false
    var commits = 0
    val controller = PlaybackStageController(1.6f, 0.9f, { panels[it] }) { _, _ ->
      commits++
      panels[PlaybackStageLayer.VIDEO] = Panel()
      if (fail) error("SDK bind failed after resource creation")
      true
    }
    controller.flush()
    fail = true
    controller.setParameters(PlaybackStageParameters(scale = 1.5f))
    assertThrows(IllegalStateException::class.java) { controller.flush() }
    assertNull(controller.appliedLayout)
    controller.setParameters(PlaybackStageParameters(scale = 2f))
    fail = false
    controller.flush()
    controller.flush()
    assertEquals(3, commits)
    assertEquals(3.2f, controller.appliedLayout!!.geometry.viewport.widthMeters, 0.0001f)
    controller.close()
    assertNull(controller.appliedLayout)
    controller.invalidate()
    controller.flush()
    assertEquals(3, commits)
  }

  @Test fun closeInsideCommitCannotRestoreAnAppliedInputTarget() {
    val panel = Panel()
    lateinit var controller: PlaybackStageController<Panel>
    var commits = 0
    controller = PlaybackStageController(1.6f, 0.9f, { panel }) { _, _ ->
      commits++
      controller.close()
      true
    }
    controller.flush()
    controller.flush()
    assertEquals(1, commits)
    assertNull(controller.appliedLayout)
  }

  @Test fun temporaryMissingPanelRestoresInputWhenTheSameResourceReturns() {
    val host = Host()
    host.readyAll()
    host.controller.flush()
    assertNotNull(host.controller.appliedLayout)
    val retained = host.ready.remove(PlaybackStageLayer.DANMAKU)!!
    host.controller.flush()
    assertNull(host.controller.appliedLayout)
    host.ready[PlaybackStageLayer.DANMAKU] = retained
    host.controller.flush()
    assertNotNull(host.controller.appliedLayout)
    assertEquals(2, host.commits.size)
    host.controller.flush()
    assertEquals(2, host.commits.size)
  }
}
