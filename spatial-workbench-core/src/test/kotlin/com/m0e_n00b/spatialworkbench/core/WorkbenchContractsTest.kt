package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchContractsTest {
  @Test
  fun validatorRejectsMissingShortsLayoutAndInvalidTransportDepth() {
    val cinemaTheme = CinemaTheme.create()
    val watchLayout = cinemaTheme.layouts.getValue(ImmersiveLayoutMode.WATCH)
    val invalidTransportLayout =
        watchLayout.copy(
            placements =
                watchLayout.placements +
                    (
                        PanelSlot.TRANSPORT to
                            watchLayout.placements.getValue(PanelSlot.TRANSPORT).copy(
                                parentSlot = PanelSlot.BROWSE,
                                depthRelation = SpatialDepthRelation.ROOT,
                            )
                    )
        )
    val missingShortsTheme =
        cinemaTheme.copy(
            layouts =
                mapOf(
                    ImmersiveLayoutMode.WATCH to invalidTransportLayout,
                    ImmersiveLayoutMode.FOCUS to cinemaTheme.layouts.getValue(ImmersiveLayoutMode.FOCUS),
                    ImmersiveLayoutMode.EDIT to watchLayout,
                )
        )

    val issues = ThemeValidator.validate(missingShortsTheme)

    assertTrue(issues.any { it is ThemeValidationIssue.MissingLayout && it.mode == ImmersiveLayoutMode.SHORTS })
    assertTrue(issues.any { it is ThemeValidationIssue.InvalidTransportOverlay && it.mode == ImmersiveLayoutMode.WATCH })
  }

  @Test
  fun cinemaThemeExposesShortsRailsAndFrontTransport() {
    val theme = CinemaTheme.create()
    val shorts = theme.layouts.getValue(ImmersiveLayoutMode.SHORTS)
    val transport = shorts.placements.getValue(PanelSlot.TRANSPORT)

    assertEquals(SpatialDepthRelation.FRONT_OF_PARENT, transport.depthRelation)
    assertEquals(PanelSlot.MEDIA_STAGE, transport.parentSlot)
    assertEquals("shorts_details_anchor", (shorts.placements.getValue(PanelSlot.SHORTS_DETAILS).anchor as SpatialAnchor.SceneAnchor).name)
    assertEquals("shorts_comments_anchor", (shorts.placements.getValue(PanelSlot.SHORTS_COMMENTS).anchor as SpatialAnchor.SceneAnchor).name)
    assertEquals(PanelPresentationPolicy.PERSISTENT, theme.presentationPolicy(PanelSlot.MEDIA_STAGE))
    assertFalse(theme.allowsDefaultCanvasHide(PanelSlot.MEDIA_STAGE))
    assertTrue(theme.allowsDefaultCanvasHide(PanelSlot.TRANSPORT))
    assertTrue(ThemeValidator.validate(theme).isEmpty())
  }

  @Test
  fun canvasCannotReferenceUnknownAtomicComponent() {
    val theme = CinemaTheme.create()
    val invalidCanvas =
        WorkbenchCanvas(
            id = "unknown-component",
            mode = ImmersiveLayoutMode.WATCH,
            visibleSlots = setOf(PanelSlot.MEDIA_STAGE),
            visibleComponentIds = setOf("not-registered"),
        )

    assertTrue(
        ThemeValidator.validate(theme.copy(canvases = listOf(invalidCanvas)))
            .any { it is ThemeValidationIssue.InvalidCanvas && it.id == "unknown-component" }
    )
  }

  @Test
  fun visibleOverflowCanvasRequiresHitTesting() {
    val theme = CinemaTheme.create()
    val invalidCanvas =
        WorkbenchCanvas(
            id = "invalid",
            mode = ImmersiveLayoutMode.WATCH,
            visibleSlots = setOf(PanelSlot.MEDIA_STAGE),
            overflowPolicy = CanvasOverflowPolicy.VISIBLE,
            preservesHitTesting = false,
        )

    val issues = ThemeValidator.validate(theme.copy(canvases = listOf(invalidCanvas)))

    assertTrue(issues.any { it is ThemeValidationIssue.InvalidCanvas && it.id == "invalid" })
  }

  @Test
  fun defaultCinemaGroupKeepsAtomicMembersAddressable() {
    val definition = DefaultCinemaPlaybackCanvasGroup.create()
    val ids = definition.members.map(ComponentNode::id)

    assertEquals(ids.size, ids.toSet().size)
    assertTrue(ids.containsAll(listOf("media-stage", "transport-action-strip", "grab-handle")))
    assertTrue(ids.containsAll(listOf("shorts-details-rail", "shorts-comments-rail")))
  }

  @Test
  fun palettePresetsPassRoleValidationAndRejectPoorContrast() {
    listOf(CinemaPalette.DARK, CinemaPalette.LIGHT, CinemaPalette.HIGH_CONTRAST).forEach { palette ->
      assertTrue(ThemeValidator.validate(CinemaTheme.create().copy(palette = palette)).none { it is ThemeValidationIssue.InvalidPalette })
    }

    val invalid =
        CinemaTheme.create().copy(
            palette =
                CinemaPalette.DARK.copy(
                    normalText = RgbColor(30, 30, 30),
                    surface = RgbColor(32, 32, 32),
                )
        )

    assertTrue(ThemeValidator.validate(invalid).any { it is ThemeValidationIssue.InvalidPalette })
  }

  @Test
  fun searchOriginRestoresCachedSearchAndFallsBackOnCacheMiss() {
    val origin =
        PlaybackBrowseOrigin.SearchResults(
            cacheKey = "search:cats:page-2",
            query = "cats",
            filters = mapOf("duration" to "short"),
            snapshotId = "snapshot-2",
            pageCursor = "cursor-2",
            scrollPosition = 240f,
        )

    assertEquals(
        BrowseContinuation.RestoreSearchResults(origin),
        BrowseOriginResolver.continuation(origin) { it == "search:cats:page-2" },
    )
    assertEquals(
        BrowseContinuation.FallbackToRecommendations,
        BrowseOriginResolver.continuation(origin) { false },
    )
    assertTrue(BrowseOriginValidator.validate(origin).isEmpty())
    assertTrue(BrowseOriginValidator.validate(origin.copy(snapshotId = "", scrollPosition = -1f)).isNotEmpty())
  }

  @Test
  fun candidateConsumesOnlyDeclaredRangeAndPreservesRemainingPinyin() {
    val result =
        SearchComposition.consume(
            composition = "ni'hao",
            candidate = InputCandidate("你", CompositionRange(0, 2)),
        )

    assertEquals(CandidateConsumption("你", "hao"), result)
    assertEquals("ni'hao语音", SearchComposition.mergeExternalFinalText("", "ni'", "hao语音"))
    assertEquals(
        SearchCompositionState(committedQuery = "oldni'系统", composition = "", candidates = emptyList()),
        SearchComposition.acceptExternalFinalText(
            SearchCompositionState(
                committedQuery = "old",
                composition = "ni'",
                candidates = listOf(InputCandidate("你", CompositionRange(0, 2))),
            ),
            "系统",
        ),
    )
    assertTrue(InputContractValidator.validate("ni", InputCandidate("你", CompositionRange(0, 3))).isNotEmpty())
  }
}
