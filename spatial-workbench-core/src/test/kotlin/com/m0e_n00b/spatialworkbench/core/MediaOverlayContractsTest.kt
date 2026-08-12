package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOverlayContractsTest {
  @Test
  fun validatorRejectsInvalidTopologyStyleCaptionAndTranslationSettings() {
    val surface =
        OverlaySurfaceSpec(
            id = "",
            enabled = true,
            supportedKinds = emptySet(),
            anchorMode = OverlayAnchorMode.STAGE_LOCKED,
            capacity = 0,
            basicStyle = OverlayBasicStyle(fontScale = 0f, opacity = 2f),
        )
    val group =
        DanmakuSurfaceGroup(
            id = "group",
            allocationWeight = 0f,
            laneSets =
                listOf(
                    DanmakuLaneSetSpec(
                        id = "scroll",
                        laneFamily = DanmakuLaneFamily.SCROLLING,
                        laneCount = 0,
                    )
                ),
            layers = listOf(DanmakuDepthLayer("layer", "missing", Float.NaN, 0)),
        )

    val topologyIssues = MediaOverlayValidator.validateTopology(listOf(surface), listOf(group))
    val captionIssues = MediaOverlayValidator.validateCaption(CaptionCue("", -1L, -1L, ""))
    val translationIssues =
        MediaOverlayValidator.validateTranslation(
            TranslationSettings(enabled = true, maxConcurrentRequests = 0, timeoutMs = 0L)
        )

    val captionOnlySurface =
        OverlaySurfaceSpec(
            id = "caption-only",
            enabled = true,
            supportedKinds = setOf(OverlayKind.CAPTION),
            anchorMode = OverlayAnchorMode.GAZE_LOCKED,
            capacity = 1,
        )
    val incompatibleGroup = group("incompatible", listOf(layer("layer", captionOnlySurface.id)))

    assertTrue(topologyIssues.isNotEmpty())
    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(captionOnlySurface), listOf(incompatibleGroup))
            .any { it is MediaOverlayValidationIssue.InvalidLayer && it.reason == "surface-does-not-support-danmaku" }
    )
    assertTrue(captionIssues.isNotEmpty())
    assertTrue(translationIssues.isNotEmpty())
  }

  @Test
  fun allocatorSkipsDisabledAndCapacityExhaustedSurfaces() {
    val disabled = surface("disabled", enabled = false)
    val full = surface("full", enabled = true, capacity = 1)
    val group = group("group", listOf(layer("disabled-layer", "disabled"), layer("full-layer", "full")))

    val result =
        DanmakuAllocator.allocate(
            request(
                groups = listOf(group),
                surfaces = mapOf(disabled.id to disabled, full.id to full),
                activeItemsBySurface = mapOf(full.id to 1),
            )
        )

    assertEquals(DanmakuAllocationResult.Dropped(DanmakuDropReason.NO_ELIGIBLE_SURFACE), result)
  }

  @Test
  fun allocatorUsesStableGroupAndLayerSelectionForEqualInputs() {
    val left = surface("left")
    val right = surface("right")
    val groupA = group("a", listOf(layer("a-layer", left.id)))
    val groupB = group("b", listOf(layer("b-layer", right.id)))
    val request = request(groups = listOf(groupA, groupB), surfaces = mapOf(left.id to left, right.id to right))

    val first = DanmakuAllocator.allocate(request)
    val second = DanmakuAllocator.allocate(request)

    assertEquals(first, second)
    assertTrue(first is DanmakuAllocationResult.Assigned)
  }

  @Test
  fun allocatorBalancesTowardLessLoadedEligibleGroup() {
    val left = surface("left")
    val right = surface("right")
    val groupA = group("a", listOf(layer("a-layer", left.id)))
    val groupB = group("b", listOf(layer("b-layer", right.id)))

    val result =
        DanmakuAllocator.allocate(
            request(
                groups = listOf(groupA, groupB),
                surfaces = mapOf(left.id to left, right.id to right),
                activeItemsByGroup = mapOf(groupA.id to 10, groupB.id to 0),
            )
        ) as DanmakuAllocationResult.Assigned

    assertEquals(groupB.id, result.assignment.groupId)
  }

  @Test
  fun allocatorRequiresMatchingDirectionAndSnapshotsSurfaceLayerAndEventStyle() {
    val surface =
        surface(
            id = "surface",
            style =
                OverlayBasicStyle(
                    fontScale = 0.8f,
                    opacity = 0.7f,
                    speedScale = 0.6f,
                    textLayout = OverlayTextLayoutStyle(direction = TextDirection.RTL),
                ),
        )
    val layer = layer("layer", surface.id, style = OverlayStyleOverride(fontScale = 1.4f))
    val group = group("group", listOf(layer), direction = DanmakuEmissionDirection.LEFT_TO_RIGHT)
    val event =
        DanmakuEvent(
            id = "event",
            startMs = 0L,
            text = "test",
            emissionDirection = DanmakuEmissionDirection.LEFT_TO_RIGHT,
            styleOverride = OverlayStyleOverride(fontScale = 2f, textLayout = OverlayTextLayoutStyle(writingMode = TextWritingMode.VERTICAL_RL)),
        )

    val assigned =
        DanmakuAllocator.allocate(
            request(event = event, groups = listOf(group), surfaces = mapOf(surface.id to surface))
        ) as DanmakuAllocationResult.Assigned

    assertEquals(DanmakuEmissionDirection.LEFT_TO_RIGHT, assigned.assignment.emissionDirection)
    assertEquals(0.8f, assigned.assignment.styleSnapshot.surfaceStyle.fontScale)
    assertEquals(1.4f, assigned.assignment.styleSnapshot.layerStyle.fontScale)
    assertEquals(2f, assigned.assignment.styleSnapshot.eventStyle?.fontScale)
    assertEquals(TextWritingMode.VERTICAL_RL, assigned.assignment.styleSnapshot.resolvedStyle.textLayout.writingMode)
    assertEquals(0.7f, assigned.assignment.styleSnapshot.resolvedStyle.opacity)
    assertEquals(0.6f, assigned.assignment.styleSnapshot.resolvedStyle.speedScale)

    val wrongDirection = DanmakuAllocator.allocate(
        request(
            event = event.copy(emissionDirection = DanmakuEmissionDirection.RIGHT_TO_LEFT),
            groups = listOf(group),
            surfaces = mapOf(surface.id to surface),
        )
    )
    assertEquals(DanmakuAllocationResult.Dropped(DanmakuDropReason.NO_ELIGIBLE_LANE), wrongDirection)
  }

  @Test
  fun unavailableLaneMovesAssignmentToAnotherLane() {
    val target = surface("surface")
    val group = group("group", listOf(layer("layer", target.id)), laneCount = 3)
    val event = DanmakuEvent("stable", 0L, "test")
    val first = DanmakuAllocator.allocate(request(event, listOf(group), mapOf(target.id to target))) as DanmakuAllocationResult.Assigned
    val second =
        DanmakuAllocator.allocate(
            request(
                event,
                listOf(group),
                mapOf(target.id to target),
                unavailableLaneIds = setOf("scroll:${first.assignment.laneIndex}"),
            )
        ) as DanmakuAllocationResult.Assigned

    assertTrue(first.assignment.laneIndex != second.assignment.laneIndex)
  }

  @Test
  fun translationCacheKeyRequiresEnabledProviderModelAndLanguage() {
    val cue = CaptionCue("cue", 0L, 1000L, "hello")
    assertEquals(null, TranslationSettings().cacheKey(cue))
    assertEquals(
        "cue:${"hello".hashCode()}:zh:provider:model",
        TranslationSettings(enabled = true, providerId = "provider", modelId = "model", targetLanguage = "zh").cacheKey(cue),
    )
  }

  private fun request(
      event: DanmakuEvent = DanmakuEvent("event", 0L, "test"),
      groups: List<DanmakuSurfaceGroup>,
      surfaces: Map<String, OverlaySurfaceSpec>,
      activeItemsBySurface: Map<String, Int> = emptyMap(),
      activeItemsByGroup: Map<String, Int> = emptyMap(),
      unavailableLaneIds: Set<String> = emptySet(),
  ) =
      DanmakuAllocationRequest(
          event = event,
          groups = groups,
          surfaces = surfaces,
          activeItemsBySurface = activeItemsBySurface,
          activeItemsByGroup = activeItemsByGroup,
          unavailableLaneIds = unavailableLaneIds,
      )

  private fun surface(
      id: String,
      enabled: Boolean = true,
      capacity: Int = 10,
      style: OverlayBasicStyle = OverlayBasicStyle(),
  ) =
      OverlaySurfaceSpec(
          id = id,
          enabled = enabled,
          supportedKinds = setOf(OverlayKind.DANMAKU),
          anchorMode = OverlayAnchorMode.STAGE_LOCKED,
          capacity = capacity,
          basicStyle = style,
      )

  private fun layer(
      id: String,
      surfaceId: String,
      style: OverlayStyleOverride = OverlayStyleOverride(),
  ) = DanmakuDepthLayer(id, surfaceId, 0f, 10, style)

  private fun group(
      id: String,
      layers: List<DanmakuDepthLayer>,
      direction: DanmakuEmissionDirection = DanmakuEmissionDirection.RIGHT_TO_LEFT,
      laneCount: Int = 2,
  ) =
      DanmakuSurfaceGroup(
          id = id,
          laneSets = listOf(DanmakuLaneSetSpec("scroll", DanmakuLaneFamily.SCROLLING, direction, laneCount)),
          layers = layers,
      )
}
