package com.m0e_n00b.spatialworkbench.core

import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.danmakuGroup
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.danmakuLayer
import com.m0e_n00b.spatialworkbench.core.ThemeTestKit.danmakuSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4.3/R7.5: `surfaceIds` as an allocation domain, lane sharing and surface references (A5, A6). */
class DanmakuGroupValidatorTest {

  private val main = danmakuSurface("main")
  private val secondary = danmakuSurface("secondary")

  @Test
  fun unknownSurfaceIsRejectedWithADedicatedIssue() {
    val group = danmakuGroup(layers = listOf(danmakuLayer(surfaceIds = setOf("ghost"))))

    val issues = MediaOverlayValidator.validateTopology(listOf(main), listOf(group))

    val unknown = issues.filterIsInstance<MediaOverlayValidationIssue.UnknownOverlaySurface>().single()
    assertEquals("group", unknown.groupId)
    assertEquals("layer", unknown.layerId)
    assertEquals("ghost", unknown.surfaceId)
  }

  @Test
  fun surfaceThatCannotRenderDanmakuIsRejected() {
    val captionOnly = danmakuSurface("captions", kind = OverlayKind.CAPTION)
    val group = danmakuGroup(layers = listOf(danmakuLayer(surfaceIds = setOf("captions"))))

    val issues = MediaOverlayValidator.validateTopology(listOf(captionOnly), listOf(group))

    assertTrue(
        issues.any {
          it is MediaOverlayValidationIssue.InvalidLayer && it.reason == "surface-does-not-support-danmaku"
        }
    )
  }

  @Test
  fun laneCountMustStayWithinOneAndSixtyFour() {
    listOf(0, 65, -3).forEach { invalid ->
      assertTrue(
          "expected laneCount=$invalid to be rejected",
          MediaOverlayValidator.validateTopology(listOf(main), listOf(danmakuGroup(laneCount = invalid)))
              .any { it is MediaOverlayValidationIssue.InvalidLaneCount && it.laneCount == invalid },
      )
    }
    listOf(1, 64).forEach { valid ->
      assertTrue(
          MediaOverlayValidator.validateTopology(listOf(main), listOf(danmakuGroup(laneCount = valid)))
              .none { it is MediaOverlayValidationIssue.InvalidLaneCount },
      )
    }
  }

  @Test
  fun isolatedLaneStateWithMultipleSurfacesRequiresPerSurfaceQuotas() {
    val twoSurfaces = setOf("main", "secondary")

    val ambiguous =
        danmakuGroup(
            layers = listOf(danmakuLayer(surfaceIds = twoSurfaces)),
            laneStateSharing = LaneStateSharing.ISOLATED,
        )
    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main, secondary), listOf(ambiguous)).any {
          it is MediaOverlayValidationIssue.AmbiguousLaneSharing &&
              it.reason == "isolated-lane-state-requires-per-surface-quota"
        }
    )

    val quotaless =
        danmakuGroup(
            layers = listOf(danmakuLayer(surfaceIds = twoSurfaces, surfaceQuotas = mapOf("main" to 16))),
            laneStateSharing = LaneStateSharing.ISOLATED,
        )
    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main, secondary), listOf(quotaless))
            .any { it is MediaOverlayValidationIssue.AmbiguousLaneSharing }
    )

    val quotaed =
        danmakuGroup(
            layers =
                listOf(
                    danmakuLayer(
                        surfaceIds = twoSurfaces,
                        surfaceQuotas = mapOf("main" to 16, "secondary" to 16),
                    )
                ),
            laneStateSharing = LaneStateSharing.ISOLATED,
        )
    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main, secondary), listOf(quotaed))
            .none { it is MediaOverlayValidationIssue.AmbiguousLaneSharing }
    )

    // Shared lane state is a single group-level state, so multiple surfaces are unambiguous.
    val shared =
        danmakuGroup(layers = listOf(danmakuLayer(surfaceIds = twoSurfaces)), laneStateSharing = LaneStateSharing.SHARED)
    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main, secondary), listOf(shared))
            .none { it is MediaOverlayValidationIssue.AmbiguousLaneSharing }
    )
  }

  @Test
  fun surfaceIdsIsAnAllocationDomainAndSelectsExactlyOneSurface() {
    val group =
        danmakuGroup(
            layers =
                listOf(
                    danmakuLayer(
                        surfaceIds = setOf("main", "secondary"),
                        maxActiveItems = 8,
                        surfaceQuotas = mapOf("main" to 8, "secondary" to 8),
                    )
                ),
            laneStateSharing = LaneStateSharing.ISOLATED,
        )
    val surfaces = mapOf(main.id to main, secondary.id to secondary)
    val request =
        DanmakuAllocationRequest(
            event = DanmakuEvent("event", 0L, "text"),
            groups = listOf(group),
            surfaces = surfaces,
            activeItemsBySurface = emptyMap(),
            activeItemsByGroup = emptyMap(),
        )

    val assigned = DanmakuAllocator.allocate(request) as DanmakuAllocationResult.Assigned

    assertTrue(assigned.assignment.surfaceId in setOf("main", "secondary"))
    // Repeating the same request must not fan out to the other surface.
    assertEquals(assigned, DanmakuAllocator.allocate(request))
  }

  @Test
  fun declaringASurfaceAsBothAllocationAndDuplicationTargetIsRejected() {
    val confused =
        danmakuGroup(
            layers = listOf(danmakuLayer(surfaceIds = setOf("main"), duplicateTo = setOf("main"))),
        )

    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main), listOf(confused)).any {
          it is MediaOverlayValidationIssue.InvalidLayer && it.reason == "duplicate-allocation-domain"
        }
    )
  }

  @Test
  fun aLayerWithoutAnySurfaceTargetIsRejected() {
    val empty = danmakuGroup(layers = listOf(danmakuLayer(surfaceIds = emptySet())))

    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main), listOf(empty)).any {
          it is MediaOverlayValidationIssue.InvalidLayer && it.reason == "surface-ids-empty"
        }
    )
  }

  @Test
  fun occlusionDomainReferencesMustResolve() {
    val unknownDomain = danmakuGroup(occlusionDomainId = "ghost")

    val issues = MediaOverlayValidator.validateTopology(listOf(main), listOf(unknownDomain))

    assertTrue(
        issues.any {
          it is MediaOverlayValidationIssue.InvalidGroup && it.reason == "unknown-occlusion-domain"
        }
    )

    val declared =
        danmakuGroup(occlusionDomainId = "stage")
    assertTrue(
        MediaOverlayValidator.validateTopology(
                listOf(main),
                listOf(declared),
                listOf(DanmakuOcclusionDomain("stage", setOf("group"))),
            )
            .none { it is MediaOverlayValidationIssue.InvalidGroup }
    )
  }

  @Test
  fun groupWideCapacityMustBePositiveWhenDeclared() {
    val invalid = danmakuGroup(maxActiveItems = 0)

    assertTrue(
        MediaOverlayValidator.validateTopology(listOf(main), listOf(invalid)).any {
          it is MediaOverlayValidationIssue.InvalidGroup && it.reason == "max-active-items"
        }
    )
  }

  @Test
  fun themeValidatorSurfacesOverlayTopologyFromTheInjectedSurfaceCatalog() {
    val theme = ThemeTestKit.cinema()
    val surface = danmakuSurface(CinemaTheme.DEFAULT_DANMAKU_SURFACE_ID)
    val catalogs =
        ThemeCatalogs(
            sceneAnchors = ThemeTestKit.declaredAnchorCatalog(),
            overlaySurfaces = OverlaySurfaceCatalog.of(listOf(surface)),
        )

    val report = ThemeValidator.inspect(theme, catalogs)
    assertTrue(report.overlayIssues.isEmpty())

    val broken = theme.withDanmakuGroups(listOf(danmakuGroup(laneCount = 200)))
    assertTrue(
        ThemeValidator.inspect(broken, catalogs).overlayIssues.any {
          it is MediaOverlayValidationIssue.InvalidLaneCount
        }
    )
  }

  @Test
  fun layerCapacityPrefersTheLayerQuotaAndThePerSurfaceQuota() {
    val layer = danmakuLayer(maxActiveItems = 10, surfaceQuotas = mapOf("main" to 4))

    assertEquals(4, layer.effectiveCapacity("main", surfaceCapacity = 64))
    assertEquals(10, layer.effectiveCapacity("secondary", surfaceCapacity = 64))
    assertEquals(2, layer.effectiveCapacity("secondary", surfaceCapacity = 2))
  }
}
