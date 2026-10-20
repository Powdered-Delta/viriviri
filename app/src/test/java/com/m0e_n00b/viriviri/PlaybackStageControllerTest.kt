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
    assertEquals(PlaybackStageCurvature.Cylinder(2.7f), layout.geometry.curvature)
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
        val actual = layer.worldPose(stage) * Vector3(0f, 0f, curvature.radiusOrNull() ?: 0f)
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
}
